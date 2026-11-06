package com.example.sysarch.sync;

import com.example.sysarch.link.LinkRepository;
import com.example.sysarch.link.LinkRepository.StoredLink;
import com.example.sysarch.sync.api.model.Link;
import com.example.sysarch.sync.api.model.SyncResult;
import com.example.sysarch.sync.api.model.SyncResultConflictsInner;
import com.example.sysarch.sync.api.model.SyncResultErrorsInner;
import com.example.sysarch.sync.model.ModelGateway;
import com.example.sysarch.sync.model.ModelGateway.ElementSnapshot;
import com.example.sysarch.sync.polarion.PolarionClient;
import com.example.sysarch.sync.polarion.PolarionClient.WorkItem;
import com.example.sysarch.sync.polarion.PolarionException;
import com.example.sysarch.sync.polarion.PolarionText;
import com.example.sysarch.sync.polarion.PolarionToken;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Push and pull between the model and Polarion (spec §6.2, §6.3, T7 step 4-5).
 *
 * <p><b>Field ownership</b> (§6.2, the only basis for conflicts). Each field has exactly one writer:
 * <ul>
 * <li>model-owned, written to Polarion custom fields ({@link SyncProperties.Fields}): element id, name, layer,
 * stereotypes, and a Connection's source/target. Polarion users must not edit them; if they do, it is a CONFLICT for
 * a human to resolve, never merged.</li>
 * <li>Polarion-owned, read into the model: the Work Item title (kept on the link, shown read-only) and a Requirement's
 * text (the Work Item description).</li>
 * <li>Connection kind stays in the model only. Only <i>creation</i> merges automatically: pushing an unbound element
 * creates its Work Item, and pulling finds new Work Items for the unbound pool.</li>
 * </ul>
 *
 * <p><b>Push is all or nothing</b> (§6.3). Every element is prechecked first (exists, maps to a Work Item type, has no
 * constraint violation, its Work Item exists and was not edited in Polarion). One failure and nothing is written.
 * Writes then run with an undo log; a failure part-way undoes the earlier writes in reverse order.
 */
@Service
public class SyncService {

    public static final String PUSH = "PUSH";

    public static final String PULL = "PULL";

    private static final String TITLE = "title";

    private static final String DESCRIPTION = "description";

    private final Logger logger = LoggerFactory.getLogger(SyncService.class);

    private final ModelGateway model;

    private final LinkRepository links;

    private final SyncStore store;

    private final PolarionClient polarion;

    private final SyncProperties properties;

    private final ObjectMapper json;

    private final TransactionTemplate transaction;

    public SyncService(ModelGateway model, LinkRepository links, SyncStore store, PolarionClient polarion, SyncProperties properties, ObjectMapper json,
            TransactionTemplate transaction) {
        this.model = Objects.requireNonNull(model);
        this.links = Objects.requireNonNull(links);
        this.store = Objects.requireNonNull(store);
        this.polarion = Objects.requireNonNull(polarion);
        this.properties = Objects.requireNonNull(properties);
        this.json = Objects.requireNonNull(json);
        this.transaction = Objects.requireNonNull(transaction);
    }

    /**
     * @param status HTTP status for the response: 200, 409 (conflicts) or 429 (Polarion rate limit)
     * @param retryAfterSeconds for 429, the Retry-After to pass on
     */
    public record Outcome(int status, SyncResult result, long retryAfterSeconds) {

        static Outcome ok(SyncResult result) {
            return new Outcome(200, result, 0);
        }
    }

    // =========================================================== push

    public Outcome push(String projectId, List<String> elementIds, boolean dryRun, PolarionToken token, String actor) {
        SyncResult result = emptyResult();
        List<String> ids = List.copyOf(new LinkedHashSet<>(elementIds));
        Map<String, ElementSnapshot> elements;
        try {
            elements = this.model.read(projectId, ids);
        } catch (ModelGateway.UnmappedProjectException e) {
            return Outcome.ok(error(result, null, "PROJECT_NOT_MAPPED", e.getMessage()));
        }

        List<Plan> plans = new ArrayList<>();
        for (String id : ids) {
            try {
                this.precheck(projectId, id, elements.get(id), token, result).ifPresent(plans::add);
            } catch (PolarionException.RateLimited e) {
                return new Outcome(429, error(result, id, "POLARION_RATE_LIMITED", e.getMessage()), e.retryAfterSeconds());
            } catch (PolarionException e) {
                error(result, id, "POLARION_ERROR", e.getMessage());
            }
        }

        if (!result.getConflicts().isEmpty()) {
            this.markConflicts(result);
            return new Outcome(409, result.pushed(0), 0);
        }
        if (!result.getErrors().isEmpty()) {
            return Outcome.ok(result.pushed(0)); // nothing is written when any element fails the precheck
        }
        long skipped = plans.stream().filter(p -> p.kind == Plan.Kind.SKIP).count();
        result.pushed((int) (plans.size() - skipped)).skipped((int) skipped);
        if (dryRun) {
            return Outcome.ok(result);
        }
        return this.execute(projectId, plans, token, actor, result);
    }

    /** Decides what to do with one element, without writing anything; failures are added to {@code result}. */
    private Optional<Plan> precheck(String projectId, String id, ElementSnapshot element, PolarionToken token, SyncResult result) {
        if (element == null) {
            error(result, id, "ELEMENT_NOT_FOUND", "no element '" + id + "' in the model of project " + projectId);
            return Optional.empty();
        }
        String workItemType = this.properties.getWorkItemTypes().get(element.type());
        if (workItemType == null) {
            error(result, id, "UNSUPPORTED_ELEMENT_TYPE", element.type() + " elements are not synchronized with Polarion");
            return Optional.empty();
        }
        if (!element.errors().isEmpty()) {
            element.errors().forEach(e -> error(result, id, "CONSTRAINT_VIOLATION", e.code() + ": " + e.message()));
            return Optional.empty();
        }
        Map<String, String> desired = this.modelOwned(element);
        var existing = this.links.find(id);
        if (existing.isEmpty()) {
            return Optional.of(Plan.create(element, workItemType, desired));
        }
        StoredLink link = existing.get();
        if (!link.projectId().equals(projectId)) {
            error(result, id, "LINKED_IN_OTHER_PROJECT", "element is bound to " + link.workItemId() + " in project " + link.projectId());
            return Optional.empty();
        }
        var workItem = this.polarion.getWorkItem(projectId, link.workItemId(), token);
        if (workItem.isEmpty()) {
            error(result, id, "WORK_ITEM_NOT_FOUND", "work item " + link.workItemId() + " no longer exists in Polarion (orphan binding)");
            return Optional.empty();
        }
        Map<String, String> current = this.modelOwnedOn(workItem.get());
        List<String> editedInPolarion = this.editedInPolarion(link, current);
        if (!editedInPolarion.isEmpty() || Link.StateEnum.CONFLICT.getValue().equals(link.state())) {
            result.addConflictsItem(new SyncResultConflictsInner().elementId(id).fields(editedInPolarion));
            return Optional.empty();
        }
        Map<String, String> changes = new LinkedHashMap<>();
        desired.forEach((field, value) -> {
            if (!Objects.equals(blankAsNull(value), blankAsNull(current.get(field)))) {
                changes.put(field, value);
            }
        });
        return Optional.of(changes.isEmpty() ? Plan.skip(element, link) : Plan.update(element, link, workItem.get(), desired, changes, current));
    }

    private Outcome execute(String projectId, List<Plan> plans, PolarionToken token, String actor, SyncResult result) {
        List<Runnable> undo = new ArrayList<>();
        for (Plan plan : plans) {
            try {
                if (plan.kind == Plan.Kind.CREATE) {
                    plan.workItemId = this.polarion.createWorkItem(projectId, plan.workItemType, plan.createAttributes(), token);
                    String created = plan.workItemId;
                    undo.add(() -> this.polarion.deleteWorkItems(projectId, List.of(created), token));
                } else if (plan.kind == Plan.Kind.UPDATE) {
                    this.polarion.updateWorkItem(projectId, plan.workItemId, new LinkedHashMap<>(plan.changes), token);
                    Map<String, Object> previous = new LinkedHashMap<>();
                    plan.changes.keySet().forEach(field -> previous.put(field, plan.previous.get(field)));
                    String updated = plan.workItemId;
                    undo.add(() -> this.polarion.updateWorkItem(projectId, updated, previous, token));
                }
            } catch (PolarionException e) {
                List<String> undoFailures = this.undo(undo);
                SyncResult failed = emptyResult().pushed(0);
                error(failed, plan.element.id(), e instanceof PolarionException.RateLimited ? "POLARION_RATE_LIMITED" : "POLARION_ERROR", e.getMessage());
                error(failed, null, "ROLLED_BACK", "the earlier writes of this push were undone" + (undoFailures.isEmpty() ? "" : "; undo failed: " + undoFailures));
                long retryAfter = e instanceof PolarionException.RateLimited limited ? limited.retryAfterSeconds() : 0;
                return new Outcome(retryAfter > 0 ? 429 : 200, failed, retryAfter);
            }
        }
        try {
            this.transaction.executeWithoutResult(status -> plans.forEach(plan -> this.record(projectId, plan, actor)));
        } catch (RuntimeException e) {
            List<String> undoFailures = this.undo(undo);
            SyncResult failed = emptyResult().pushed(0);
            error(failed, null, "BOOKKEEPING_FAILED", e.getMessage() + "; Polarion writes undone" + (undoFailures.isEmpty() ? "" : ", undo failed: " + undoFailures));
            return Outcome.ok(failed);
        }
        return Outcome.ok(result);
    }

    /** Links and audit for one written element; runs in the push transaction. */
    private void record(String projectId, Plan plan, String actor) {
        if (plan.kind == Plan.Kind.SKIP) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        String pushed = this.toJson(plan.desired);
        if (plan.kind == Plan.Kind.CREATE) {
            this.links.insert(new StoredLink(plan.element.id(), plan.element.type(), plan.workItemId, plan.workItemType, projectId,
                    Link.StateEnum.ACTIVE.getValue(), plan.element.rev(), null, null, now, null, pushed, plan.element.name()));
            this.store.audit(projectId, plan.element.id(), plan.workItemId, PUSH, "workItem", null, plan.workItemId, actor);
            plan.desired.forEach((field, value) -> this.store.audit(projectId, plan.element.id(), plan.workItemId, PUSH, field, null, value, actor));
        } else {
            StoredLink link = plan.link;
            this.links.update(new StoredLink(link.elementId(), link.elementType(), link.workItemId(), link.workItemType(), link.projectId(), link.state(),
                    plan.element.rev(), link.wiRev(), link.etag(), now, link.lastPullAt(), pushed, link.workItemTitle()));
            plan.changes.forEach((field, value) -> this.store.audit(projectId, plan.element.id(), plan.workItemId, PUSH, field, plan.previous.get(field), value, actor));
        }
    }

    /** Runs undo actions in reverse order; returns the ones that failed. */
    private List<String> undo(List<Runnable> undo) {
        List<String> failures = new ArrayList<>();
        for (int i = undo.size() - 1; i >= 0; i--) {
            try {
                undo.get(i).run();
            } catch (RuntimeException e) {
                failures.add(e.getMessage());
                this.logger.error("push rollback step failed: {}", e.getMessage());
            }
        }
        return failures;
    }

    private void markConflicts(SyncResult result) {
        this.transaction.executeWithoutResult(status -> result.getConflicts().forEach(conflict -> this.links.find(conflict.getElementId())
                .filter(link -> !Link.StateEnum.CONFLICT.getValue().equals(link.state()))
                .ifPresent(link -> this.links.update(link.withState(Link.StateEnum.CONFLICT.getValue())))));
    }

    // =========================================================== pull

    public Outcome pull(String projectId, OffsetDateTime since, PolarionToken token, String actor) {
        SyncResult result = emptyResult();
        List<StoredLink> bound = this.links.findByProject(projectId, null).stream()
                .filter(l -> !Link.StateEnum.ORPHAN.getValue().equals(l.state()))
                .toList();
        Map<String, ElementSnapshot> elements;
        try {
            elements = this.model.read(projectId, bound.stream().map(StoredLink::elementId).toList());
        } catch (ModelGateway.UnmappedProjectException e) {
            return Outcome.ok(error(result, null, "PROJECT_NOT_MAPPED", e.getMessage()));
        }

        List<StoredLink> linkUpdates = new ArrayList<>();
        Map<String, Map<String, String>> textChanges = new LinkedHashMap<>();
        List<Runnable> audits = new ArrayList<>();
        int pulled = 0;
        int skipped = 0;
        try {
            for (StoredLink link : bound) {
                var found = this.polarion.getWorkItem(projectId, link.workItemId(), token);
                if (found.isEmpty()) {
                    linkUpdates.add(link.withState(Link.StateEnum.ORPHAN.getValue()));
                    error(result, link.elementId(), "WORK_ITEM_DELETED", "work item " + link.workItemId() + " was deleted in Polarion; binding marked ORPHAN");
                    audits.add(() -> this.store.audit(projectId, link.elementId(), link.workItemId(), PULL, "state", link.state(), "ORPHAN", actor));
                    continue;
                }
                WorkItem workItem = found.get();
                boolean changed = false;
                String state = link.state();
                List<String> edited = this.editedInPolarion(link, this.modelOwnedOn(workItem));
                if (!edited.isEmpty()) {
                    result.addConflictsItem(new SyncResultConflictsInner().elementId(link.elementId()).fields(edited));
                    state = Link.StateEnum.CONFLICT.getValue();
                }
                if (!Objects.equals(workItem.title(), link.workItemTitle())) {
                    String oldTitle = link.workItemTitle();
                    audits.add(() -> this.store.audit(projectId, link.elementId(), link.workItemId(), PULL, TITLE, oldTitle, workItem.title(), actor));
                    changed = true;
                }
                ElementSnapshot element = elements.get(link.elementId());
                String text = description(workItem);
                if (element != null && "Requirement".equals(element.type()) && text != null && !text.equals(element.text())) {
                    textChanges.put(element.id(), Map.of(ElementSnapshot.TEXT, text));
                    audits.add(() -> this.store.audit(projectId, link.elementId(), link.workItemId(), PULL, ElementSnapshot.TEXT, element.text(), text, actor));
                    changed = true;
                }
                linkUpdates.add(new StoredLink(link.elementId(), link.elementType(), link.workItemId(), link.workItemType(), link.projectId(), state,
                        link.modelRev(), workItem.updated() == null ? link.wiRev() : workItem.updated().toString(), link.etag(), link.lastPushAt(),
                        OffsetDateTime.now(), link.pushedFields(), workItem.title()));
                if (changed) {
                    pulled++;
                } else {
                    skipped++;
                }
            }
            if (since != null) {
                this.collectUnbound(projectId, since, token, bound, audits, actor);
            }
        } catch (PolarionException.RateLimited e) {
            return new Outcome(429, error(result, null, "POLARION_RATE_LIMITED", e.getMessage()), e.retryAfterSeconds());
        } catch (PolarionException e) {
            return Outcome.ok(error(result, null, "POLARION_ERROR", e.getMessage()));
        }

        // Polarion-owned values go into the model as one edit through the editing context, then the bookkeeping.
        this.model.applyPolarionOwned(projectId, textChanges);
        this.transaction.executeWithoutResult(status -> {
            linkUpdates.forEach(this.links::update);
            audits.forEach(Runnable::run);
        });
        return Outcome.ok(result.pulled(pulled).skipped(skipped));
    }

    /** New Work Items of the synchronized types, created since {@code since}, that no element is bound to (spec §6.2). */
    private void collectUnbound(String projectId, OffsetDateTime since, PolarionToken token, List<StoredLink> bound, List<Runnable> audits, String actor) {
        Set<String> types = new LinkedHashSet<>(this.properties.getWorkItemTypes().values());
        String query = "type:(" + String.join(" ", types) + ") AND created:[" + since.format(DateTimeFormatter.BASIC_ISO_DATE).substring(0, 8) + " TO *]";
        Set<String> boundIds = new LinkedHashSet<>();
        bound.forEach(l -> boundIds.add(l.workItemId()));
        this.links.findByProject(projectId, Link.StateEnum.ORPHAN.getValue()).forEach(l -> boundIds.add(l.workItemId()));
        for (WorkItem workItem : this.polarion.queryWorkItems(projectId, query, token)) {
            if (!boundIds.contains(workItem.id())) {
                audits.add(() -> {
                    if (this.store.addToUnboundPool(projectId, workItem.id(), workItem.type(), workItem.title())) {
                        this.store.audit(projectId, "-", workItem.id(), PULL, "unboundPool", null, workItem.id(), actor);
                    }
                });
            }
        }
    }

    // =========================================================== field mapping

    /** The model-owned values of an element, keyed by Polarion custom field id. */
    Map<String, String> modelOwned(ElementSnapshot element) {
        SyncProperties.Fields fields = this.properties.getFields();
        Map<String, String> values = new LinkedHashMap<>();
        values.put(fields.getElementId(), element.id());
        values.put(fields.getName(), element.name());
        values.put(fields.getLayer(), element.layer());
        values.put(fields.getStereotypes(), String.join(", ", element.stereotypes()));
        if ("Connection".equals(element.type())) {
            values.put(fields.getSource(), element.sourceId());
            values.put(fields.getTarget(), element.targetId());
        }
        return values;
    }

    /** The values of the model-owned custom fields as they are on the Work Item now. */
    private Map<String, String> modelOwnedOn(WorkItem workItem) {
        SyncProperties.Fields fields = this.properties.getFields();
        Map<String, String> values = new LinkedHashMap<>();
        for (String field : List.of(fields.getElementId(), fields.getName(), fields.getLayer(), fields.getStereotypes(), fields.getSource(),
                fields.getTarget())) {
            Object value = workItem.attribute(field);
            if (value != null) {
                values.put(field, String.valueOf(value));
            }
        }
        return values;
    }

    /** Model-owned fields whose Polarion value no longer matches what this service last pushed. */
    private List<String> editedInPolarion(StoredLink link, Map<String, String> current) {
        if (link.pushedFields() == null) {
            return List.of();
        }
        Map<String, String> pushed = this.fromJson(link.pushedFields());
        List<String> edited = new ArrayList<>();
        pushed.forEach((field, value) -> {
            if (!Objects.equals(blankAsNull(value), blankAsNull(current.get(field)))) {
                edited.add(field);
            }
        });
        return edited;
    }

    /** A Work Item description is {"type": "text/plain" | "text/html", "value": "..."}; HTML is read as its plain text. */
    private static String description(WorkItem workItem) {
        if (workItem.attribute(DESCRIPTION) instanceof Map<?, ?> description && description.get("value") != null) {
            Object type = description.get("type");
            return PolarionText.toPlain(type == null ? null : type.toString(), description.get("value").toString());
        }
        return null;
    }

    /**
     * Polarion drops a custom field set to "" (it reads back as absent; verified against Polarion 2512), so empty
     * and absent are the same value when comparing what we pushed with what is there.
     */
    private static String blankAsNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String toJson(Map<String, String> values) {
        try {
            return this.json.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> fromJson(String text) {
        try {
            return this.json.readValue(text, new TypeReference<LinkedHashMap<String, String>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unreadable pushed_fields: " + text, e);
        }
    }

    private static SyncResult emptyResult() {
        return new SyncResult().pushed(0).pulled(0).skipped(0).conflicts(new ArrayList<>()).errors(new ArrayList<>());
    }

    private static SyncResult error(SyncResult result, String elementId, String code, String message) {
        result.addErrorsItem(new SyncResultErrorsInner().elementId(elementId).code(code).message(message));
        return result;
    }

    /** What push will do with one element. */
    private static final class Plan {

        enum Kind {
            CREATE, UPDATE, SKIP
        }

        final Kind kind;

        final ElementSnapshot element;

        final StoredLink link;

        final String workItemType;

        final Map<String, String> desired;

        final Map<String, String> changes;

        final Map<String, String> previous;

        String workItemId;

        private Plan(Kind kind, ElementSnapshot element, StoredLink link, String workItemType, String workItemId, Map<String, String> desired,
                Map<String, String> changes, Map<String, String> previous) {
            this.kind = kind;
            this.element = element;
            this.link = link;
            this.workItemType = workItemType;
            this.workItemId = workItemId;
            this.desired = desired;
            this.changes = changes;
            this.previous = previous;
        }

        static Plan create(ElementSnapshot element, String workItemType, Map<String, String> desired) {
            return new Plan(Kind.CREATE, element, null, workItemType, null, desired, desired, Map.of());
        }

        static Plan update(ElementSnapshot element, StoredLink link, WorkItem workItem, Map<String, String> desired, Map<String, String> changes,
                Map<String, String> previous) {
            return new Plan(Kind.UPDATE, element, link, link.workItemType(), workItem.id(), desired, changes, previous);
        }

        static Plan skip(ElementSnapshot element, StoredLink link) {
            return new Plan(Kind.SKIP, element, link, link.workItemType(), link.workItemId(), Map.of(), Map.of(), Map.of());
        }

        /**
         * Creation is the one automatic merge: the new Work Item gets the model-owned fields, plus an initial title
         * (the element name; Polarion owns it from then on) and, for a Requirement, its text as the description.
         */
        Map<String, Object> createAttributes() {
            Map<String, Object> attributes = new LinkedHashMap<>(this.desired);
            attributes.put(TITLE, this.element.name());
            if (this.element.text() != null) {
                attributes.put(DESCRIPTION, Map.of("type", "text/plain", "value", this.element.text()));
            }
            return attributes;
        }
    }
}

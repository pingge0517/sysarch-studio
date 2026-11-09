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
 * <li>(P1 T6) A Requirement's {@code satisfiedBy}/{@code verifiedBy} is model-owned and pushed as native Polarion
 * links (user's decision): the satisfying element's Work Item {@code implements} (or {@code verifies}, configurable)
 * the requirement's Work Item. The links belong to the element's push; pushing a requirement also pushes the elements
 * it links to and the ones it used to link to, so a removed relation is removed in Polarion too. Both ends must be
 * bound or created in the same push. Only links with these roles to Work Items bound to requirements are managed;
 * a change to them in Polarion is a CONFLICT ({@code traceLinks}), like a model-owned field.</li>
 * <li>(P1 T6) A Requirement's {@code reqId} is Polarion-owned: the bound Work Item's id, written on pull.</li>
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

    /** The pseudo-field under which pushed_fields records the trace links last written ("role:WI,..."), P1 T6. */
    static final String TRACE_LINKS = "traceLinks";

    private static final String REQUIREMENT = "Requirement";

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
        Map<String, ElementSnapshot> elements;
        try {
            elements = this.model.readAll(projectId);
        } catch (ModelGateway.UnmappedProjectException e) {
            return Outcome.ok(error(result, null, "PROJECT_NOT_MAPPED", e.getMessage()));
        }
        Trace trace = new Trace(projectId, elements, this.links.findByProject(projectId, null));
        List<String> ids = trace.withOwners(new LinkedHashSet<>(elementIds));

        List<Plan> plans = new ArrayList<>();
        for (String id : ids) {
            try {
                this.precheck(projectId, id, elements.get(id), token, result, trace).ifPresent(plans::add);
            } catch (PolarionException.RateLimited e) {
                return new Outcome(429, error(result, id, "POLARION_RATE_LIMITED", e.getMessage()), e.retryAfterSeconds());
            } catch (PolarionException e) {
                error(result, id, "POLARION_ERROR", e.getMessage());
            }
        }

        Set<String> creating = new LinkedHashSet<>();
        plans.stream().filter(plan -> plan.kind == Plan.Kind.CREATE).forEach(plan -> creating.add(plan.element.id()));
        for (Plan plan : plans) {
            for (TraceTarget target : plan.desiredTrace) {
                if (trace.requirementWorkItems.get(target.requirementId()) == null && !creating.contains(target.requirementId())) {
                    error(result, plan.element.id(), "TRACE_TARGET_NOT_BOUND", "requirement " + target.requirementId()
                            + " has no Work Item yet; push it together with this element (or first)");
                }
            }
        }

        if (!result.getConflicts().isEmpty()) {
            this.markConflicts(result);
            this.model.refreshViews(projectId); // links became CONFLICT
            return new Outcome(409, result.pushed(0), 0);
        }
        if (!result.getErrors().isEmpty()) {
            return Outcome.ok(result.pushed(0)); // nothing is written when any element fails the precheck
        }
        long skipped = plans.stream().filter(p -> p.kind == Plan.Kind.SKIP && !p.traceDiffers).count();
        result.pushed((int) (plans.size() - skipped)).skipped((int) skipped);
        if (dryRun) {
            return Outcome.ok(result);
        }
        Outcome outcome = this.execute(projectId, plans, token, actor, result);
        if (outcome.status() == 200 && outcome.result().getErrors().isEmpty() && outcome.result().getPushed() > 0) {
            this.model.refreshViews(projectId); // new or changed bindings: recompute the Validation view
        }
        return outcome;
    }

    /** Decides what to do with one element, without writing anything; failures are added to {@code result}. */
    private Optional<Plan> precheck(String projectId, String id, ElementSnapshot element, PolarionToken token, SyncResult result, Trace trace) {
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
        List<TraceTarget> desiredTrace = trace.targetsOf(element);
        var existing = this.links.find(id);
        if (existing.isEmpty()) {
            return Optional.of(Plan.create(element, workItemType, desired).trace(trace.isOwner(element), desiredTrace, Set.of(), !desiredTrace.isEmpty()));
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
        List<String> editedInPolarion = new ArrayList<>(this.editedInPolarion(link, current));
        Set<String> currentTrace = trace.managedLinks(workItem.get());
        if (trace.isOwner(element) && this.traceEdited(link, currentTrace)) {
            editedInPolarion.add(TRACE_LINKS);
        }
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
        boolean traceDiffers = trace.isOwner(element) && !trace.resolvedOrNull(desiredTrace).equals(currentTrace);
        Plan plan = changes.isEmpty() ? Plan.skip(element, link, desired) : Plan.update(element, link, workItem.get(), desired, changes, current);
        return Optional.of(plan.trace(trace.isOwner(element), desiredTrace, currentTrace, traceDiffers));
    }

    /** Whether the managed trace links on the Work Item differ from what this service last wrote ("" if it never did). */
    private boolean traceEdited(StoredLink link, Set<String> current) {
        String pushed = link.pushedFields() == null ? null : this.fromJson(link.pushedFields()).get(TRACE_LINKS);
        return !Objects.equals(blankAsNull(pushed), blankAsNull(Trace.join(current)));
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
        // Phase 2 (P1 T6): trace links, now that every Work Item of the push exists.
        Map<String, String> workItemOf = new LinkedHashMap<>();
        plans.forEach(plan -> {
            if (plan.workItemId != null) {
                workItemOf.put(plan.element.id(), plan.workItemId);
            }
        });
        for (Plan plan : plans) {
            if (!plan.traceOwner) {
                continue;
            }
            try {
                Set<String> desired = new java.util.TreeSet<>();
                for (TraceTarget target : plan.desiredTrace) {
                    String requirementWorkItem = workItemOf.containsKey(target.requirementId()) ? workItemOf.get(target.requirementId())
                            : this.workItemOfElement(projectId, target.requirementId());
                    desired.add(target.role() + ":" + requirementWorkItem);
                }
                plan.resolvedTrace = desired;
                List<String> add = desired.stream().filter(link -> !plan.currentTrace.contains(link)).toList();
                List<String> remove = plan.currentTrace.stream().filter(link -> !desired.contains(link)).toList();
                String owner = plan.workItemId;
                if (!add.isEmpty()) {
                    this.polarion.addLinks(projectId, owner, Trace.links(projectId, add), token);
                    undo.add(() -> this.polarion.removeLinks(projectId, owner, Trace.links(projectId, add), token));
                }
                if (!remove.isEmpty()) {
                    this.polarion.removeLinks(projectId, owner, Trace.links(projectId, remove), token);
                    undo.add(() -> this.polarion.addLinks(projectId, owner, Trace.links(projectId, remove), token));
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

    private String workItemOfElement(String projectId, String elementId) {
        return this.links.find(elementId).filter(link -> link.projectId().equals(projectId)).map(StoredLink::workItemId)
                .orElseThrow(() -> new IllegalStateException("no Work Item for " + elementId));
    }

    /** Links and audit for one written element; runs in the push transaction. */
    private void record(String projectId, Plan plan, String actor) {
        if (plan.kind == Plan.Kind.SKIP && !plan.traceDiffers) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, String> pushedValues = new LinkedHashMap<>(plan.desired);
        if (plan.traceOwner) {
            pushedValues.put(TRACE_LINKS, Trace.join(plan.resolvedTrace));
            if (!plan.resolvedTrace.equals(plan.currentTrace)) {
                this.store.audit(projectId, plan.element.id(), plan.workItemId, PUSH, TRACE_LINKS, Trace.join(plan.currentTrace), Trace.join(plan.resolvedTrace), actor);
            }
        }
        String pushed = this.toJson(pushedValues);
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

    /** Full pull (the API's /sync/pull): every bound Work Item is read. */
    public Outcome pull(String projectId, OffsetDateTime since, PolarionToken token, String actor) {
        return this.pull(projectId, since, token, actor, false);
    }

    /**
     * Incremental pull (P1 T6, the scheduled job; spec P1 §4.3: "只拉取增量，不拉全量"): only the Work Items updated since
     * {@code since} are read; the other bindings are only checked for existence (one query per 50 ids), so a deleted
     * Work Item still becomes ORPHAN. New Work Items created since then go to the unbound pool.
     */
    public Outcome pullIncremental(String projectId, OffsetDateTime since, PolarionToken token, String actor) {
        return this.pull(projectId, Objects.requireNonNull(since), token, actor, true);
    }

    private Outcome pull(String projectId, OffsetDateTime since, PolarionToken token, String actor, boolean incremental) {
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
        Trace trace = new Trace(projectId, elements, this.links.findByProject(projectId, null));

        List<StoredLink> linkUpdates = new ArrayList<>();
        Map<String, Map<String, String>> modelChanges = new LinkedHashMap<>();
        List<Runnable> audits = new ArrayList<>();
        int pulled = 0;
        int skipped = 0;
        try {
            Map<String, Optional<WorkItem>> observed = incremental ? this.observeIncrementally(projectId, since, bound, token) : this.observeAll(projectId, bound, token);
            for (StoredLink link : bound) {
                if (!observed.containsKey(link.workItemId())) {
                    continue; // unchanged since the last pull (incremental only)
                }
                var found = observed.get(link.workItemId());
                if (found.isEmpty()) {
                    linkUpdates.add(link.withState(Link.StateEnum.ORPHAN.getValue()));
                    error(result, link.elementId(), "WORK_ITEM_DELETED", "work item " + link.workItemId() + " was deleted in Polarion; binding marked ORPHAN");
                    audits.add(() -> this.store.audit(projectId, link.elementId(), link.workItemId(), PULL, "state", link.state(), "ORPHAN", actor));
                    continue;
                }
                WorkItem workItem = found.get();
                boolean changed = false;
                String state = link.state();
                List<String> edited = new ArrayList<>(this.editedInPolarion(link, this.modelOwnedOn(workItem)));
                boolean owner = !REQUIREMENT.equals(link.elementType()) && this.properties.getWorkItemTypes().containsKey(link.elementType());
                if (owner && this.traceEdited(link, trace.managedLinks(workItem))) {
                    edited.add(TRACE_LINKS);
                }
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
                if (element != null && REQUIREMENT.equals(element.type())) {
                    String text = description(workItem);
                    if (text != null && !text.equals(element.text())) {
                        modelChanges.computeIfAbsent(element.id(), k -> new LinkedHashMap<>()).put(ElementSnapshot.TEXT, text);
                        audits.add(() -> this.store.audit(projectId, link.elementId(), link.workItemId(), PULL, ElementSnapshot.TEXT, element.text(), text, actor));
                        changed = true;
                    }
                    if (!workItem.id().equals(element.reqId())) { // P1 T6: reqId is the Work Item id (Polarion-owned)
                        modelChanges.computeIfAbsent(element.id(), k -> new LinkedHashMap<>()).put(ElementSnapshot.REQ_ID, workItem.id());
                        audits.add(() -> this.store.audit(projectId, link.elementId(), link.workItemId(), PULL, ElementSnapshot.REQ_ID, element.reqId(),
                                workItem.id(), actor));
                        changed = true;
                    }
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
        this.model.applyPolarionOwned(projectId, modelChanges);
        this.transaction.executeWithoutResult(status -> {
            linkUpdates.forEach(this.links::update);
            audits.forEach(Runnable::run);
        });
        this.model.refreshViews(projectId); // link states and titles changed: recompute the Validation view
        return Outcome.ok(result.pulled(pulled).skipped(skipped));
    }

    /** Every bound Work Item, read one by one (empty = deleted). */
    private Map<String, Optional<WorkItem>> observeAll(String projectId, List<StoredLink> bound, PolarionToken token) {
        Map<String, Optional<WorkItem>> observed = new LinkedHashMap<>();
        for (StoredLink link : bound) {
            observed.put(link.workItemId(), this.polarion.getWorkItem(projectId, link.workItemId(), token));
        }
        return observed;
    }

    /** The bound Work Items updated since {@code since}, and the bound ones that no longer exist (empty). */
    private Map<String, Optional<WorkItem>> observeIncrementally(String projectId, OffsetDateTime since, List<StoredLink> bound, PolarionToken token) {
        Set<String> boundIds = new LinkedHashSet<>();
        bound.forEach(link -> boundIds.add(link.workItemId()));
        Map<String, Optional<WorkItem>> observed = new LinkedHashMap<>();
        if (boundIds.isEmpty()) {
            return observed;
        }
        Set<String> types = new LinkedHashSet<>(this.properties.getWorkItemTypes().values());
        String query = "type:(" + String.join(" ", types) + ") AND updated:[" + since.format(DateTimeFormatter.BASIC_ISO_DATE).substring(0, 8) + " TO *]";
        for (WorkItem workItem : this.polarion.queryWorkItems(projectId, query, token)) {
            if (boundIds.contains(workItem.id()) && (workItem.updated() == null || !workItem.updated().isBefore(since))) {
                observed.put(workItem.id(), Optional.of(workItem));
            }
        }
        List<String> ids = List.copyOf(boundIds);
        Set<String> existing = new LinkedHashSet<>();
        for (int i = 0; i < ids.size(); i += 50) {
            List<String> batch = ids.subList(i, Math.min(ids.size(), i + 50));
            this.polarion.queryWorkItems(projectId, "id:(" + String.join(" ", batch) + ")", token).forEach(w -> existing.add(w.id()));
        }
        ids.stream().filter(id -> !existing.contains(id)).forEach(id -> observed.put(id, Optional.empty()));
        return observed;
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
            if (!TRACE_LINKS.equals(field) && !Objects.equals(blankAsNull(value), blankAsNull(current.get(field)))) { // links: traceEdited
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

    /** A trace link the model wants: the owner element {@code role} the requirement (element id). */
    record TraceTarget(String role, String requirementId) {
    }

    /**
     * The trace view of one project for push and pull (P1 T6): which requirements each element satisfies or verifies,
     * which Work Items are bound to requirements, and which links on a Work Item are therefore managed.
     */
    final class Trace {

        private final String projectId;

        private final Map<String, ElementSnapshot> elements;

        private final List<StoredLink> projectLinks;

        /** Requirement element id → its Work Item (bound in this project). */
        final Map<String, String> requirementWorkItems = new LinkedHashMap<>();

        Trace(String projectId, Map<String, ElementSnapshot> elements, List<StoredLink> projectLinks) {
            this.projectId = projectId;
            this.elements = elements;
            this.projectLinks = projectLinks;
            projectLinks.stream().filter(link -> REQUIREMENT.equals(link.elementType()))
                    .forEach(link -> this.requirementWorkItems.put(link.elementId(), link.workItemId()));
        }

        boolean isOwner(ElementSnapshot element) {
            return element != null && !REQUIREMENT.equals(element.type()) && SyncService.this.properties.getWorkItemTypes().containsKey(element.type());
        }

        /** The requirements this element satisfies / verifies, sorted. */
        List<TraceTarget> targetsOf(ElementSnapshot element) {
            if (!this.isOwner(element)) {
                return List.of();
            }
            SyncProperties.LinkRoles roles = SyncService.this.properties.getLinkRoles();
            List<TraceTarget> targets = new ArrayList<>();
            for (ElementSnapshot requirement : this.elements.values()) {
                if (requirement.satisfiedBy().contains(element.id())) {
                    targets.add(new TraceTarget(roles.getSatisfiedBy(), requirement.id()));
                }
                if (requirement.verifiedBy().contains(element.id())) {
                    targets.add(new TraceTarget(roles.getVerifiedBy(), requirement.id()));
                }
            }
            targets.sort(java.util.Comparator.comparing(TraceTarget::role).thenComparing(TraceTarget::requirementId));
            return targets;
        }

        /** The targets as "role:WI", or a set that cannot equal any current set if a requirement has no Work Item yet. */
        Set<String> resolvedOrNull(List<TraceTarget> targets) {
            Set<String> resolved = new java.util.TreeSet<>();
            for (TraceTarget target : targets) {
                String workItem = this.requirementWorkItems.get(target.requirementId());
                resolved.add(target.role() + ":" + (workItem == null ? "#" + target.requirementId() : workItem));
            }
            return resolved;
        }

        /** The Work Item's links that this service manages: a trace role, to a Work Item bound to a requirement. */
        Set<String> managedLinks(WorkItem workItem) {
            SyncProperties.LinkRoles roles = SyncService.this.properties.getLinkRoles();
            Set<String> requirementItems = new LinkedHashSet<>(this.requirementWorkItems.values());
            Set<String> managed = new java.util.TreeSet<>();
            for (PolarionClient.WorkItemLink link : workItem.links()) {
                if ((link.role().equals(roles.getSatisfiedBy()) || link.role().equals(roles.getVerifiedBy())) && this.projectId.equals(link.targetProject())
                        && requirementItems.contains(link.targetId())) {
                    managed.add(link.role() + ":" + link.targetId());
                }
            }
            return managed;
        }

        /**
         * The ids to push: the given ones, plus for each requirement among them the elements that satisfy or verify it
         * and the elements whose last pushed links point at its Work Item (so a removed relation is removed in Polarion).
         */
        List<String> withOwners(Set<String> ids) {
            Set<String> all = new LinkedHashSet<>(ids);
            for (String id : ids) {
                ElementSnapshot requirement = this.elements.get(id);
                if (requirement == null || !REQUIREMENT.equals(requirement.type())) {
                    continue;
                }
                requirement.satisfiedBy().stream().filter(this.elements::containsKey).forEach(all::add);
                requirement.verifiedBy().stream().filter(this.elements::containsKey).forEach(all::add);
                String workItem = this.requirementWorkItems.get(id);
                if (workItem != null) {
                    for (StoredLink link : this.projectLinks) {
                        String pushed = link.pushedFields() == null ? null : SyncService.this.fromJson(link.pushedFields()).get(TRACE_LINKS);
                        if (pushed != null && java.util.Arrays.stream(pushed.split(",")).anyMatch(entry -> entry.endsWith(":" + workItem))
                                && this.elements.containsKey(link.elementId())) {
                            all.add(link.elementId());
                        }
                    }
                }
            }
            return List.copyOf(all);
        }

        static String join(Set<String> links) {
            return String.join(",", new java.util.TreeSet<>(links));
        }

        static List<PolarionClient.WorkItemLink> links(String projectId, List<String> entries) {
            return entries.stream().map(entry -> {
                int colon = entry.indexOf(':');
                return new PolarionClient.WorkItemLink(entry.substring(0, colon), projectId, entry.substring(colon + 1));
            }).toList();
        }
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

        /** P1 T6: this element's Work Item carries managed trace links. */
        boolean traceOwner;

        List<TraceTarget> desiredTrace = List.of();

        /** The managed links on the Work Item now, as "role:WI". */
        Set<String> currentTrace = Set.of();

        /** The links written by this push, as "role:WI"; set during execution. */
        Set<String> resolvedTrace = Set.of();

        /** The links will change (decided in the precheck, for counting and dry runs). */
        boolean traceDiffers;

        Plan trace(boolean owner, List<TraceTarget> desired, Set<String> current, boolean differs) {
            this.traceOwner = owner;
            this.desiredTrace = List.copyOf(desired);
            this.currentTrace = Set.copyOf(current);
            this.resolvedTrace = Set.copyOf(current);
            this.traceDiffers = differs;
            return this;
        }

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

        static Plan skip(ElementSnapshot element, StoredLink link, Map<String, String> desired) {
            return new Plan(Kind.SKIP, element, link, link.workItemType(), link.workItemId(), desired, Map.of(), Map.of());
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

package com.example.sysarch.sync.consistency;

import com.example.sysarch.link.LinkRepository;
import com.example.sysarch.link.LinkRepository.StoredLink;
import com.example.sysarch.sync.SyncProperties;
import com.example.sysarch.sync.SyncService;
import com.example.sysarch.sync.SyncStore;
import com.example.sysarch.sync.api.model.ConsistencyReport;
import com.example.sysarch.sync.api.model.ConsistencyReportIssuesInner;
import com.example.sysarch.sync.api.model.Link;
import com.example.sysarch.sync.api.model.SyncResultErrorsInner;
import com.example.sysarch.sync.consistency.ConsistencyRules.Facts;
import com.example.sysarch.sync.consistency.ConsistencyRules.Issue;
import com.example.sysarch.sync.model.ModelGateway;
import com.example.sysarch.sync.model.ModelGateway.ConnectionRepair;
import com.example.sysarch.sync.model.ModelGateway.ElementSnapshot;
import com.example.sysarch.sync.polarion.PolarionClient;
import com.example.sysarch.sync.polarion.PolarionClient.WorkItem;
import com.example.sysarch.sync.polarion.PolarionException;
import com.example.sysarch.sync.polarion.PolarionToken;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code /consistency/report} and {@code /consistency/repair} (spec §7, T8).
 *
 * <p>The report only detects; it changes no model data. Repair runs only for the issues a person names: there is no
 * "repair everything" (T8 step 2), and each issue gets its own action in the result. R2 follows the project's fixed
 * strategy ({@link ConsistencyProperties}): MODEL_WINS rewrites Polarion, POLARION_WINS rewrites the model (through the
 * editing context, with the on-drop constraints).
 */
@Service
public class ConsistencyService {

    public static final String REPAIR = "REPAIR";

    private final ModelGateway model;

    private final LinkRepository links;

    private final ConsistencyStore store;

    private final SyncStore audit;

    private final SyncService sync;

    private final PolarionClient polarion;

    private final SyncProperties syncProperties;

    private final ConsistencyProperties properties;

    private final ObjectMapper json;

    private final TransactionTemplate transaction;

    private final ConsistencyRules rules;

    public ConsistencyService(ModelGateway model, LinkRepository links, ConsistencyStore store, SyncStore audit, SyncService sync, PolarionClient polarion,
            SyncProperties syncProperties, ConsistencyProperties properties, ObjectMapper json, TransactionTemplate transaction) {
        this.model = Objects.requireNonNull(model);
        this.links = Objects.requireNonNull(links);
        this.store = Objects.requireNonNull(store);
        this.audit = Objects.requireNonNull(audit);
        this.sync = Objects.requireNonNull(sync);
        this.polarion = Objects.requireNonNull(polarion);
        this.syncProperties = Objects.requireNonNull(syncProperties);
        this.properties = Objects.requireNonNull(properties);
        this.json = Objects.requireNonNull(json);
        this.transaction = Objects.requireNonNull(transaction);
        this.rules = new ConsistencyRules(syncProperties);
    }

    // =========================================================== report

    /**
     * @param token with a token, every bound Work Item is looked up in Polarion now; without, the report judges from
     *            what is recorded (pull results, webhook marks), so R1 is still found but R2 only on the canvas side
     */
    public ConsistencyReport report(String projectId, PolarionToken token) {
        Facts facts = this.facts(projectId, token);
        List<Issue> issues = this.rules.evaluate(facts);
        if (facts.isLive()) {
            facts.live().keySet().forEach(workItemId -> this.store.clearCheck(projectId, workItemId)); // the marks are judged
        }
        return toReport(projectId, issues);
    }

    static ConsistencyReport toReport(String projectId, List<Issue> issues) {
        return new ConsistencyReport().projectId(projectId).generatedAt(OffsetDateTime.now()).issues(issues.stream()
                .map(issue -> new ConsistencyReportIssuesInner().id(issue.id()).rule(ConsistencyReportIssuesInner.RuleEnum.fromValue(issue.rule()))
                        .severity(ConsistencyReportIssuesInner.SeverityEnum.fromValue(issue.severity())).elementId(issue.elementId())
                        .workItemId(issue.workItemId()).message(issue.message()).repairable(issue.repairable()))
                .toList());
    }

    private Facts facts(String projectId, PolarionToken token) {
        Map<String, ElementSnapshot> elements;
        try {
            elements = this.model.readAll(projectId);
        } catch (ModelGateway.UnmappedProjectException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        List<StoredLink> bound = this.links.findByProject(projectId, null);
        Map<String, Optional<WorkItem>> live = null;
        if (token != null) {
            live = new LinkedHashMap<>();
            for (String workItemId : new LinkedHashSet<>(bound.stream().map(StoredLink::workItemId).sorted().toList())) {
                live.put(workItemId, this.polarion.getWorkItem(projectId, workItemId, token));
            }
        }
        return new Facts(projectId, elements, bound, this.store.pendingChecks(projectId), this.store.pendingClaims(projectId), live);
    }

    // =========================================================== repair

    /** What repair did with one issue. */
    public record RepairAction(String issueId, String rule, String action, String status, String message) {

        static RepairAction done(Issue issue, String action, String message) {
            return new RepairAction(issue.id(), issue.rule(), action, "DONE", message);
        }

        static RepairAction failed(Issue issue, String action, String message) {
            return new RepairAction(issue.id(), issue.rule(), action, "FAILED", message);
        }
    }

    public record RepairResult(String projectId, String strategy, List<RepairAction> actions) {
    }

    public RepairResult repair(String projectId, List<String> issueIds, String requestedStrategy, PolarionToken token, String actor) {
        String strategy = this.properties.strategyOf(projectId);
        if (strategy == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "no consistency strategy is configured for project " + projectId
                    + " (CONSISTENCY_STRATEGY or sysarch.consistency.strategies." + projectId + ")");
        }
        if (requestedStrategy != null && !requestedStrategy.equals(strategy)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "the strategy of project " + projectId + " is fixed to " + strategy
                    + " and cannot be switched per request");
        }
        if (issueIds == null || issueIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name the issues to repair (issueIds); there is no repair-all");
        }

        Facts facts = this.facts(projectId, token);
        Map<String, Issue> current = new LinkedHashMap<>();
        this.rules.evaluate(facts).forEach(issue -> current.put(issue.id(), issue));

        List<RepairAction> actions = new ArrayList<>();
        List<Issue> unbound = new ArrayList<>();
        for (String issueId : new LinkedHashSet<>(issueIds)) {
            Issue issue = current.get(issueId);
            if (issue == null) {
                actions.add(new RepairAction(issueId, null, "NONE", "SKIPPED", "not in the current report (already resolved, or never existed)"));
                continue;
            }
            switch (issue.rule()) {
                case "R1" -> actions.add(this.repairOrphan(projectId, issue, actor));
                case "R2" -> actions.add(this.repairDanglingEdge(projectId, issue, facts, strategy, token, actor));
                case "R3" -> actions.add(this.repairDuplicate(projectId, issue, actor));
                case "R4" -> actions.add(new RepairAction(issue.id(), "R4", "NONE", "REFUSED", "层违规不允许自动修复，须人工修改模型"));
                case "R5" -> unbound.add(issue);
                default -> throw new IllegalStateException(issue.rule());
            }
        }
        if (!unbound.isEmpty()) {
            actions.addAll(this.repairUnbound(projectId, unbound, token, actor));
        }
        if (actions.stream().anyMatch(action -> "DONE".equals(action.status()))) {
            this.model.refreshViews(projectId); // bindings changed outside the model: recompute the Validation view
        }
        return new RepairResult(projectId, strategy, actions);
    }

    /** R1: unbind and mark the element pending claim (spec §7). */
    private RepairAction repairOrphan(String projectId, Issue issue, String actor) {
        this.transaction.executeWithoutResult(status -> {
            this.links.delete(issue.elementId());
            this.store.addPendingClaim(issue.elementId(), projectId, issue.workItemId(), "R1: work item deleted in Polarion");
            this.audit.audit(projectId, issue.elementId(), issue.workItemId(), REPAIR, "binding", issue.workItemId(), null, actor);
        });
        return RepairAction.done(issue, "UNBIND_AND_MARK_PENDING_CLAIM", "绑定已解除，元素 " + issue.elementId() + " 转入待认领");
    }

    /** R3: keep one binding, the others become CONFLICT. */
    private RepairAction repairDuplicate(String projectId, Issue issue, String actor) {
        this.transaction.executeWithoutResult(status -> issue.others().forEach(elementId -> this.links.find(elementId).ifPresent(link -> {
            this.links.update(link.withState(Link.StateEnum.CONFLICT.getValue()));
            this.audit.audit(projectId, elementId, link.workItemId(), REPAIR, "state", link.state(), Link.StateEnum.CONFLICT.getValue(), actor);
        })));
        return RepairAction.done(issue, "KEEP_ONE_MARK_OTHERS_CONFLICT", "保留 " + issue.keep() + "，" + issue.others() + " 置为 CONFLICT");
    }

    /** R2 by the project's strategy. Both directions need the Work Item as it is now, so both need a token. */
    private RepairAction repairDanglingEdge(String projectId, Issue issue, Facts facts, String strategy, PolarionToken token, String actor) {
        if (token == null) {
            return RepairAction.failed(issue, "NONE", "R2 repair reads or writes Polarion: send your Polarion token (Authorization: Bearer ...)");
        }
        StoredLink link = this.links.find(issue.elementId()).orElse(null);
        WorkItem workItem = facts.live().getOrDefault(issue.workItemId(), Optional.empty()).orElse(null);
        if (link == null || workItem == null) {
            return RepairAction.failed(issue, "NONE", "binding or work item changed since the report; run the report again");
        }
        ElementSnapshot connection = facts.elements().get(issue.elementId());
        SyncProperties.Fields fields = this.syncProperties.getFields();
        String polarionSource = text(workItem.attribute(fields.getSource()));
        String polarionTarget = text(workItem.attribute(fields.getTarget()));
        try {
            if (ConsistencyProperties.MODEL_WINS.equals(strategy)) {
                if (connection == null) {
                    this.polarion.deleteWorkItems(projectId, List.of(workItem.id()), token);
                    this.transaction.executeWithoutResult(status -> {
                        this.links.delete(link.elementId());
                        this.audit.audit(projectId, link.elementId(), workItem.id(), REPAIR, "workItem", workItem.id(), null, actor);
                    });
                    return RepairAction.done(issue, "DELETE_WORK_ITEM", "画布无此连线，已删除 Polarion 中的 " + workItem.id());
                }
                Map<String, Object> endpoints = new LinkedHashMap<>();
                endpoints.put(fields.getSource(), connection.sourceId());
                endpoints.put(fields.getTarget(), connection.targetId());
                this.polarion.updateWorkItem(projectId, workItem.id(), endpoints, token);
                this.recordEndpoints(projectId, link, connection.sourceId(), connection.targetId(), polarionSource, polarionTarget, actor);
                return RepairAction.done(issue, "WRITE_ENDPOINTS_TO_POLARION", workItem.id() + " 端点改为 " + connection.sourceId() + " → " + connection.targetId());
            }
            // POLARION_WINS
            if (connection == null) {
                String name = Optional.ofNullable(text(workItem.attribute(fields.getName()))).orElse(workItem.title());
                String failure = this.model.repairConnection(projectId, new ConnectionRepair(link.elementId(), polarionSource, polarionTarget, name, false));
                return failure == null ? RepairAction.done(issue, "CREATE_CONNECTION", "按 Polarion 在画布重建连线 " + polarionSource + " → " + polarionTarget)
                        : RepairAction.failed(issue, "CREATE_CONNECTION", failure);
            }
            if (polarionSource == null && polarionTarget == null) {
                String failure = this.model.repairConnection(projectId, new ConnectionRepair(link.elementId(), null, null, null, true));
                if (failure != null) {
                    return RepairAction.failed(issue, "DELETE_CONNECTION", failure);
                }
                this.transaction.executeWithoutResult(status -> {
                    this.links.delete(link.elementId());
                    this.audit.audit(projectId, link.elementId(), workItem.id(), REPAIR, "connection", link.elementId(), null, actor);
                });
                return RepairAction.done(issue, "DELETE_CONNECTION", "Polarion 无此关系，已从模型删除连线 " + link.elementId() + " 并解除绑定");
            }
            String failure = this.model.repairConnection(projectId, new ConnectionRepair(link.elementId(), polarionSource, polarionTarget, null, false));
            if (failure != null) {
                return RepairAction.failed(issue, "SET_CONNECTION_ENDPOINTS", failure);
            }
            this.recordEndpoints(projectId, link, polarionSource, polarionTarget, connection.sourceId(), connection.targetId(), actor);
            return RepairAction.done(issue, "SET_CONNECTION_ENDPOINTS", "连线端点按 Polarion 改为 " + polarionSource + " → " + polarionTarget);
        } catch (PolarionException e) {
            return RepairAction.failed(issue, "NONE", "Polarion: " + e.getMessage());
        }
    }

    /** Model and Polarion agree on the endpoints again: record them as pushed, so the next push sees no conflict. */
    private void recordEndpoints(String projectId, StoredLink link, String source, String target, String oldSource, String oldTarget, String actor) {
        SyncProperties.Fields fields = this.syncProperties.getFields();
        Map<String, String> pushed = link.pushedFields() == null ? new LinkedHashMap<>() : this.fromJson(link.pushedFields());
        pushed.put(fields.getSource(), source);
        pushed.put(fields.getTarget(), target);
        this.transaction.executeWithoutResult(status -> {
            this.links.update(new StoredLink(link.elementId(), link.elementType(), link.workItemId(), link.workItemType(), link.projectId(), link.state(),
                    link.modelRev(), link.wiRev(), link.etag(), link.lastPushAt(), link.lastPullAt(), this.toJson(pushed), link.workItemTitle()));
            this.audit.audit(projectId, link.elementId(), link.workItemId(), REPAIR, fields.getSource(), oldSource, source, actor);
            this.audit.audit(projectId, link.elementId(), link.workItemId(), REPAIR, fields.getTarget(), oldTarget, target, actor);
        });
    }

    /** R5: create the Work Items in one push (all or nothing, as every push). */
    private List<RepairAction> repairUnbound(String projectId, List<Issue> issues, PolarionToken token, String actor) {
        if (token == null) {
            return issues.stream().map(i -> RepairAction.failed(i, "CREATE_WORK_ITEM", "creating work items needs your Polarion token (Authorization: Bearer ...)"))
                    .toList();
        }
        SyncService.Outcome outcome = this.sync.push(projectId, issues.stream().map(Issue::elementId).toList(), false, token, actor);
        List<SyncResultErrorsInner> errors = outcome.result().getErrors();
        if (outcome.status() == 200 && errors.isEmpty()) {
            return issues.stream().map(i -> RepairAction.done(i, "CREATE_WORK_ITEM", "已创建 Work Item " + this.links.find(i.elementId())
                    .map(StoredLink::workItemId).orElse("?") + " 并绑定")).toList();
        }
        return issues.stream().map(i -> RepairAction.failed(i, "CREATE_WORK_ITEM", errors.stream()
                .filter(e -> e.getElementId() == null || e.getElementId().equals(i.elementId())).map(e -> e.getCode() + ": " + e.getMessage()).findFirst()
                .orElse("push refused (HTTP " + outcome.status() + "): another element of the batch failed, nothing was created"))).toList();
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
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
}

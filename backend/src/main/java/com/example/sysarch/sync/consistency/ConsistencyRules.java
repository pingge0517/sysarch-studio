package com.example.sysarch.sync.consistency;

import com.example.sysarch.link.LinkRepository.StoredLink;
import com.example.sysarch.sync.SyncProperties;
import com.example.sysarch.sync.api.model.Link;
import com.example.sysarch.sync.model.ModelGateway.ElementSnapshot;
import com.example.sysarch.sync.polarion.PolarionClient.WorkItem;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The five consistency rules of spec §7, as a pure function of what is known: the model, the bindings, the marks
 * left by webhooks, and, when the caller sent a Polarion token, the Work Items as they are now.
 *
 * <ul>
 * <li><b>R1 orphan binding</b> (BLOCKER): the element exists, its Work Item was deleted. Known live (404), or without a
 * token from a pull that marked the link ORPHAN or a signed {@code workitem.deleted} webhook.</li>
 * <li><b>R2 dangling edge</b> (MAJOR), for bound Connections: the canvas has the connection but the Work Item's
 * {@code source}/{@code target} fields say otherwise (empty = Polarion has no such relation; needs a token), or the
 * connection is gone from the canvas while its Work Item still exists (with a token: and still has endpoints).</li>
 * <li><b>R3 duplicate binding</b> (MAJOR): one Work Item bound by several elements. Links already in CONFLICT are not
 * counted, so the R3 repair (mark the others CONFLICT) resolves it.</li>
 * <li><b>R4 layer violation</b> (BLOCKER, never repairable): an Allocate failing the OCL {@code LayerAdjacentOnly}.</li>
 * <li><b>R5 unbound element</b> (MINOR): an element of a synchronized type with no binding.</li>
 * </ul>
 *
 * <p>Issue ids are stable ({@code R1:<elementId>}, {@code R3:<workItemId>}...), so a repair can name the issues of an
 * earlier report and is matched against a fresh evaluation.
 */
public final class ConsistencyRules {

    public static final String BLOCKER = "BLOCKER";

    public static final String MAJOR = "MAJOR";

    public static final String MINOR = "MINOR";

    private static final String CONNECTION = "Connection";

    private static final String ALLOCATE = "Allocate";

    private final SyncProperties properties;

    public ConsistencyRules(SyncProperties properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    /**
     * Everything a report is judged on.
     *
     * @param pendingChecks Work Item id to the latest webhook event not yet judged
     * @param pendingClaims element id to the Work Item it was bound to before an R1 repair
     * @param live Work Item id to the Work Item now (empty = deleted), or null when no token was given
     */
    public record Facts(String projectId, Map<String, ElementSnapshot> elements, List<StoredLink> links, Map<String, String> pendingChecks,
            Map<String, String> pendingClaims, Map<String, Optional<WorkItem>> live) {

        public boolean isLive() {
            return this.live != null;
        }
    }

    /**
     * @param keep for R3: the element that keeps the binding
     * @param others for R3: the elements to mark CONFLICT
     */
    public record Issue(String id, String rule, String severity, String elementId, String workItemId, String message, boolean repairable, String keep,
            List<String> others) {
    }

    public List<Issue> evaluate(Facts facts) {
        List<Issue> issues = new ArrayList<>();
        for (StoredLink link : facts.links()) {
            ElementSnapshot element = facts.elements().get(link.elementId());
            boolean deleted = this.isDeleted(facts, link);
            if (element != null && deleted) {
                issues.add(new Issue("R1:" + link.elementId(), "R1", BLOCKER, link.elementId(), link.workItemId(),
                        "孤儿绑定：" + label(element) + " 绑定的 Work Item " + link.workItemId() + " 已在 Polarion 删除；修复：解除绑定，元素转入待认领", true, null,
                        List.of()));
            }
            if (CONNECTION.equals(link.elementType()) && !deleted) {
                this.danglingEdge(facts, link, element).ifPresent(issues::add);
            }
        }
        issues.addAll(this.duplicateBindings(facts));
        for (ElementSnapshot element : facts.elements().values()) {
            if (ALLOCATE.equals(element.type())) {
                element.errors().stream().filter(e -> "LayerAdjacentOnly".equals(e.code())).findFirst()
                        .ifPresent(e -> issues.add(new Issue("R4:" + element.id(), "R4", BLOCKER, element.id(), null,
                                "层违规：" + label(element) + " " + e.message() + "；不允许自动修复，须人工修改模型", false, null, List.of())));
            }
        }
        var bound = facts.links().stream().map(StoredLink::elementId).collect(java.util.stream.Collectors.toSet());
        for (ElementSnapshot element : facts.elements().values()) {
            if (this.properties.getWorkItemTypes().containsKey(element.type()) && !bound.contains(element.id())) {
                String former = facts.pendingClaims().get(element.id());
                String state = former == null ? "未绑定 Work Item" : "待认领（原绑定 " + former + " 已删除）";
                issues.add(new Issue("R5:" + element.id(), "R5", MINOR, element.id(), former, "未绑定元素：" + label(element) + " " + state
                        + "；修复：创建 Work Item 并绑定，或忽略", true, null, List.of()));
            }
        }
        issues.sort(Comparator.comparing(Issue::rule).thenComparing(Issue::id));
        return issues;
    }

    private boolean isDeleted(Facts facts, StoredLink link) {
        if (facts.isLive()) {
            return facts.live().getOrDefault(link.workItemId(), Optional.empty()).isEmpty();
        }
        return Link.StateEnum.ORPHAN.getValue().equals(link.state()) || ConsistencyStore.DELETED.equals(facts.pendingChecks().get(link.workItemId()));
    }

    private Optional<Issue> danglingEdge(Facts facts, StoredLink link, ElementSnapshot connection) {
        String id = "R2:" + link.elementId();
        Optional<WorkItem> workItem = facts.isLive() ? facts.live().get(link.workItemId()) : Optional.empty();
        String polarionSource = workItem.map(w -> this.value(w, this.properties.getFields().getSource())).orElse(null);
        String polarionTarget = workItem.map(w -> this.value(w, this.properties.getFields().getTarget())).orElse(null);
        boolean polarionHasRelation = polarionSource != null || polarionTarget != null;
        if (connection == null) {
            if (facts.isLive() && !polarionHasRelation) {
                return Optional.empty();
            }
            String relation = facts.isLive() ? "（" + polarionSource + " → " + polarionTarget + "）" : "";
            return Optional.of(new Issue(id, "R2", MAJOR, link.elementId(), link.workItemId(),
                    "悬空连线：Polarion 有关系 " + link.workItemId() + relation + "，画布上已无连线 " + link.elementId(), true, null, List.of()));
        }
        if (!facts.isLive()) {
            return Optional.empty();
        }
        if (Objects.equals(polarionSource, connection.sourceId()) && Objects.equals(polarionTarget, connection.targetId())) {
            return Optional.empty();
        }
        String polarion = polarionHasRelation ? link.workItemId() + " 为 " + polarionSource + " → " + polarionTarget : link.workItemId() + " 无对应关系";
        return Optional.of(new Issue(id, "R2", MAJOR, link.elementId(), link.workItemId(),
                "悬空连线：画布连线 " + label(connection) + " 为 " + connection.sourceId() + " → " + connection.targetId() + "，Polarion " + polarion, true, null,
                List.of()));
    }

    private List<Issue> duplicateBindings(Facts facts) {
        Map<String, List<StoredLink>> byWorkItem = new LinkedHashMap<>();
        facts.links().stream().filter(l -> !Link.StateEnum.CONFLICT.getValue().equals(l.state())).sorted(Comparator.comparing(StoredLink::workItemId))
                .forEach(l -> byWorkItem.computeIfAbsent(l.workItemId(), k -> new ArrayList<>()).add(l));
        List<Issue> issues = new ArrayList<>();
        byWorkItem.forEach((workItemId, links) -> {
            if (links.size() > 1) {
                String keep = this.keeper(facts, workItemId, links);
                List<String> others = links.stream().map(StoredLink::elementId).filter(e -> !e.equals(keep)).sorted().toList();
                issues.add(new Issue("R3:" + workItemId, "R3", MAJOR, keep, workItemId, "重复绑定：Work Item " + workItemId + " 被 " + links.size() + " 个元素绑定（"
                        + String.join(", ", links.stream().map(StoredLink::elementId).sorted().toList()) + "）；修复：保留 " + keep + "，其余置为 CONFLICT", true,
                        keep, others));
            }
        });
        return issues;
    }

    /**
     * The binding R3 keeps: the element the Work Item itself names (its model-owned element id field), else the one
     * pushed first, else the smallest element id.
     */
    private String keeper(Facts facts, String workItemId, List<StoredLink> links) {
        if (facts.isLive()) {
            String named = facts.live().getOrDefault(workItemId, Optional.empty()).map(w -> this.value(w, this.properties.getFields().getElementId()))
                    .orElse(null);
            if (named != null && links.stream().anyMatch(l -> l.elementId().equals(named))) {
                return named;
            }
        }
        return links.stream().min(Comparator.comparing((StoredLink l) -> l.lastPushAt() == null ? OffsetDateTime.MAX : l.lastPushAt())
                .thenComparing(StoredLink::elementId)).orElseThrow().elementId();
    }

    private String value(WorkItem workItem, String field) {
        Object value = workItem.attribute(field);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private static String label(ElementSnapshot element) {
        return element.type() + " " + element.id() + (element.name() == null || element.name().isBlank() ? "" : "（" + element.name() + "）");
    }
}

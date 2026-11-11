package com.example.sysarch.view.projection;

import com.example.sysarch.ElementIds;
import com.example.sysarch.view.api.AutoExpand;
import com.example.sysarch.view.api.DiagramDescription;
import com.example.sysarch.view.api.EdgeStyle;
import com.example.sysarch.view.api.Filter;
import com.example.sysarch.view.api.NodeStyle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Computes what a VSM diagram shows for the current model: the model is the only source of truth, and the
 * diagram is a pure function of (description, root, active filters) (spec §5.1). Independent of Sirius Web; the
 * Sirius adapter renders the resulting {@link DiagramSnapshot}.
 *
 * <p>P0 projection rules. The DSL leaves these open; they were decided for T4 and are documented here so they
 * are not implicit:
 * <ol>
 * <li><b>Scope</b>: all elements of the diagram root's resource, in document order.</li>
 * <li><b>Peer nodes</b>: a node style whose type is the diagram root's type (or a super/subtype of it) shows
 * every such element in scope. This is how a BDD shows all blocks, including newly created ones. (P3 T6) So does a style of a
 * class that can only be a document root (no containment holds it: Actor). Any other
 * node style shows only elements reachable from the root through {@code autoExpand} (feature, depth).</li>
 * <li><b>Border nodes</b> sit on the node of their {@code affiliationFeature} value, or on the diagram frame
 * when that value is the root. Without such a parent they are not shown.</li>
 * <li><b>One node per element.</b> When several styles apply to an element, an element with
 * {@code isAbstract = true} gets the last one and every other element gets the first (BDD
 * {@code block}/{@code abstractBlock}). (P3 T5) A style with a {@code when} applies only where it holds, and the first
 * such style that holds wins before the rule above (state machine: {@code isInitial} → filled circle, {@code isFinal}
 * → double circle, the rest the plain state style).</li>
 * <li><b>Edges</b>: one per (source end value, target end value) of each element of the edge's type, drawn only
 * when both ends have nodes; (P3 T5) a further element between the same two nodes gets its own edge, id suffixed with
 * {@code #<element id>}. Styles with the same type and the same ends are alternatives: the element gets the
 * first whose {@code when} holds (P3 T5), else the one whose id matches its {@code kind} literal (IBD {@code assembly}/{@code delegation} by {@code Connection.kind}),
 * otherwise the first. Switching style keeps the edge id.
 * An end element without a node of its own is drawn to the node of its {@code typedBy}, and the edge label is
 * then read from that end element. For example, a BDD composition runs from the owner block to the part's type
 * and is labelled with the part's name. (P1 T3) An end that has neither is drawn to the nearest container that has
 * a node, a container's {@code typedBy} counting as above, and the label stays the edge element's: a layer view
 * draws no ports, so a connection runs between the blocks that own its ports (a port on a part: the part's type).</li>
 * <li><b>Filters</b>: an active filter hides the root-type elements its expression rejects, together with their
 * border nodes and edges. The model is unchanged. A filter marked {@code alwaysOn} (a layer view's
 * {@code layer == 'X'}, P1 T3) is active whatever is switched on.</li>
 * </ol>
 */
public final class DiagramProjector {

    /** P3 T3: the reserved affiliationFeature "the element that contains it" (vsm-compiler accepts it). */
    public static final String EOBJECT_CONTAINER = "eContainer";

    /**
     * P3 (spec §9): the partition to show, passed with the active filter ids as {@code "§partition=N"} (0-based), so the
     * projection cache keys on it like on filters. Not a filter id.
     */
    public static final String PARTITION_TOKEN = "§partition=";

    static final String ABSTRACT_FEATURE = "isAbstract";

    static final String TYPE_FEATURE = "typedBy";

    static final String KIND_FEATURE = "kind";

    private DiagramProjector() {
    }

    public static DiagramSnapshot project(DiagramDescription description, EObject root) {
        return project(description, root, Set.of());
    }

    /**
     * @param activeFilterIds ids of the description's filters that are switched on
     * @throws IllegalArgumentException if the root has the wrong type or a filter id is unknown
     */
    public static DiagramSnapshot project(DiagramDescription description, EObject root, Set<String> activeFilterIds) {
        return projectWithElements(description, root, activeFilterIds).snapshot();
    }

    /**
     * Like {@link #project(DiagramDescription, EObject, Set)}, but also returns the model element behind every id in
     * the snapshot (node elements, edge label elements and edge ends). Renderers that need the EMF objects themselves,
     * such as the Sirius Web adapter, use this.
     */
    public static ProjectionResult projectWithElements(DiagramDescription description, EObject root, Set<String> activeFilterIds) {
        Projection projection = new Projection(description, root, activeFilterIds);
        DiagramSnapshot snapshot = projection.run();
        return new ProjectionResult(snapshot, Map.copyOf(projection.elementsById), Map.copyOf(projection.edgeEnds));
    }

    /**
     * @param elements element id to model element, for every id that appears in the snapshot
     * @param edgeEnds edge id to the element the edge belongs to and its unresolved end elements
     */
    public record ProjectionResult(DiagramSnapshot snapshot, Map<String, EObject> elements, Map<String, EdgeEnds> edgeEnds) {

        public EObject element(String id) {
            return this.elements.get(id);
        }
    }

    /**
     * The model behind one edge, before {@code typedBy} resolution: for a BDD composition the owner block, the owner
     * block and the Part; for a connection the Connection and its two ports.
     */
    public record EdgeEnds(EObject domainElement, EObject source, EObject target) {
    }

    private static final class Projection {

        private final DiagramDescription description;

        private final EObject root;

        private final EPackage ePackage;

        private final EClass domainClass;

        private final List<EObject> scope;

        private final Set<EObject> reachable;

        private final List<Predicate<EObject>> activeFilters = new ArrayList<>();

        private final Map<EObject, DiagramSnapshot.Node> nodes = new HashMap<>();

        private final List<DiagramSnapshot.Node> orderedNodes = new ArrayList<>();

        private final Set<String> nodeIds = new HashSet<>();

        private final Map<String, EObject> elementsById = new HashMap<>();

        private final Map<String, EdgeEnds> edgeEnds = new HashMap<>();

        /** Which element an edge id was first given to (P3 T5: a second element between the same ends gets its own edge). */
        private final Map<String, EObject> edgeOwners = new HashMap<>();

        private int partitionIndex;

        /** Elements whose node is left out because they belong to another partition (P3). */
        private final Set<EObject> otherPartitions = new HashSet<>();

        private DiagramSnapshot.Partition partition;

        private int hiddenEdges;

        Projection(DiagramDescription description, EObject root, Set<String> activeFilterIds) {
            this.description = description;
            this.root = root;
            this.ePackage = root.eClass().getEPackage();
            this.domainClass = this.eClass(description.domainType());
            if (!this.domainClass.isInstance(root)) {
                throw new IllegalArgumentException(description.id() + " needs a " + description.domainType() + " root, got " + root.eClass().getName());
            }
            this.scope = scope(root);
            this.reachable = this.reachable();
            Set<String> known = new HashSet<>();
            for (Filter filter : description.filters()) {
                known.add(filter.id());
                if (filter.alwaysOn() || activeFilterIds.contains(filter.id())) {
                    this.activeFilters.add(FilterExpression.parse(filter.expression()));
                }
            }
            for (String id : activeFilterIds) {
                if (id.startsWith(PARTITION_TOKEN)) {
                    this.partitionIndex = Integer.parseInt(id.substring(PARTITION_TOKEN.length()));
                    continue;
                }
                if (!known.contains(id)) {
                    throw new IllegalArgumentException("unknown filter '" + id + "' for diagram " + description.id());
                }
            }
        }

        DiagramSnapshot run() {
            List<NodeStyle> plain = this.description.nodes().stream().filter(n -> !n.borderNode()).toList();
            List<NodeStyle> border = this.description.nodes().stream().filter(NodeStyle::borderNode).toList();
            for (EObject element : this.scope) {
                this.projectNode(element, plain, false);
            }
            this.partition();
            for (EObject element : this.scope) {
                this.projectNode(element, border, true);
            }
            this.elementsById.put(elementId(this.root), this.root);
            List<DiagramSnapshot.Edge> edges = this.edges();
            if (this.partition != null) {
                this.partition = new DiagramSnapshot.Partition(this.partition.index(), this.partition.count(), this.partition.first(), this.partition.last(),
                        this.partition.total(), this.hiddenEdges);
            }
            return new DiagramSnapshot(this.description.id(), elementId(this.root), this.orderedNodes, edges, this.partition);
        }

        /**
         * P3 (spec §9 "状态机节点上限 200，超出启用分区视图"): with more top-level nodes than {@code partitionSize}, the nodes are
         * put in breadth-first order along the view's edges (starting from the first node in document order, then the
         * next one not yet reached), so neighbours stay together, cut into partitions of {@code partitionSize}, and only
         * the chosen partition is kept. Border nodes and edges of the others then drop out by themselves.
         */
        private void partition() {
            Integer size = this.description.partitionSize();
            List<DiagramSnapshot.Node> top = this.orderedNodes.stream().filter(n -> n.parentId() == null).toList();
            if (size == null || top.size() <= size) {
                return;
            }
            Map<String, Set<String>> neighbours = new HashMap<>();
            for (EdgeStyle style : this.description.edges()) {
                EClass type = this.eClass(style.domainType());
                for (EObject element : this.scope) {
                    if (type.isInstance(element)) {
                        for (EObject s : ends(element, style.sourceEnd())) {
                            for (EObject t : ends(element, style.targetEnd())) {
                                neighbours.computeIfAbsent(elementId(s), k -> new java.util.LinkedHashSet<>()).add(elementId(t));
                                neighbours.computeIfAbsent(elementId(t), k -> new java.util.LinkedHashSet<>()).add(elementId(s));
                            }
                        }
                    }
                }
            }
            Set<String> topIds = new java.util.LinkedHashSet<>();
            top.forEach(n -> topIds.add(n.id()));
            List<String> order = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String start : topIds) {
                if (!seen.add(start)) {
                    continue;
                }
                java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>(List.of(start));
                while (!queue.isEmpty()) {
                    String id = queue.poll();
                    order.add(id);
                    for (String next : neighbours.getOrDefault(id, Set.of())) {
                        if (topIds.contains(next) && seen.add(next)) {
                            queue.add(next);
                        }
                    }
                }
            }
            int count = (order.size() + size - 1) / size;
            int index = Math.max(0, Math.min(this.partitionIndex, count - 1));
            Set<String> keep = new HashSet<>(order.subList(index * size, Math.min(order.size(), (index + 1) * size)));
            Set<String> removed = new HashSet<>();
            for (DiagramSnapshot.Node node : List.copyOf(this.orderedNodes)) {
                if ((node.parentId() == null && !keep.contains(node.id())) || removed.contains(node.parentId())) {
                    removed.add(node.id());
                    this.orderedNodes.remove(node);
                    EObject element = this.elementsById.remove(node.elementId());
                    this.nodes.remove(element);
                    this.nodeIds.remove(node.id());
                    this.otherPartitions.add(element);
                }
            }
            this.partition = new DiagramSnapshot.Partition(index, count, index * size + 1, Math.min(order.size(), (index + 1) * size), order.size(), 0);
        }

        private void projectNode(EObject element, List<NodeStyle> styles, boolean borderPass) {
            if (this.nodes.containsKey(element) || this.hidden(element)) {
                return;
            }
            List<NodeStyle> candidates = new ArrayList<>();
            List<EObject> parents = new ArrayList<>();
            for (NodeStyle style : styles) {
                EClass type = this.eClass(style.appliesTo());
                if (!type.isInstance(element)) {
                    continue;
                }
                if (borderPass) {
                    EObject parent = single(element, style.affiliationFeature());
                    if (parent != null && (parent == this.root || this.nodes.containsKey(parent))) {
                        candidates.add(style);
                        parents.add(parent);
                    }
                } else if (this.isPeer(type) || this.reachable.contains(element)) {
                    candidates.add(style);
                    parents.add(null);
                }
            }
            if (candidates.isEmpty()) {
                return;
            }
            int chosen = -1;
            List<Integer> plain = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                String when = candidates.get(i).when();
                if (when == null) {
                    plain.add(i);
                } else if (chosen < 0 && condition(when).test(element)) {
                    chosen = i; // P3 T5: the first style whose when holds
                }
            }
            if (chosen < 0) {
                if (plain.isEmpty()) {
                    return;
                }
                chosen = plain.size() > 1 && isTrue(element, ABSTRACT_FEATURE) ? plain.get(plain.size() - 1) : plain.get(0);
            }
            NodeStyle style = candidates.get(chosen);
            EObject parent = parents.get(chosen);
            String id = elementId(element);
            if (!this.nodeIds.add(id)) {
                throw new IllegalStateException("two elements share the id '" + id + "' in diagram " + this.description.id());
            }
            List<DiagramSnapshot.Compartment> compartments = style.compartments().stream()
                    .map(c -> new DiagramSnapshot.Compartment(c.feature(), c.label(),
                            many(element, c.feature()).stream().map(item -> LabelTemplates.render(c.itemTemplate(), item)).toList()))
                    .toList();
            DiagramSnapshot.Node node = new DiagramSnapshot.Node(id, id, style.id(), parent == null ? null : elementId(parent),
                    nullToEmpty(LabelTemplates.render(style.labelTemplate(), element)) + valueBadge(element), LabelTemplates.render(style.subLabelTemplate(), element),
                    compartments);
            this.nodes.put(element, node);
            this.elementsById.put(id, element);
            this.orderedNodes.add(node);
        }

        private List<DiagramSnapshot.Edge> edges() {
            List<DiagramSnapshot.Edge> edges = new ArrayList<>();
            Set<String> edgeIds = new HashSet<>();
            for (List<EdgeStyle> alternatives : alternativeGroups(this.description.edges())) {
                EdgeStyle first = alternatives.get(0);
                EClass type = this.eClass(first.domainType());
                for (EObject element : this.scope) {
                    if (!type.isInstance(element) || this.hidden(element)) {
                        continue;
                    }
                    EdgeStyle style = chooseByKind(alternatives, element);
                    for (EObject source : ends(element, first.sourceEnd())) {
                        for (EObject target : ends(element, first.targetEnd())) {
                            this.edge(first.id(), style, element, source, target).filter(e -> edgeIds.add(e.id())).ifPresent(edges::add);
                        }
                    }
                }
            }
            return edges;
        }

        /**
         * @param idPrefix the first style of the alternative group, so an edge keeps its id (and its saved
         *        waypoints) when a change of {@code kind} switches it to another style
         */
        private Optional<DiagramSnapshot.Edge> edge(String idPrefix, EdgeStyle style, EObject element, EObject source, EObject target) {
            DiagramSnapshot.Node sourceNode = this.nodes.get(source);
            DiagramSnapshot.Node targetNode = this.nodes.get(target);
            EObject labelElement = element;
            if (sourceNode == null && this.nodes.get(single(source, TYPE_FEATURE)) != null) {
                sourceNode = this.nodes.get(single(source, TYPE_FEATURE));
                labelElement = source;
            }
            if (targetNode == null && this.nodes.get(single(target, TYPE_FEATURE)) != null) {
                targetNode = this.nodes.get(single(target, TYPE_FEATURE));
                labelElement = target;
            }
            if (sourceNode == null) {
                sourceNode = this.containerNode(source);
            }
            if (targetNode == null) {
                targetNode = this.containerNode(target);
            }
            if (sourceNode == null || targetNode == null) {
                if ((sourceNode != null || targetNode != null) && (this.otherPartitions.contains(source) || this.otherPartitions.contains(target))) {
                    this.hiddenEdges++; // P3: crosses into another partition
                }
                return Optional.empty();
            }
            String id = idPrefix + ":" + elementId(source) + "->" + elementId(target);
            EObject owner = this.edgeOwners.putIfAbsent(id, element);
            if (owner != null && owner != element) {
                // P3 T5: two transitions between the same states are two edges; the first keeps the plain id (saved layouts)
                id = id + "#" + elementId(element);
                this.edgeOwners.putIfAbsent(id, element);
            }
            this.elementsById.put(elementId(labelElement), labelElement);
            this.edgeEnds.putIfAbsent(id, new EdgeEnds(element, source, target));
            return Optional.of(new DiagramSnapshot.Edge(id, elementId(labelElement), style.id(), sourceNode.id(), targetNode.id(),
                    nullToEmpty(LabelTemplates.render(style.labelTemplate(), labelElement))));
        }

        /** The node of the nearest container of {@code end} (or of that container's {@code typedBy}); null if none. */
        private DiagramSnapshot.Node containerNode(EObject end) {
            for (EObject container = end.eContainer(); container != null; container = container.eContainer()) {
                DiagramSnapshot.Node node = this.nodes.get(container);
                if (node == null) {
                    node = this.nodes.get(single(container, TYPE_FEATURE));
                }
                if (node != null) {
                    return node;
                }
            }
            return null;
        }

        private boolean hidden(EObject element) {
            return this.domainClass.isInstance(element) && this.activeFilters.stream().anyMatch(f -> !f.test(element));
        }

        private boolean isPeer(EClass type) {
            return type.isSuperTypeOf(this.domainClass) || this.domainClass.isSuperTypeOf(type) || documentRootOnly(type);
        }

        /** Root plus everything reached by following each autoExpand feature up to its depth. */
        private Set<EObject> reachable() {
            Set<EObject> result = new LinkedHashSet<>();
            result.add(this.root);
            for (AutoExpand autoExpand : this.description.autoExpands()) {
                List<EObject> frontier = List.of(this.root);
                for (int depth = 0; depth < autoExpand.maxDepth() && !frontier.isEmpty(); depth++) {
                    List<EObject> next = new ArrayList<>();
                    for (EObject element : frontier) {
                        for (EObject child : many(element, autoExpand.feature())) {
                            if (result.add(child)) {
                                next.add(child);
                            }
                        }
                    }
                    frontier = next;
                }
            }
            return result;
        }

        /** {@code nsPrefix::Name} to the EClass, in the root's package (P0 has a single metamodel). */
        private EClass eClass(String domainType) {
            int separator = domainType.indexOf("::");
            String prefix = separator < 0 ? "" : domainType.substring(0, separator);
            if (prefix.equals(this.ePackage.getNsPrefix())
                    && this.ePackage.getEClassifier(domainType.substring(separator + 2)) instanceof EClass eClass) {
                return eClass;
            }
            throw new IllegalArgumentException("type " + domainType + " is not in metamodel " + this.ePackage.getNsURI());
        }
    }

    private static List<EObject> scope(EObject root) {
        List<EObject> scope = new ArrayList<>();
        if (root.eResource() != null) {
            root.eResource().getAllContents().forEachRemaining(scope::add);
        } else {
            EObject top = EcoreUtil.getRootContainer(root);
            scope.add(top);
            top.eAllContents().forEachRemaining(scope::add);
        }
        return scope;
    }

    /** Edge styles with the same type, source end and target end, grouped in declaration order. */
    private static List<List<EdgeStyle>> alternativeGroups(List<EdgeStyle> styles) {
        Map<String, List<EdgeStyle>> groups = new LinkedHashMap<>();
        for (EdgeStyle style : styles) {
            groups.computeIfAbsent(style.domainType() + "|" + style.sourceEnd() + "|" + style.targetEnd(), k -> new ArrayList<>()).add(style);
        }
        return List.copyOf(groups.values());
    }

    /**
     * Among alternative styles, the one whose id matches the element's {@code kind} literal, ignoring case and
     * underscores ({@code DELEGATION} picks {@code delegation}); otherwise the first.
     */
    /**
     * P3 T6: a class that no containment reference of its package can hold (Actor, like Requirement) only exists as a
     * document root, so nothing reaches it through autoExpand; its node style shows every such element in the document,
     * as peers do. A use-case diagram therefore shows its actors before any use case is linked to them.
     */
    static boolean documentRootOnly(EClass type) {
        return ROOT_ONLY.computeIfAbsent(type, t -> t.getEPackage().getEClassifiers().stream().filter(EClass.class::isInstance).map(EClass.class::cast)
                .flatMap(c -> c.getEReferences().stream()).filter(EReference::isContainment).noneMatch(r -> r.getEReferenceType().isSuperTypeOf(t)));
    }

    private static final java.util.Map<EClass, Boolean> ROOT_ONLY = new java.util.concurrent.ConcurrentHashMap<>();

    /** P3 T5: a style's {@code when}, parsed once per expression (vsm-compiler has checked it). */
    private static final java.util.Map<String, FilterExpression> CONDITIONS = new java.util.concurrent.ConcurrentHashMap<>();

    static FilterExpression condition(String when) {
        return CONDITIONS.computeIfAbsent(when, FilterExpression::parse);
    }

    private static EdgeStyle chooseByKind(List<EdgeStyle> alternatives, EObject element) {
        for (EdgeStyle style : alternatives) {
            if (style.when() != null && condition(style.when()).test(element)) {
                return style; // P3 T5
            }
        }
        alternatives = alternatives.stream().filter(s -> s.when() == null).toList().isEmpty() ? alternatives
                : alternatives.stream().filter(s -> s.when() == null).toList();
        if (alternatives.size() > 1 && element.eClass().getEStructuralFeature(KIND_FEATURE) instanceof EStructuralFeature kind
                && element.eGet(kind) instanceof Enumerator literal) {
            String wanted = normalize(literal.getLiteral());
            for (EdgeStyle style : alternatives) {
                if (normalize(style.id()).equals(wanted)) {
                    return style;
                }
            }
        }
        return alternatives.get(0);
    }

    private static String normalize(String name) {
        return name.replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static List<EObject> ends(EObject element, String end) {
        return EdgeStyle.SELF.equals(end) ? List.of(element) : many(element, end);
    }

    private static List<EObject> many(EObject element, String featureName) {
        EStructuralFeature feature = element.eClass().getEStructuralFeature(featureName);
        if (!(feature instanceof EReference)) {
            return List.of();
        }
        Object value = element.eGet(feature);
        if (value instanceof Collection<?> values) {
            return values.stream().filter(EObject.class::isInstance).map(EObject.class::cast).toList();
        }
        return value instanceof EObject eObject ? List.of(eObject) : List.of();
    }

    /**
     * P3 T4: a parameter with a value (a solver's result is written to {@code defaultValue}) shows it after its name,
     * read-only on the canvas ("以只读徽标显示"): editing the label renames, the value comes from solving or the properties.
     */
    static String valueBadge(EObject element) {
        if (element instanceof com.example.sysarch.model.Parameter parameter && parameter.eIsSet(com.example.sysarch.model.SysarchPackage.Literals.PARAMETER__DEFAULT_VALUE)) {
            double v = parameter.getDefaultValue();
            String text = Math.abs(v) >= 1e6 || (v != 0 && Math.abs(v) < 1e-3) ? String.format(java.util.Locale.ROOT, "%.4g", v)
                    : new java.math.BigDecimal(v).round(new java.math.MathContext(6)).stripTrailingZeros().toPlainString();
            return " = " + text;
        }
        return "";
    }

    private static EObject single(EObject element, String featureName) {
        if (element == null || featureName == null) {
            return null;
        }
        if (EOBJECT_CONTAINER.equals(featureName)) {
            return element.eContainer(); // P3 T3: "the element that contains it" (vsm-compiler's reserved word)
        }
        EStructuralFeature feature = element.eClass().getEStructuralFeature(featureName);
        return feature instanceof EReference reference && !reference.isMany() && element.eGet(feature) instanceof EObject value ? value : null;
    }

    private static boolean isTrue(EObject element, String featureName) {
        EStructuralFeature feature = element.eClass().getEStructuralFeature(featureName);
        return feature != null && Boolean.TRUE.equals(element.eGet(feature));
    }

    /** The element's ID attribute ({@code ArchElement.id}), or its URI fragment if the ID is not set. */
    public static String elementId(EObject element) {
        return ElementIds.of(element);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

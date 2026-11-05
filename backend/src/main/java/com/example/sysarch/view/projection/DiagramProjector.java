package com.example.sysarch.view.projection;

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
 * every such element in scope. This is how a BDD shows all blocks, including newly created ones. Any other
 * node style shows only elements reachable from the root through {@code autoExpand} (feature, depth).</li>
 * <li><b>Border nodes</b> sit on the node of their {@code affiliationFeature} value, or on the diagram frame
 * when that value is the root. Without such a parent they are not shown.</li>
 * <li><b>One node per element.</b> When several styles apply to an element, an element with
 * {@code isAbstract = true} gets the last one and every other element gets the first (BDD
 * {@code block}/{@code abstractBlock}).</li>
 * <li><b>Edges</b>: one per (source end value, target end value) of each element of the edge's type, drawn only
 * when both ends have nodes. Styles with the same type and the same ends are alternatives: the element gets the
 * one whose id matches its {@code kind} literal (IBD {@code assembly}/{@code delegation} by {@code Connection.kind}),
 * otherwise the first. Switching style keeps the edge id.
 * An end element without a node of its own is drawn to the node of its {@code typedBy}, and the edge label is
 * then read from that end element. For example, a BDD composition runs from the owner block to the part's type
 * and is labelled with the part's name.</li>
 * <li><b>Filters</b>: an active filter hides the root-type elements its expression rejects, together with their
 * border nodes and edges. The model is unchanged.</li>
 * </ol>
 */
public final class DiagramProjector {

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
                if (activeFilterIds.contains(filter.id())) {
                    this.activeFilters.add(FilterExpression.parse(filter.expression()));
                }
            }
            for (String id : activeFilterIds) {
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
            for (EObject element : this.scope) {
                this.projectNode(element, border, true);
            }
            this.elementsById.put(elementId(this.root), this.root);
            return new DiagramSnapshot(this.description.id(), elementId(this.root), this.orderedNodes, this.edges());
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
            int chosen = candidates.size() > 1 && isTrue(element, ABSTRACT_FEATURE) ? candidates.size() - 1 : 0;
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
                    nullToEmpty(LabelTemplates.render(style.labelTemplate(), element)), LabelTemplates.render(style.subLabelTemplate(), element),
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
            if (sourceNode == null) {
                sourceNode = this.nodes.get(single(source, TYPE_FEATURE));
                labelElement = source;
            }
            if (targetNode == null) {
                targetNode = this.nodes.get(single(target, TYPE_FEATURE));
                labelElement = target;
            }
            if (sourceNode == null || targetNode == null) {
                return Optional.empty();
            }
            String id = idPrefix + ":" + elementId(source) + "->" + elementId(target);
            this.elementsById.put(elementId(labelElement), labelElement);
            this.edgeEnds.putIfAbsent(id, new EdgeEnds(element, source, target));
            return Optional.of(new DiagramSnapshot.Edge(id, elementId(labelElement), style.id(), sourceNode.id(), targetNode.id(),
                    nullToEmpty(LabelTemplates.render(style.labelTemplate(), labelElement))));
        }

        private boolean hidden(EObject element) {
            return this.domainClass.isInstance(element) && this.activeFilters.stream().anyMatch(f -> !f.test(element));
        }

        private boolean isPeer(EClass type) {
            return type.isSuperTypeOf(this.domainClass) || this.domainClass.isSuperTypeOf(type);
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
    private static EdgeStyle chooseByKind(List<EdgeStyle> alternatives, EObject element) {
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

    private static EObject single(EObject element, String featureName) {
        if (element == null || featureName == null) {
            return null;
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
        String id = EcoreUtil.getID(element);
        if (id != null) {
            return id;
        }
        return element.eResource() != null ? element.eResource().getURIFragment(element) : EcoreUtil.getURI(element).fragment();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

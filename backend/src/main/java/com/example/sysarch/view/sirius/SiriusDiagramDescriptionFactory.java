package com.example.sysarch.view.sirius;

import com.example.sysarch.view.api.Arrow;
import com.example.sysarch.view.api.DiagramDescription;
import com.example.sysarch.view.api.EdgeStyle;
import com.example.sysarch.view.api.LabelPosition;
import com.example.sysarch.view.api.LineStyle;
import com.example.sysarch.view.api.NodeStyle;
import com.example.sysarch.view.api.Routing;
import com.example.sysarch.view.api.SynchronizationKind;
import com.example.sysarch.view.projection.DiagramProjector;
import com.example.sysarch.view.projection.DiagramProjector.EdgeEnds;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.view.projection.DiagramSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.components.collaborative.api.ChangeKind;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.diagrams.ArrowStyle;
import org.eclipse.sirius.components.diagrams.EdgeType;
import org.eclipse.sirius.components.diagrams.FreeFormLayoutStrategy;
import org.eclipse.sirius.components.diagrams.HeaderSeparatorDisplayMode;
import org.eclipse.sirius.components.diagrams.InsideLabelLocation;
import org.eclipse.sirius.components.diagrams.LabelOverflowStrategy;
import org.eclipse.sirius.components.diagrams.LabelTextAlign;
import org.eclipse.sirius.components.diagrams.LabelVisibility;
import org.eclipse.sirius.components.diagrams.NodeType;
import org.eclipse.sirius.components.diagrams.OutsideLabelLocation;
import org.eclipse.sirius.components.diagrams.RectangularNodeStyle;
import org.eclipse.sirius.components.diagrams.description.EdgeDescription;
import org.eclipse.sirius.components.diagrams.description.IDiagramElementDescription;
import org.eclipse.sirius.components.diagrams.description.InsideLabelDescription;
import org.eclipse.sirius.components.diagrams.description.LabelDescription;
import org.eclipse.sirius.components.diagrams.description.LabelStyleDescription;
import org.eclipse.sirius.components.diagrams.description.NodeDescription;
import org.eclipse.sirius.components.diagrams.description.OutsideLabelDescription;
import org.eclipse.sirius.components.diagrams.description.SynchronizationPolicy;
import org.eclipse.sirius.components.diagrams.renderer.DiagramRenderingCache;
import org.eclipse.sirius.components.representations.Element;
import org.eclipse.sirius.components.representations.Failure;
import org.eclipse.sirius.components.representations.IStatus;
import org.eclipse.sirius.components.representations.Success;
import org.eclipse.sirius.components.representations.VariableManager;

/**
 * Turns one of our VSM {@link DiagramDescription}s into a Sirius Web diagram description (T4).
 *
 * <p>Sirius renders and pushes the diagram with its own renderer, as spec §2.3 requires. What the diagram contains
 * comes entirely from {@link DiagramProjector}: every provider below reads the cached projection, and the adapter
 * makes no projection decisions of its own.
 *
 * <p>Mapping:
 * <ul>
 * <li>Each non-border node style becomes a top-level Sirius node description listing that style's projected
 * nodes. When no node style shows the root itself (IBD), a <b>frame</b> node for the root is added, and those
 * node descriptions become its children. Ports that sit on the root then have a border to sit on.</li>
 * <li>Each border style becomes a border node description of every node description whose element type its
 * {@code affiliationFeature} can point to.</li>
 * <li>Each edge style becomes an edge description. Every projected edge is its own semantic element
 * ({@link EdgeRef}), so Sirius draws exactly the projected source/target pair.</li>
 * <li>Sub-label and compartment items are rendered as extra lines of the node label (P0 simplification: no
 * separate list compartments yet).</li>
 * </ul>
 */
public final class SiriusDiagramDescriptionFactory {

    public static final String ID_PREFIX = "sysarch://diagram/";

    static final String FRAME_STYLE_ID = "frame";

    private static final String DEFAULT_COLOR = "#FFFFFF";

    private static final String DEFAULT_BORDER_COLOR = "#1A2B4A";

    private static final String TEXT_COLOR = "#1A2B4A";

    private final EPackage ePackage;

    private final IIdentityService identityService;

    private final ProjectionCache projections;

    private final DiagramFilterState filters;

    private final ToolFactory tools;

    public SiriusDiagramDescriptionFactory(EPackage ePackage, IIdentityService identityService, ProjectionCache projections, DiagramFilterState filters,
            ConstraintValidator validator) {
        this.ePackage = Objects.requireNonNull(ePackage);
        this.identityService = Objects.requireNonNull(identityService);
        this.projections = Objects.requireNonNull(projections);
        this.filters = Objects.requireNonNull(filters);
        this.tools = new ToolFactory(filters, Objects.requireNonNull(validator));
    }

    public static String diagramDescriptionId(DiagramDescription source) {
        return ID_PREFIX + source.id();
    }

    public org.eclipse.sirius.components.diagrams.description.DiagramDescription create(DiagramDescription source) {
        String diagramId = diagramDescriptionId(source);
        EClass domainClass = this.eClass(source.domainType());
        List<NodeStyle> plain = source.nodes().stream().filter(n -> !n.borderNode()).toList();
        List<NodeStyle> border = source.nodes().stream().filter(NodeStyle::borderNode).toList();
        SynchronizationPolicy policy = source.synchronizationKind() == SynchronizationKind.AUTO ? SynchronizationPolicy.SYNCHRONIZED
                : SynchronizationPolicy.UNSYNCHRONIZED;

        List<NodeDescription> allNodeDescriptions = new ArrayList<>();
        List<NodeDescription> plainDescriptions = new ArrayList<>();
        for (NodeStyle style : plain) {
            EClass type = this.eClass(style.appliesTo());
            List<NodeDescription> borders = this.borderDescriptions(source, diagramId, style.id(), type, border, policy, allNodeDescriptions);
            NodeDescription description = this.nodeDescription(source, diagramId + "/node/" + style.id(), style, policy,
                    vm -> this.elements(source, vm, style.id(), null), borders);
            plainDescriptions.add(description);
            allNodeDescriptions.add(description);
        }

        List<NodeDescription> topLevel = plainDescriptions;
        boolean rootIsShown = plain.stream().anyMatch(s -> isPeer(this.eClass(s.appliesTo()), domainClass));
        if (!rootIsShown) {
            List<NodeDescription> frameBorders = this.borderDescriptions(source, diagramId, FRAME_STYLE_ID, domainClass, border, policy, allNodeDescriptions);
            NodeDescription frame = this.frameDescription(source, diagramId, policy, plainDescriptions, frameBorders);
            allNodeDescriptions.add(frame);
            topLevel = List.of(frame);
        }

        List<IDiagramElementDescription> edgeEnds = List.copyOf(allNodeDescriptions);
        List<EdgeDescription> edgeDescriptions = source.edges().stream().map(style -> this.edgeDescription(source, diagramId, style, policy, edgeEnds)).toList();

        return org.eclipse.sirius.components.diagrams.description.DiagramDescription.newDiagramDescription(diagramId)
                .label(source.label())
                .targetObjectIdProvider(vm -> self(vm).map(this.identityService::getId).orElse(null))
                .canCreatePredicate(vm -> self(vm).filter(domainClass::isInstance).isPresent())
                .labelProvider(vm -> vm.get(org.eclipse.sirius.components.diagrams.description.DiagramDescription.LABEL, String.class).orElse(source.label()))
                .palettes(List.of(this.tools.palette(source, diagramId, allNodeDescriptions, description -> this.elementType(source, domainClass, description))))
                .nodeDescriptions(topLevel)
                .edgeDescriptions(edgeDescriptions)
                .dropHandler(vm -> new Failure("此图不支持拖放"))
                .iconURLsProvider(vm -> List.of())
                .build();
    }

    // ---- Nodes ----

    private NodeDescription nodeDescription(DiagramDescription source, String id, NodeStyle style, SynchronizationPolicy policy,
            Function<VariableManager, List<?>> semanticElements, List<NodeDescription> borders) {
        var builder = NodeDescription.newNodeDescription(id)
                .synchronizationPolicy(policy)
                .typeProvider(vm -> NodeType.NODE_RECTANGLE)
                .targetObjectIdProvider(vm -> self(vm).map(this.identityService::getId).orElse(null))
                .targetObjectKindProvider(vm -> self(vm).map(this::kind).orElse(""))
                .targetObjectLabelProvider(vm -> this.node(source, vm).map(DiagramSnapshot.Node::label).orElse(""))
                .semanticElementsProvider(semanticElements)
                .styleProvider(vm -> rectangle(style.color(), style.borderColor(), style.borderWidth()))
                .borderNodeDescriptions(borders)
                .deleteHandler(this::deleteElement)
                .labelEditHandler(this::rename)
                .initialChildBorderNodePositions(Map.of());
        if (style.width() != null) {
            builder.defaultWidthProvider(vm -> style.width());
        }
        if (style.height() != null) {
            builder.defaultHeightProvider(vm -> style.height());
        }
        LabelStyleDescription labelStyle = labelStyle(style.headerBold(), style.italic());
        if (style.labelPosition() == LabelPosition.OUTSIDE) {
            builder.outsideLabelDescriptions(List.of(OutsideLabelDescription.newOutsideLabelDescription(id + "/label")
                    .textProvider(vm -> this.labelText(source, vm))
                    .styleDescriptionProvider(vm -> labelStyle)
                    .outsideLabelLocation(OutsideLabelLocation.BOTTOM_MIDDLE)
                    .overflowStrategy(LabelOverflowStrategy.NONE)
                    .textAlign(LabelTextAlign.CENTER)
                    .build()));
        } else {
            builder.insideLabelDescription(insideLabel(id + "/label", vm -> this.labelText(source, vm), labelStyle, false));
        }
        return builder.build();
    }

    private NodeDescription frameDescription(DiagramDescription source, String diagramId, SynchronizationPolicy policy, List<NodeDescription> children,
            List<NodeDescription> borders) {
        String id = diagramId + "/node/" + FRAME_STYLE_ID;
        return NodeDescription.newNodeDescription(id)
                .synchronizationPolicy(policy)
                .typeProvider(vm -> NodeType.NODE_RECTANGLE)
                .targetObjectIdProvider(vm -> self(vm).map(this.identityService::getId).orElse(null))
                .targetObjectKindProvider(vm -> self(vm).map(this::kind).orElse(""))
                .targetObjectLabelProvider(vm -> self(vm).map(SiriusDiagramDescriptionFactory::name).orElse(""))
                .semanticElementsProvider(vm -> diagramRoot(vm).map(List::of).orElse(List.of()))
                .styleProvider(vm -> rectangle(DEFAULT_COLOR, DEFAULT_BORDER_COLOR, 2))
                .insideLabelDescription(insideLabel(id + "/label", vm -> self(vm).map(SiriusDiagramDescriptionFactory::name).orElse(""),
                        labelStyle(true, false), true))
                .childNodeDescriptions(children)
                .borderNodeDescriptions(borders)
                .deleteHandler(vm -> new Failure("不能在图中删除图的根元素"))
                .labelEditHandler(this::rename)
                .initialChildBorderNodePositions(Map.of())
                .build();
    }

    /** Border node descriptions for a parent of type {@code parentType}: one per border style that can sit on it. */
    private List<NodeDescription> borderDescriptions(DiagramDescription source, String diagramId, String parentStyleId, EClass parentType,
            List<NodeStyle> borderStyles, SynchronizationPolicy policy, List<NodeDescription> all) {
        List<NodeDescription> result = new ArrayList<>();
        for (NodeStyle style : borderStyles) {
            EClass styleType = this.eClass(style.appliesTo());
            if (styleType.getEStructuralFeature(style.affiliationFeature()) instanceof EReference affiliation
                    && affiliation.getEReferenceType().isSuperTypeOf(parentType)) {
                String id = diagramId + "/node/" + parentStyleId + "/border/" + style.id();
                NodeDescription description = this.nodeDescription(source, id, style, policy,
                        vm -> selfElement(vm).map(parent -> this.elements(source, vm, style.id(), DiagramProjector.elementId(parent))).orElse(List.of()), List.of());
                result.add(description);
                all.add(description);
            }
        }
        return result;
    }

    /** Projected elements of one style under one parent ({@code parentId} null: top level). */
    private List<EObject> elements(DiagramDescription source, VariableManager vm, String styleId, String parentId) {
        ProjectionCache.Projection projection = this.projection(source, vm);
        if (projection == null) {
            return List.of();
        }
        return projection.nodes(styleId, parentId).stream().map(node -> projection.element(node.elementId())).toList();
    }

    private Optional<DiagramSnapshot.Node> node(DiagramDescription source, VariableManager vm) {
        ProjectionCache.Projection projection = this.projection(source, vm);
        return projection == null ? Optional.empty() : selfElement(vm).map(element -> projection.node(DiagramProjector.elementId(element)));
    }

    /** Label, then sub-label, then one line per compartment item. */
    private String labelText(DiagramSnapshot.Node node) {
        StringBuilder text = new StringBuilder(node.label());
        if (node.subLabel() != null && !node.subLabel().isEmpty()) {
            text.append('\n').append(node.subLabel());
        }
        for (DiagramSnapshot.Compartment compartment : node.compartments()) {
            text.append("\n— ").append(compartment.label()).append(" —");
            compartment.items().forEach(item -> text.append('\n').append(item));
        }
        return text.toString();
    }

    private String labelText(DiagramDescription source, VariableManager vm) {
        return this.node(source, vm).map(this::labelText).orElse("");
    }

    // ---- Edges ----

    private EdgeDescription edgeDescription(DiagramDescription source, String diagramId, EdgeStyle style, SynchronizationPolicy policy,
            List<IDiagramElementDescription> ends) {
        String id = diagramId + "/edge/" + style.id();
        LabelStyleDescription labelStyle = labelStyle(false, false);
        org.eclipse.sirius.components.diagrams.EdgeStyle edgeStyle = org.eclipse.sirius.components.diagrams.EdgeStyle.newEdgeStyle()
                .size(1)
                .lineStyle(style.lineStyle() == LineStyle.DASHED ? org.eclipse.sirius.components.diagrams.LineStyle.Dash
                        : org.eclipse.sirius.components.diagrams.LineStyle.Solid)
                .sourceArrow(style.arrow() == Arrow.FILLED_DIAMOND ? ArrowStyle.FillDiamond : ArrowStyle.None)
                .targetArrow(style.arrow() == Arrow.HOLLOW_TRIANGLE ? ArrowStyle.InputClosedArrow : ArrowStyle.None)
                .color(Optional.ofNullable(style.color()).orElse(DEFAULT_BORDER_COLOR))
                .edgeType(style.routing() == Routing.STRAIGHT ? EdgeType.Oblique : EdgeType.Manhattan)
                .build();
        return EdgeDescription.newEdgeDescription(id)
                .synchronizationPolicy(policy)
                .targetObjectIdProvider(vm -> edgeRef(vm).map(ref -> this.identityService.getId(ref.element())).orElse(null))
                .targetObjectKindProvider(vm -> edgeRef(vm).map(ref -> this.kind(ref.element())).orElse(""))
                .targetObjectLabelProvider(vm -> edgeRef(vm).map(EdgeRef::label).orElse(""))
                .semanticElementsProvider(vm -> this.edgeRefs(source, vm, style.id()))
                .sourceDescriptions(ends)
                .targetDescriptions(ends)
                .sourceProvider(vm -> edgeRef(vm).map(ref -> nodeElements(vm, ref.source())).orElse(List.of()))
                .targetProvider(vm -> edgeRef(vm).map(ref -> nodeElements(vm, ref.target())).orElse(List.of()))
                .styleProvider(vm -> edgeStyle)
                .centerLabelDescription(LabelDescription.newLabelDescription(id + "/label")
                        .textProvider(vm -> edgeRef(vm).map(EdgeRef::label).orElse(""))
                        .styleDescriptionProvider(vm -> labelStyle)
                        .build())
                .deleteHandler(vm -> edgeRef(vm).map(ref -> this.deleteEdge(ref, style)).orElse(new Failure("未找到连线")))
                .build();
    }

    private List<EdgeRef> edgeRefs(DiagramDescription source, VariableManager vm, String styleId) {
        ProjectionCache.Projection projection = this.projection(source, vm);
        if (projection == null) {
            return List.of();
        }
        return projection.snapshot().edges(styleId).stream()
                .map(edge -> new EdgeRef(edge.id(), edge.label(), projection.element(edge.elementId()), projection.element(edge.sourceId()),
                        projection.element(edge.targetId()), projection.result().edgeEnds().get(edge.id())))
                .toList();
    }

    /** The rendered node that shows {@code element}. Each element has at most one node (projection rule 4). */
    private static List<Element> nodeElements(VariableManager vm, EObject element) {
        return vm.get(org.eclipse.sirius.components.diagrams.description.DiagramDescription.CACHE, DiagramRenderingCache.class)
                .map(cache -> cache.getElementsRepresenting(element).stream().limit(1).toList())
                .orElse(List.of());
    }

    // ---- Edits ----

    /** Deleting a node deletes its element from the model; the diagram then follows (spec §5.1). */
    private IStatus deleteElement(VariableManager vm) {
        Optional<EObject> element = self(vm).filter(EObject.class::isInstance).map(EObject.class::cast);
        if (element.isEmpty()) {
            return new Failure("未找到要删除的元素");
        }
        EcoreUtil.delete(element.get(), true);
        return semanticChange();
    }

    /**
     * Deleting an edge removes what it shows: for a {@code self -> feature} style the target from that feature
     * (a containment target, such as a Part, is deleted); otherwise the element the edge stands for (a Connection).
     */
    private IStatus deleteEdge(EdgeRef ref, EdgeStyle style) {
        EdgeEnds ends = ref.ends();
        if (ends == null) {
            return new Failure("未找到连线对应的模型元素");
        }
        if (EdgeStyle.SELF.equals(style.sourceEnd())) {
            EStructuralFeature feature = ends.domainElement().eClass().getEStructuralFeature(style.targetEnd());
            if (feature instanceof EReference reference && reference.isContainment()) {
                EcoreUtil.delete(ends.target(), true);
            } else if (feature != null && feature.isMany()) {
                ((Collection<?>) ends.domainElement().eGet(feature)).remove(ends.target());
            } else if (feature != null) {
                ends.domainElement().eUnset(feature);
            }
        } else {
            EcoreUtil.delete(ends.domainElement(), true);
        }
        return semanticChange();
    }

    private IStatus rename(VariableManager vm, String newName) {
        Optional<EObject> element = self(vm).filter(EObject.class::isInstance).map(EObject.class::cast);
        EStructuralFeature name = element.map(e -> e.eClass().getEStructuralFeature("name")).orElse(null);
        if (name == null) {
            return new Failure("该元素没有名称");
        }
        element.get().eSet(name, newName);
        return semanticChange();
    }

    // ---- Helpers ----

    /** Element type a node description shows: its style's type, or the root type for the frame. Ids end with the style id. */
    private EClass elementType(DiagramDescription source, EClass domainClass, NodeDescription description) {
        String styleId = description.getId().substring(description.getId().lastIndexOf('/') + 1);
        if (FRAME_STYLE_ID.equals(styleId)) {
            return domainClass;
        }
        return source.nodes().stream().filter(n -> n.id().equals(styleId)).findFirst().map(n -> this.eClass(n.appliesTo())).orElseThrow();
    }

    /** Sirius adds entries to a tool result's parameters (ToolDiagramExecutor), so the map must be mutable. */
    static Success semanticChange() {
        return new Success(ChangeKind.SEMANTIC_CHANGE, new HashMap<>());
    }

    private ProjectionCache.Projection projection(DiagramDescription source, VariableManager vm) {
        return diagramRoot(vm).map(root -> this.projections.get(source, root, this.filters.active(diagramId(vm)))).orElse(null);
    }

    /**
     * Sirius still requires a target object kind, but {@code IIdentityService.getKind} is deprecated for removal
     * (sirius-web issue 5114). Sirius 2025.8 itself still calls it, so we do too, in this one place.
     */
    @SuppressWarnings("removal")
    private String kind(Object element) {
        return this.identityService.getKind(element);
    }

    private EClass eClass(String domainType) {
        int separator = domainType.indexOf("::");
        if (separator > 0 && domainType.substring(0, separator).equals(this.ePackage.getNsPrefix())
                && this.ePackage.getEClassifier(domainType.substring(separator + 2)) instanceof EClass eClass) {
            return eClass;
        }
        throw new IllegalArgumentException("type " + domainType + " is not in metamodel " + this.ePackage.getNsURI());
    }

    private static boolean isPeer(EClass type, EClass domainClass) {
        return type.isSuperTypeOf(domainClass) || domainClass.isSuperTypeOf(type);
    }

    private static Optional<Object> self(VariableManager vm) {
        return vm.get(VariableManager.SELF, Object.class);
    }

    private static Optional<EObject> selfElement(VariableManager vm) {
        return vm.get(VariableManager.SELF, EObject.class);
    }

    private static Optional<EdgeRef> edgeRef(VariableManager vm) {
        return vm.get(VariableManager.SELF, EdgeRef.class);
    }

    /** The diagram's target object: {@code self} of the outermost variable manager. */
    static Optional<EObject> diagramRoot(VariableManager vm) {
        VariableManager top = vm;
        while (top.getParent() != null) {
            top = top.getParent();
        }
        return top.get(VariableManager.SELF, EObject.class);
    }

    /** Id of the Sirius diagram being rendered; null while it is first created. */
    static String diagramId(VariableManager vm) {
        VariableManager current = vm;
        while (current != null) {
            Optional<DiagramContext> context = current.get(DiagramContext.DIAGRAM_CONTEXT, DiagramContext.class);
            if (context.isPresent() && context.get().diagram() != null) {
                return context.get().diagram().getId();
            }
            current = current.getParent();
        }
        return null;
    }

    private static String name(Object element) {
        if (element instanceof EObject eObject && eObject.eClass().getEStructuralFeature("name") instanceof EStructuralFeature name) {
            Object value = eObject.eGet(name);
            return value == null ? "" : value.toString();
        }
        return "";
    }

    private static RectangularNodeStyle rectangle(String color, String borderColor, Integer borderWidth) {
        return RectangularNodeStyle.newRectangularNodeStyle()
                .background(Optional.ofNullable(color).orElse(DEFAULT_COLOR))
                .borderColor(Optional.ofNullable(borderColor).orElse(DEFAULT_BORDER_COLOR))
                .borderSize(Optional.ofNullable(borderWidth).orElse(1))
                .borderRadius(0)
                .borderStyle(org.eclipse.sirius.components.diagrams.LineStyle.Solid)
                .childrenLayoutStrategy(new FreeFormLayoutStrategy())
                .build();
    }

    private static InsideLabelDescription insideLabel(String id, Function<VariableManager, String> text, LabelStyleDescription style, boolean header) {
        return InsideLabelDescription.newInsideLabelDescription(id)
                .textProvider(text)
                .styleDescriptionProvider(vm -> style)
                .isHeaderProvider(vm -> header)
                .headerSeparatorDisplayModeProvider(vm -> HeaderSeparatorDisplayMode.NEVER)
                .insideLabelLocation(InsideLabelLocation.TOP_CENTER)
                .overflowStrategy(LabelOverflowStrategy.NONE)
                .textAlign(LabelTextAlign.CENTER)
                .build();
    }

    private static LabelStyleDescription labelStyle(boolean bold, boolean italic) {
        return LabelStyleDescription.newLabelStyleDescription()
                .colorProvider(vm -> TEXT_COLOR)
                .fontSizeProvider(vm -> 14)
                .boldProvider(vm -> bold)
                .italicProvider(vm -> italic)
                .underlineProvider(vm -> false)
                .strikeThroughProvider(vm -> false)
                .iconURLProvider(vm -> List.of())
                .backgroundProvider(vm -> "transparent")
                .borderColorProvider(vm -> "transparent")
                .borderSizeProvider(vm -> 0)
                .borderRadiusProvider(vm -> 0)
                .borderStyleProvider(vm -> org.eclipse.sirius.components.diagrams.LineStyle.Solid)
                .maxWidthProvider(vm -> null)
                .visibilityProvider(vm -> LabelVisibility.visible)
                .build();
    }

    /**
     * One projected edge as Sirius' semantic element.
     *
     * @param element the element the edge stands for (its targetObjectId), e.g. the Part of a composition
     * @param source element shown by the source node
     * @param target element shown by the target node
     * @param ends the unresolved ends, used to delete what the edge shows
     */
    record EdgeRef(String edgeId, String label, EObject element, EObject source, EObject target, EdgeEnds ends) {
    }
}

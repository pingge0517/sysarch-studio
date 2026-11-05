package com.example.sysarch.view.sirius;

import com.example.sysarch.model.ConnectionKind;
import com.example.sysarch.model.Port;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.validation.ValidationError;
import com.example.sysarch.view.api.DiagramDescription;
import com.example.sysarch.view.api.EdgeStyle;
import com.example.sysarch.view.api.Filter;
import com.example.sysarch.view.api.Tool;
import com.example.sysarch.view.api.ToolAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.components.diagrams.description.EdgeDescription;
import org.eclipse.sirius.components.diagrams.description.IDiagramElementDescription;
import org.eclipse.sirius.components.diagrams.description.NodeDescription;
import org.eclipse.sirius.components.diagrams.tools.ITool;
import org.eclipse.sirius.components.diagrams.tools.Palette;
import org.eclipse.sirius.components.diagrams.tools.SingleClickOnDiagramElementTool;
import org.eclipse.sirius.components.diagrams.tools.SingleClickOnTwoDiagramElementsCandidate;
import org.eclipse.sirius.components.diagrams.tools.SingleClickOnTwoDiagramElementsTool;
import org.eclipse.sirius.components.representations.Failure;
import org.eclipse.sirius.components.representations.IStatus;
import org.eclipse.sirius.components.representations.Message;
import org.eclipse.sirius.components.representations.MessageLevel;
import org.eclipse.sirius.components.representations.VariableManager;

/**
 * The palette of a VSM diagram: one Sirius tool per VSM {@code tools[]} entry, plus one toggle per filter (T4 step 4,
 * T6 step 2).
 *
 * <ul>
 * <li><b>create-child</b>: on every node (and the diagram background) whose element can contain the target class in
 * the tool's containment feature. The new element gets a default name, so it passes NameNotEmpty.</li>
 * <li><b>create-edge</b> (IBD {@code connect}): between nodes whose elements fit the edge style's ends. With
 * {@code validateOnDrop} the edge-drop constraints run first ({@link ConstraintValidator#validateEdgeDrop}); a
 * violation returns {@link Failure} with the messages <i>before anything is created</i>, so a rejected connection
 * is never stored (spec AC-5).</li>
 * <li><b>external-link</b> (bind a Polarion work item) is T7 and not offered yet.</li>
 * <li>Each filter becomes a background tool that switches it on or off for this diagram only.</li>
 * </ul>
 *
 * <p>Sirius looks tools up by id in the diagram description's palettes, so every tool is in the one palette built
 * here; {@link SysarchPaletteProvider} then shows the subset that applies to the clicked element.
 */
final class ToolFactory {

    private final DiagramFilterState filters;

    private final ConstraintValidator validator;

    ToolFactory(DiagramFilterState filters, ConstraintValidator validator) {
        this.filters = Objects.requireNonNull(filters);
        this.validator = Objects.requireNonNull(validator);
    }

    Palette palette(DiagramDescription source, String diagramId, List<NodeDescription> nodes, Function<NodeDescription, EClass> typeOf) {
        EClass rootType = typeOfRoot(source, nodes, typeOf);
        List<ITool> tools = new ArrayList<>();
        for (Tool tool : source.tools()) {
            String id = diagramId + "/tool/" + tool.id();
            if (tool.action() == ToolAction.CREATE_CHILD) {
                tools.add(this.createChildTool(id, tool, nodes, typeOf, rootType));
            } else if (tool.action() == ToolAction.CREATE_EDGE) {
                this.createEdgeTool(id, source, tool, nodes, typeOf).ifPresent(tools::add);
            }
        }
        for (Filter filter : source.filters()) {
            tools.add(SingleClickOnDiagramElementTool.newSingleClickOnDiagramElementTool(diagramId + "/tool/filter/" + filter.id())
                    .label(filter.label())
                    .iconURL(List.of())
                    .targetDescriptions(List.of())
                    .appliesToDiagramRoot(true)
                    .handler(vm -> this.toggleFilter(vm, filter.id()))
                    .build());
        }
        return Palette.newPalette(diagramId + "/palette").tools(tools).toolSections(List.of()).build();
    }

    private SingleClickOnDiagramElementTool createChildTool(String id, Tool tool, List<NodeDescription> nodes, Function<NodeDescription, EClass> typeOf,
            EClass rootType) {
        List<IDiagramElementDescription> targets = nodes.stream()
                .filter(node -> canContain(typeOf.apply(node), tool))
                .<IDiagramElementDescription>map(node -> node)
                .toList();
        return SingleClickOnDiagramElementTool.newSingleClickOnDiagramElementTool(id)
                .label(tool.label())
                .iconURL(List.of())
                .targetDescriptions(targets)
                .appliesToDiagramRoot(rootType != null && canContain(rootType, tool))
                .handler(vm -> this.createChild(vm, tool))
                .build();
    }

    private IStatus createChild(VariableManager vm, Tool tool) {
        Optional<EObject> container = vm.get(VariableManager.SELF, EObject.class);
        if (container.isEmpty()) {
            return new Failure("未找到要添加到的元素");
        }
        EStructuralFeature feature = container.get().eClass().getEStructuralFeature(tool.containmentFeature());
        EClass childType = childType(container.get(), tool);
        if (!(feature instanceof EReference reference) || !reference.isContainment() || childType == null) {
            return new Failure("「" + tool.label() + "」不能用于这个元素");
        }
        EObject child = EcoreUtil.create(childType);
        if (feature.isMany()) {
            @SuppressWarnings("unchecked")
            EList<EObject> values = (EList<EObject>) container.get().eGet(feature);
            setName(child, defaultName(childType, values.size() + 1));
            values.add(child);
        } else {
            setName(child, defaultName(childType, 1));
            container.get().eSet(feature, child);
        }
        return SiriusDiagramDescriptionFactory.semanticChange();
    }

    /**
     * The edge tool connects nodes whose elements fit the ends of the edge style for {@code tool.targetClass()}
     * (IBD: Connection source/target are Ports, so ports on parts and on the frame).
     */
    private Optional<SingleClickOnTwoDiagramElementsTool> createEdgeTool(String id, DiagramDescription source, Tool tool, List<NodeDescription> nodes,
            Function<NodeDescription, EClass> typeOf) {
        Optional<EdgeStyle> style = source.edges().stream().filter(e -> e.domainType().equals(tool.targetClass())).findFirst();
        Optional<EClass> edgeType = style.map(s -> eClassOf(nodes, typeOf, s.domainType()));
        if (style.isEmpty() || edgeType.isEmpty() || edgeType.get() == null) {
            return Optional.empty();
        }
        EClass sourceType = endType(edgeType.get(), style.get().sourceEnd());
        EClass targetType = endType(edgeType.get(), style.get().targetEnd());
        if (sourceType == null || targetType == null) {
            return Optional.empty();
        }
        List<IDiagramElementDescription> sources = nodes.stream().filter(n -> sourceType.isSuperTypeOf(typeOf.apply(n))).<IDiagramElementDescription>map(n -> n).toList();
        List<IDiagramElementDescription> targets = nodes.stream().filter(n -> targetType.isSuperTypeOf(typeOf.apply(n))).<IDiagramElementDescription>map(n -> n).toList();
        EClass connectionType = edgeType.get();
        EdgeStyle edgeStyle = style.get();
        return Optional.of(SingleClickOnTwoDiagramElementsTool.newSingleClickOnTwoDiagramElementsTool(id)
                .label(tool.label())
                .iconURL(List.of())
                .candidates(List.of(SingleClickOnTwoDiagramElementsCandidate.newSingleClickOnTwoDiagramElementsCandidate().sources(sources).targets(targets).build()))
                .handler(vm -> this.connect(vm, tool, connectionType, edgeStyle))
                .build());
    }

    private IStatus connect(VariableManager vm, Tool tool, EClass connectionType, EdgeStyle style) {
        Optional<EObject> from = vm.get(EdgeDescription.SEMANTIC_EDGE_SOURCE, EObject.class);
        Optional<EObject> to = vm.get(EdgeDescription.SEMANTIC_EDGE_TARGET, EObject.class);
        if (from.isEmpty() || to.isEmpty()) {
            return new Failure("未找到连接的两端");
        }
        if (tool.validateOnDrop() && from.get() instanceof Port sourcePort && to.get() instanceof Port targetPort) {
            List<ValidationError> errors = this.validator.validateEdgeDrop(sourcePort, targetPort, ConnectionKind.ASSEMBLY);
            if (!errors.isEmpty()) {
                // Nothing has been created yet: a rejected connection never reaches the model (spec AC-5).
                return new Failure(errors.stream().map(error -> new Message(error.message(), MessageLevel.ERROR)).toList());
            }
        }
        EObject connection = EcoreUtil.create(connectionType);
        connection.eSet(connectionType.getEStructuralFeature(style.sourceEnd()), from.get());
        connection.eSet(connectionType.getEStructuralFeature(style.targetEnd()), to.get());
        setName(connection, name(from.get()) + " → " + name(to.get()));
        copyAttribute(from.get(), connection, "interfaceType");
        // Connections have no container in the metamodel: they live at the top of the document of the ports.
        from.get().eResource().getContents().add(connection);
        return SiriusDiagramDescriptionFactory.semanticChange();
    }

    private IStatus toggleFilter(VariableManager vm, String filterId) {
        String diagramId = SiriusDiagramDescriptionFactory.diagramId(vm);
        if (diagramId == null) {
            return new Failure("未找到当前图");
        }
        this.filters.toggle(diagramId, filterId);
        return SiriusDiagramDescriptionFactory.semanticChange();
    }

    // ---- helpers ----

    private static boolean canContain(EClass containerType, Tool tool) {
        if (!(containerType.getEStructuralFeature(tool.containmentFeature()) instanceof EReference reference) || !reference.isContainment()) {
            return false;
        }
        EClass target = eClassByName(reference.getEReferenceType(), tool.targetClass());
        return target != null && reference.getEReferenceType().isSuperTypeOf(target);
    }

    private static EClass childType(EObject container, Tool tool) {
        EStructuralFeature feature = container.eClass().getEStructuralFeature(tool.containmentFeature());
        return feature instanceof EReference reference ? eClassByName(reference.getEReferenceType(), tool.targetClass()) : null;
    }

    /** {@code nsPrefix::Name} looked up in the package of {@code anyClass}. */
    private static EClass eClassByName(EClass anyClass, String domainType) {
        String name = domainType.substring(domainType.indexOf("::") + 2);
        return anyClass.getEPackage().getEClassifier(name) instanceof EClass eClass ? eClass : null;
    }

    private static EClass eClassOf(List<NodeDescription> nodes, Function<NodeDescription, EClass> typeOf, String domainType) {
        return nodes.stream().findFirst().map(typeOf).map(any -> eClassByName(any, domainType)).orElse(null);
    }

    private static EClass endType(EClass edgeType, String end) {
        return edgeType.getEStructuralFeature(end) instanceof EReference reference ? reference.getEReferenceType() : null;
    }

    private static EClass typeOfRoot(DiagramDescription source, List<NodeDescription> nodes, Function<NodeDescription, EClass> typeOf) {
        return eClassOf(nodes, typeOf, source.domainType());
    }

    /** e.g. {@code part3}: the class name in lower camel case and a running number. */
    private static String defaultName(EClass type, int number) {
        return Character.toLowerCase(type.getName().charAt(0)) + type.getName().substring(1) + number;
    }

    private static void setName(EObject element, String name) {
        EStructuralFeature feature = element.eClass().getEStructuralFeature("name");
        if (feature != null) {
            element.eSet(feature, name);
        }
    }

    private static String name(EObject element) {
        EStructuralFeature feature = element.eClass().getEStructuralFeature("name");
        Object value = feature == null ? null : element.eGet(feature);
        return value == null ? "?" : value.toString();
    }

    private static void copyAttribute(EObject from, EObject to, String attribute) {
        EStructuralFeature source = from.eClass().getEStructuralFeature(attribute);
        EStructuralFeature target = to.eClass().getEStructuralFeature(attribute);
        if (source != null && target != null && !source.isMany() && !target.isMany()) {
            to.eSet(target, from.eGet(source));
        }
    }
}

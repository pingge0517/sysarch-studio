package com.example.sysarch.view.sirius;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.view.generated.LaArchitectureDiagramDescriptionProvider;
import com.example.sysarch.view.generated.PaArchitectureDiagramDescriptionProvider;
import com.example.sysarch.view.generated.SaFunctionChainDiagramDescriptionProvider;
import com.example.sysarch.view.projection.DiagramProjector;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.diagrams.ArrowStyle;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.EdgeType;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.components.diagrams.RectangularNodeStyle;
import org.eclipse.sirius.components.diagrams.components.DiagramComponent;
import org.eclipse.sirius.components.diagrams.components.DiagramComponentProps;
import org.eclipse.sirius.components.diagrams.description.DiagramDescription;
import org.eclipse.sirius.components.diagrams.renderer.DiagramRenderer;
import org.eclipse.sirius.components.representations.Element;
import org.eclipse.sirius.components.representations.IStatus;
import org.eclipse.sirius.components.representations.Success;
import org.eclipse.sirius.components.representations.VariableManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * P1 T3 acceptance (spec P1 §5 T3, P1-AC-1): the SA, LA and PA views open; a view shows only its layer, always; what
 * a view creates belongs to its layer and so is visible there and nowhere else.
 */
class LayerViewsTest {

    private static final SysarchFactory FACTORY = SysarchFactory.eINSTANCE;

    private static final ConstraintValidator VALIDATOR = new ConstraintValidator();

    private static final IIdentityService IDS = new IIdentityService() {
        @Override
        public String getId(Object object) {
            return object instanceof EObject element ? DiagramProjector.elementId(element) : null;
        }

        @Override
        @SuppressWarnings("removal")
        public String getKind(Object object) {
            return object instanceof EObject element ? "sysarch::" + element.eClass().getName() : "";
        }
    };

    @AfterAll
    static void disposeOcl() {
        VALIDATOR.close();
    }

    private final SiriusDiagramDescriptionFactory factory = new SiriusDiagramDescriptionFactory(SysarchPackage.eINSTANCE, IDS, new ProjectionCache(),
            new DiagramFilterState(), VALIDATOR);

    private final DiagramDescription sa = this.factory.create(new SaFunctionChainDiagramDescriptionProvider().build());

    private final DiagramDescription la = this.factory.create(new LaArchitectureDiagramDescriptionProvider().build());

    private final DiagramDescription pa = this.factory.create(new PaArchitectureDiagramDescriptionProvider().build());

    private Resource model;

    private PartDefinition system;

    private PartDefinition controller;

    private PartDefinition ecu;

    @BeforeEach
    void fixture() {
        this.model = new ResourceImpl(URI.createURI("memory:/layers.xmi"));
        this.system = this.block("PD-SA", "整车功能", Layer.SYSTEM);
        this.controller = this.block("PD-LA", "控制器", Layer.LOGICAL);
        this.ecu = this.block("PD-PA", "ECU", Layer.PHYSICAL);
        this.model.getContents().addAll(List.of(this.system, this.controller, this.ecu));
    }

    // =========================================================== the three views open and show one layer each

    @Test
    void eachViewShowsOnlyItsLayer() {
        assertEquals(List.of("整车功能"), labels(this.render(this.sa, this.system)));
        assertEquals(List.of("控制器"), labels(this.render(this.la, this.controller)));
        assertEquals(List.of("ECU"), labels(this.render(this.pa, this.ecu)));
    }

    /** The layer filter is the view's scope: on without being switched on, and not offered as a toggle. */
    @Test
    void theLayerFilterIsAlwaysOnAndNotAToggle() {
        assertEquals(List.of("控制器"), labels(this.render(this.la, this.ecu)),
                "opened on a PHYSICAL block, LA still shows the logical blocks and hides the root itself");

        var background = new SysarchPaletteProvider().handle(null, null, this.la, this.la, null, null);
        List<String> tools = background.paletteEntries().stream()
                .map(org.eclipse.sirius.components.collaborative.diagrams.dto.ToolSection.class::cast)
                .flatMap(section -> section.tools().stream()).map(org.eclipse.sirius.components.collaborative.diagrams.dto.ITool::label).toList();
        assertEquals(List.of("新建逻辑组件"), tools, "no 仅逻辑层 toggle");
        assertFalse(SysarchPaletteProvider.allTools(this.la).anyMatch(t -> t.getId().contains("/tool/filter/")));
    }

    // =========================================================== P1-AC-1: creating in a layer view

    /** "在 LA 视图新建元素，其 layer 自动为 LOGICAL；切到 PA 视图后新元素不可见". */
    @Test
    void whatTheLaViewCreatesIsLogicalVisibleInLaAndNotInPa() {
        IStatus status = this.click(this.la, "/tool/createFunction", this.controller);

        assertTrue(status instanceof Success, String.valueOf(status));
        Part part = this.controller.getOwnedParts().get(0);
        assertEquals(Layer.LOGICAL, part.getLayer());
        PartDefinition type = part.getTypedBy();
        assertNotNull(type, "a layer view also creates the part's type, so the Part is not left untyped");
        assertEquals(Layer.LOGICAL, type.getLayer());
        assertEquals(part.getName(), type.getName());
        assertEquals("逻辑组件 1", part.getName(), "named after the tool (P1 trial T9-03), not part1");
        assertSame(this.model, type.eResource());

        assertEquals(Set.of("控制器", part.getName()), Set.copyOf(labels(this.render(this.la, this.controller))));
        assertEquals(List.of("ECU"), labels(this.render(this.pa, this.ecu)), "not visible in PA");
        assertEquals(List.of("整车功能"), labels(this.render(this.sa, this.system)));
        assertEquals(List.of(), VALIDATOR.validateAll(List.of(part, type)), "neither is marked red");
    }

    /** P1 trial T9-03: numbered among the elements of that class and layer, a used name skipped. */
    @Test
    void newElementsAreNumberedPerLayerAndNamesStayUnique() {
        this.click(this.la, "/tool/createFunction", this.controller);
        this.click(this.sa, "/tool/createFunction", this.system);
        this.controller.getOwnedParts().get(0).getTypedBy().setName("逻辑组件 2"); // a block someone renamed

        this.click(this.la, "/tool/createFunction", this.controller);

        Part second = this.controller.getOwnedParts().get(1);
        assertEquals("逻辑组件 3", second.getName(), "2 is the second logical part's number, but a block is already called that");
        assertEquals("逻辑组件 3", second.getTypedBy().getName());
        assertTrue(this.system.getOwnedParts().get(0).getName().endsWith(" 1"), "the SA part counts in its own layer");
    }

    @Test
    void eachViewCreatesItsOwnLayer() {
        this.click(this.sa, "/tool/createFunction", this.system);
        this.click(this.pa, "/tool/createFunction", this.ecu);

        assertEquals(Layer.SYSTEM, this.system.getOwnedParts().get(0).getTypedBy().getLayer());
        assertEquals(Layer.PHYSICAL, this.ecu.getOwnedParts().get(0).getTypedBy().getLayer());
        assertEquals(List.of("控制器"), labels(this.render(this.la, this.controller)), "neither shows up in LA");
    }

    /** The diagram background creates into the diagram root, with the same defaults. */
    @Test
    void creatingOnTheBackgroundUsesTheRoot() {
        IStatus status = this.click(this.la, "/tool/createFunction", this.controller);

        assertTrue(status instanceof Success);
        assertEquals(1, this.controller.getOwnedParts().size());
    }

    // =========================================================== the domain-controller demo

    /** LA shows the four logical blocks and the connections between the parts' types; PA the two ECUs; SA nothing. */
    @Test
    void theDemoInTheLayerViews() {
        Resource demo = new ResourceImpl(URI.createURI("memory:/demo.xmi"));
        demo.getContents().addAll(DomainControllerDemo.build());
        PartDefinition controllerDemo = (PartDefinition) demo.getContents().get(0);
        PartDefinition soc = (PartDefinition) demo.getContents().get(4);

        Diagram laDiagram = this.render(this.la, controllerDemo);
        assertEquals(Set.of("域控制器", "感知融合单元", "决策规划单元", "执行控制单元"), Set.copyOf(labels(laDiagram)));
        assertEquals(4, laDiagram.getEdges().size(), "the 4 connections, drawn between the blocks that type the ports' parts");
        assertTrue(laDiagram.getEdges().stream().allMatch(e -> e.getCenterLabel() != null && e.getCenterLabel().text().equals("CAN-FD")), "labelled {interfaceType.name}");
        assertEquals(Set.of("域控 SoC", "安全 MCU"), Set.copyOf(labels(this.render(this.pa, soc))));
        assertEquals(List.of(), labels(this.render(this.sa, controllerDemo)), "the demo has no SYSTEM blocks");
    }

    // =========================================================== P1 DSL styles in Sirius

    @Test
    void functionsAreRoundedAndExchangesHaveAnOpenArrow() {
        Diagram diagram = this.render(this.sa, this.system);
        Node function = diagram.getNodes().get(0);
        assertTrue(function.getDescriptionId().endsWith("/node/function"));
        assertEquals(SiriusDiagramDescriptionFactory.ROUNDED_RADIUS, ((RectangularNodeStyle) function.getStyle()).getBorderRadius());

        var exchange = this.sa.getEdgeDescriptions().get(0);
        assertTrue(exchange.getId().endsWith("/edge/exchange"));
        VariableManager vm = new VariableManager();
        var style = exchange.getStyleProvider().apply(vm);
        assertEquals(ArrowStyle.InputArrow, style.getTargetArrow());
        assertEquals(EdgeType.Oblique, style.getEdgeType(), "Sirius has no curves: spline is drawn straight");
    }

    // =========================================================== helpers

    private IStatus click(DiagramDescription description, String toolSuffix, EObject self) {
        VariableManager vm = new VariableManager();
        vm.put(VariableManager.SELF, self);
        return SysarchPaletteProvider.allTools(description)
                .filter(t -> t.getId().endsWith(toolSuffix))
                .map(org.eclipse.sirius.components.diagrams.tools.SingleClickOnDiagramElementTool.class::cast)
                .findFirst().orElseThrow().getHandler().apply(vm);
    }

    private Diagram render(DiagramDescription description, EObject root) {
        VariableManager vm = new VariableManager();
        vm.put(VariableManager.SELF, root);
        vm.put(DiagramContext.DIAGRAM_CONTEXT, null);
        DiagramComponentProps props = DiagramComponentProps.newDiagramComponentProps()
                .variableManager(vm)
                .diagramDescription(description)
                .allDiagramDescriptions(List.of(this.sa, this.la, this.pa))
                .operationValidator((operation, variables) -> { })
                .viewCreationRequests(List.of())
                .viewDeletionRequests(List.of())
                .previousDiagram(Optional.empty())
                .diagramEvents(List.of())
                .nodeAppearanceHandlers(List.of())
                .edgeAppearanceHandlers(List.of())
                .build();
        return new DiagramRenderer().render(new Element(DiagramComponent.class, props));
    }

    /** Block names (the first label line), in render order. */
    private static List<String> labels(Diagram diagram) {
        return allNodes(diagram.getNodes()).map(n -> n.getInsideLabel().getText().split("\n")[0]).toList();
    }

    private static Stream<Node> allNodes(List<Node> nodes) {
        return nodes.stream().flatMap(n -> Stream.concat(Stream.of(n), Stream.concat(allNodes(n.getChildNodes()), allNodes(n.getBorderNodes()))));
    }

    private PartDefinition block(String id, String name, Layer layer) {
        PartDefinition block = FACTORY.createPartDefinition();
        block.setId(id);
        block.setName(name);
        block.setLayer(layer);
        return block;
    }
}

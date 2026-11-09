package com.example.sysarch.view.sirius;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.model.Connection;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.view.generated.BddDiagramDescriptionProvider;
import com.example.sysarch.view.generated.IbdDiagramDescriptionProvider;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.view.projection.DiagramProjector;

import java.util.List;
import java.util.Map;
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
import org.eclipse.sirius.components.diagrams.Edge;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.components.diagrams.components.DiagramComponent;
import org.eclipse.sirius.components.diagrams.components.DiagramComponentProps;
import org.eclipse.sirius.components.diagrams.description.DiagramDescription;
import org.eclipse.sirius.components.diagrams.description.NodeDescription;
import org.eclipse.sirius.components.diagrams.renderer.DiagramRenderer;
import org.eclipse.sirius.components.representations.Element;
import org.eclipse.sirius.components.representations.IStatus;
import org.eclipse.sirius.components.representations.Success;
import org.eclipse.sirius.components.representations.VariableManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T4/T5 adapter: our VSM descriptions rendered by Sirius Web's own {@link DiagramRenderer}, the same way
 * {@code DiagramCreationService} does it, without Spring or a database.
 */
class SiriusRenderingTest {

    private static final SysarchFactory FACTORY = SysarchFactory.eINSTANCE;

    /** Ids as our projection sees them, so assertions can use the fixture's ids. */
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

    private final DiagramFilterState filters = new DiagramFilterState();

    /** OCL setup is expensive; one validator for the whole class. */
    private static final ConstraintValidator VALIDATOR = new ConstraintValidator();

    /**
     * Eclipse OCL keeps one environment per thread: a validator left open makes the next test class's validator a
     * second live instance on the same thread, and OCL's own assertions then fail in whichever class runs next.
     */
    @AfterAll
    static void disposeOcl() {
        VALIDATOR.close();
    }

    private final SiriusDiagramDescriptionFactory factory = new SiriusDiagramDescriptionFactory(SysarchPackage.eINSTANCE, IDS, new ProjectionCache(), this.filters,
            VALIDATOR);

    private final DiagramDescription bdd = this.factory.create(new BddDiagramDescriptionProvider().build());

    private final DiagramDescription ibd = this.factory.create(new IbdDiagramDescriptionProvider().build());

    private Resource model;

    private PartDefinition vehicle;

    private PartDefinition car;

    private PartDefinition engine;

    private int counter;

    @BeforeEach
    void fixture() {
        this.model = new ResourceImpl(URI.createURI("memory:/model.xmi"));
        this.vehicle = this.block("Vehicle", true);
        this.car = this.block("Car", false);
        this.engine = this.block("Engine", false);
        this.car.getSuperTypes().add(this.vehicle);
        this.model.getContents().addAll(List.of(this.vehicle, this.car, this.engine));
    }

    @Test
    void bddShowsEveryBlockWithLabelsAndEdges() {
        Port can = this.port("can0", "CAN");
        this.car.getOwnedPorts().add(can);
        Part enginePart = this.part("engine", this.engine);
        this.car.getOwnedParts().add(enginePart);

        Diagram diagram = this.render(this.bdd, this.car, Optional.empty());

        assertEquals(this.car.getId(), diagram.getTargetObjectId());
        // Sirius renders node descriptions in declaration order (block before abstractBlock); the order carries no meaning.
        assertEquals(Set.of("Vehicle", "Car\nLOGICAL\n— ports —\ncan0 : CAN", "Engine\nLOGICAL\n— ports —"), Set.copyOf(labels(diagram.getNodes())));
        assertEquals(3, diagram.getNodes().size());
        Node vehicleNode = this.nodeFor(diagram, this.vehicle);
        assertTrue(vehicleNode.getDescriptionId().endsWith("/node/abstractBlock"), "abstract block gets abstractBlock");
        Node carNode = this.nodeFor(diagram, this.car);
        Node engineNode = this.nodeFor(diagram, this.engine);

        Edge generalization = this.edge(diagram, "generalization");
        assertEquals(carNode.getId(), generalization.getSourceId());
        assertEquals(vehicleNode.getId(), generalization.getTargetId());
        assertEquals(ArrowStyle.InputClosedArrow, generalization.getStyle().getTargetArrow());

        Edge composition = this.edge(diagram, "composition");
        assertEquals(carNode.getId(), composition.getSourceId());
        assertEquals(engineNode.getId(), composition.getTargetId());
        assertEquals(ArrowStyle.FillDiamond, composition.getStyle().getSourceArrow());
        assertEquals("engine", composition.getCenterLabel().text());
        assertEquals(enginePart.getId(), composition.getTargetObjectId(), "the edge stands for the Part");
    }

    /** AC-4 at the renderer level: a block added to the model appears on the next render, nothing else to do. */
    @Test
    void newBlockAppearsOnTheNextRender() {
        Diagram before = this.render(this.bdd, this.car, Optional.empty());
        PartDefinition bus = this.block("Bus", false);
        this.model.getContents().add(bus);

        Diagram after = this.render(this.bdd, this.car, Optional.of(before));

        assertEquals(before.getNodes().size() + 1, after.getNodes().size());
        assertEquals("Bus\nLOGICAL\n— ports —", this.nodeFor(after, bus).getInsideLabel().getText());
        assertEquals(this.nodeFor(before, this.car).getId(), this.nodeFor(after, this.car).getId(), "existing nodes keep their id, so their layout");
    }

    @Test
    void hideAbstractFilterIsPerDiagramAndLeavesTheModelAlone() {
        Diagram shown = this.render(this.bdd, this.car, Optional.empty());
        assertTrue(this.filters.toggle(shown.getId(), "hideAbstract"));

        Diagram filtered = this.render(this.bdd, this.car, Optional.of(shown));

        assertEquals(List.of("Car\nLOGICAL\n— ports —", "Engine\nLOGICAL\n— ports —"), labels(filtered.getNodes()));
        assertTrue(filtered.getEdges().isEmpty(), "the generalization to the hidden block goes too");
        assertTrue(this.model.getContents().contains(this.vehicle));
    }

    @Test
    void ibdShowsTheRootAsAFrameWithPartsInsideAndPortsOnBorders() {
        Port external = this.port("ext", "CAN");
        this.vehicle.getOwnedPorts().add(external);
        Part ctrl = this.part("ctrl", this.engine);
        Port ctrlPort = this.port("can0", "CAN");
        ctrl.getPorts().add(ctrlPort);
        this.vehicle.getOwnedParts().add(ctrl);
        Connection bus = FACTORY.createConnection();
        bus.setId("CO-1");
        bus.setName("bus");
        bus.setSource(external);
        bus.setTarget(ctrlPort);
        bus.setInterfaceType(com.example.sysarch.TestInterfaces.named("CAN"));
        this.model.getContents().add(bus);

        Diagram diagram = this.render(this.ibd, this.vehicle, Optional.empty());

        assertEquals(1, diagram.getNodes().size(), "only the frame at top level");
        Node frame = diagram.getNodes().get(0);
        assertEquals(this.vehicle.getId(), frame.getTargetObjectId());
        assertEquals("Vehicle", frame.getInsideLabel().getText());
        assertEquals(List.of(external.getId()), frame.getBorderNodes().stream().map(Node::getTargetObjectId).toList());
        Node part = frame.getChildNodes().get(0);
        assertEquals("ctrl\nEngine [1]", part.getInsideLabel().getText());
        Node partPort = part.getBorderNodes().get(0);
        assertEquals(ctrlPort.getId(), partPort.getTargetObjectId());
        assertEquals("can0", partPort.getOutsideLabels().get(0).text());

        Edge assembly = this.edge(diagram, "assembly");
        assertEquals(frame.getBorderNodes().get(0).getId(), assembly.getSourceId());
        assertEquals(partPort.getId(), assembly.getTargetId());
        assertEquals("CAN", assembly.getCenterLabel().text());
    }

    @Test
    void deletingANodeDeletesTheElement() {
        NodeDescription block = this.nodeDescription(this.bdd, "/node/block");
        VariableManager vm = new VariableManager();
        vm.put(VariableManager.SELF, this.engine);

        IStatus status = block.getDeleteHandler().apply(vm);

        assertTrue(status instanceof Success);
        assertFalse(this.model.getContents().contains(this.engine));
    }

    @Test
    void deletingAGeneralizationEdgeRemovesOnlyTheSupertypeReference() {
        Diagram diagram = this.render(this.bdd, this.car, Optional.empty());
        var description = this.bdd.getEdgeDescriptions().stream().filter(e -> e.getId().endsWith("/edge/generalization")).findFirst().orElseThrow();
        var refs = description.getSemanticElementsProvider().apply(this.rootVariables(this.car, Optional.of(diagram)));
        VariableManager vm = new VariableManager();
        vm.put(VariableManager.SELF, refs.get(0));

        assertTrue(description.getDeleteHandler().apply(vm) instanceof Success);

        assertTrue(this.car.getSuperTypes().isEmpty());
        assertTrue(this.model.getContents().contains(this.vehicle), "the supertype itself stays");
    }

    // ---- Tools (T4 step 4, T6 step 2) ----

    @Test
    void palettesOfferTheYamlToolsWhereTheyApply() {
        var palettes = new SysarchPaletteProvider();
        var bddBlock = this.nodeDescription(this.bdd, "/node/block");
        assertEquals(List.of("新建部件", "新建端口"), toolLabels(palettes.handle(null, null, this.bdd, bddBlock, null, null)));
        assertEquals(List.of("新建部件", "新建端口", "隐藏抽象块"), toolLabels(palettes.handle(null, null, this.bdd, this.bdd, null, null)),
                "diagram background: create into the root, filter toggle");

        var ibdFrame = this.nodeDescription(this.ibd, "/node/frame");
        assertEquals(List.of("新建部件"), toolLabels(palettes.handle(null, null, this.ibd, ibdFrame, null, null)));
        var portOnPart = ibdFrame.getChildNodeDescriptions().get(0).getBorderNodeDescriptions().get(0);
        assertEquals(List.of("建立连接"), toolLabels(palettes.handle(null, null, this.ibd, portOnPart, null, null)), "connect starts from a port");
        var portOnFrame = ibdFrame.getBorderNodeDescriptions().get(0);
        assertEquals(List.of("建立连接"), toolLabels(palettes.handle(null, null, this.ibd, portOnFrame, null, null)), "and from a port on the frame");
    }

    /**
     * Sirius 2025.8 draws connection handles on a node only for edge tools in {@code paletteEntries} (directly or in a
     * tool section), never for {@code quickAccessTools}, and draws quick access tools as icons only. So our tools are in
     * one labelled section: visible by name, and connectable by dragging (found in the browser check of AC-5).
     */
    @Test
    void toolsAreInALabelledSectionNotQuickAccess() {
        var portOnPart = this.nodeDescription(this.ibd, "/node/frame").getChildNodeDescriptions().get(0).getBorderNodeDescriptions().get(0);

        var palette = new SysarchPaletteProvider().handle(null, null, this.ibd, portOnPart, null, null);

        assertEquals(List.of(), palette.quickAccessTools());
        assertEquals(1, palette.paletteEntries().size());
        var section = (org.eclipse.sirius.components.collaborative.diagrams.dto.ToolSection) palette.paletteEntries().get(0);
        assertEquals("建模工具", section.label());
        assertTrue(section.tools().get(0) instanceof org.eclipse.sirius.components.collaborative.diagrams.dto.SingleClickOnTwoDiagramElementsTool);
    }

    @Test
    void createPartAddsANamedPartToTheClickedBlock() {
        VariableManager vm = new VariableManager();
        vm.put(VariableManager.SELF, this.car);

        IStatus status = this.clickTool(this.bdd, "/tool/createPart").getHandler().apply(vm);

        assertTrue(status instanceof Success, String.valueOf(status));
        assertEquals(1, this.car.getOwnedParts().size());
        assertEquals("part1", this.car.getOwnedParts().get(0).getName(), "a default name keeps NameNotEmpty satisfied");
    }

    /**
     * AC-5: connecting ports of different interface types is rejected before anything is created, with the reason as
     * an error message, and the two ports are drawn red. The status is a {@link Success} only so that Sirius refreshes
     * the diagram (a Failure would not); it carries no model change.
     */
    @Test
    void connectRejectsIncompatiblePortsWithoutTouchingTheModel() {
        Port can = this.port("can0", "CAN");
        can.setDirection(com.example.sysarch.model.PortDirection.OUT);
        Port eth = this.port("eth0", "ETH");
        Port other = this.port("other", "CAN");
        this.vehicle.getOwnedPorts().addAll(List.of(can, eth, other));
        int before = this.model.getContents().size();

        IStatus status = this.connect(can, eth);

        assertTrue(status instanceof Success success && success.getMessages().size() == 1
                && success.getMessages().get(0).level() == org.eclipse.sirius.components.representations.MessageLevel.ERROR
                && success.getMessages().get(0).body().startsWith("端口接口不可赋值：「CAN」与「ETH」"), String.valueOf(status));
        assertEquals(before, this.model.getContents().size(), "no Connection was stored");
        assertEquals(Map.of("can0", "#D32F2F", "eth0", "#D32F2F", "other", "#1A2B4A"), this.portBorders());
    }

    /** "两端端口描红" lasts until the next edit of the model, whatever it is. */
    @Test
    void theRedPortsClearOnTheNextEdit() {
        Port can = this.port("can0", "CAN");
        can.setDirection(com.example.sysarch.model.PortDirection.OUT);
        Port eth = this.port("eth0", "ETH");
        this.vehicle.getOwnedPorts().addAll(List.of(can, eth));
        this.connect(can, eth);
        assertEquals("#D32F2F", this.portBorders().get("can0"));

        eth.setInterfaceType(com.example.sysarch.TestInterfaces.named("CAN"));

        assertEquals(Map.of("can0", "#1A2B4A", "eth0", "#1A2B4A"), this.portBorders());
    }

    /** Border colour of each port on the IBD frame, by port name. */
    private Map<String, String> portBorders() {
        Diagram diagram = this.render(this.ibd, this.vehicle, Optional.empty());
        Map<String, String> borders = new java.util.TreeMap<>();
        for (Node port : diagram.getNodes().get(0).getBorderNodes()) {
            borders.put(port.getOutsideLabels().get(0).text(), ((org.eclipse.sirius.components.diagrams.RectangularNodeStyle) port.getStyle()).getBorderColor());
        }
        return borders;
    }

    @Test
    void connectCreatesAConnectionForCompatiblePorts() {
        Port out = this.port("out", "CAN");
        out.setDirection(com.example.sysarch.model.PortDirection.OUT);
        Port in = this.port("in", "CAN");
        in.setDirection(com.example.sysarch.model.PortDirection.IN);
        this.vehicle.getOwnedPorts().addAll(List.of(out, in));

        assertTrue(this.connect(out, in) instanceof Success);

        Connection connection = this.model.getContents().stream().filter(Connection.class::isInstance).map(Connection.class::cast).findFirst().orElseThrow();
        assertEquals(out, connection.getSource());
        assertEquals(in, connection.getTarget());
        assertEquals("out → in", connection.getName());
        assertEquals("CAN", connection.getInterfaceType().getName());
    }

    @Test
    void connectorToolsAreOfferedWhenDraggingFromPortToPort() {
        Port a = this.port("a", "CAN");
        Port b = this.port("b", "CAN");
        this.vehicle.getOwnedPorts().addAll(List.of(a, b));
        Diagram diagram = this.render(this.ibd, this.vehicle, Optional.empty());
        List<Node> ports = diagram.getNodes().get(0).getBorderNodes();
        var search = new org.eclipse.sirius.components.core.api.IRepresentationDescriptionSearchService() {
            @Override
            public Optional<org.eclipse.sirius.components.representations.IRepresentationDescription> findById(
                    org.eclipse.sirius.components.core.api.IEditingContext editingContext, String id) {
                return Optional.of(SiriusRenderingTest.this.ibd);
            }

            @Override
            public Map<String, org.eclipse.sirius.components.representations.IRepresentationDescription> findAll(
                    org.eclipse.sirius.components.core.api.IEditingContext editingContext) {
                return Map.of();
            }
        };

        var tools = new SysarchPaletteProvider.ConnectorTools(search).getConnectorTools(ports.get(0), ports.get(1), diagram, null);

        assertEquals(List.of("建立连接"), tools.stream().map(org.eclipse.sirius.components.diagrams.tools.ITool::getLabel).toList());
    }

    @Test
    void filterToolTogglesHideAbstractForThatDiagramOnly() {
        Diagram diagram = this.render(this.bdd, this.car, Optional.empty());
        var tool = this.clickTool(this.bdd, "/tool/filter/hideAbstract");

        assertTrue(tool.getHandler().apply(this.rootVariables(this.car, Optional.of(diagram))) instanceof Success);
        assertEquals(Set.of("hideAbstract"), this.filters.active(diagram.getId()));
        assertEquals(Set.of(), this.filters.active("another-diagram"));

        tool.getHandler().apply(this.rootVariables(this.car, Optional.of(diagram)));
        assertEquals(Set.of(), this.filters.active(diagram.getId()), "second click switches it off");
    }

    private IStatus connect(Port source, Port target) {
        var tool = SysarchPaletteProvider.allTools(this.ibd)
                .filter(org.eclipse.sirius.components.diagrams.tools.SingleClickOnTwoDiagramElementsTool.class::isInstance)
                .map(org.eclipse.sirius.components.diagrams.tools.SingleClickOnTwoDiagramElementsTool.class::cast)
                .findFirst().orElseThrow();
        VariableManager vm = new VariableManager();
        vm.put(org.eclipse.sirius.components.diagrams.description.EdgeDescription.SEMANTIC_EDGE_SOURCE, source);
        vm.put(org.eclipse.sirius.components.diagrams.description.EdgeDescription.SEMANTIC_EDGE_TARGET, target);
        return tool.getHandler().apply(vm);
    }

    private org.eclipse.sirius.components.diagrams.tools.SingleClickOnDiagramElementTool clickTool(DiagramDescription description, String suffix) {
        return SysarchPaletteProvider.allTools(description)
                .filter(t -> t.getId().endsWith(suffix))
                .map(org.eclipse.sirius.components.diagrams.tools.SingleClickOnDiagramElementTool.class::cast)
                .findFirst().orElseThrow();
    }

    private static List<String> toolLabels(org.eclipse.sirius.components.collaborative.diagrams.dto.Palette palette) {
        return palette.paletteEntries().stream()
                .filter(org.eclipse.sirius.components.collaborative.diagrams.dto.ToolSection.class::isInstance)
                .map(org.eclipse.sirius.components.collaborative.diagrams.dto.ToolSection.class::cast)
                .flatMap(section -> section.tools().stream())
                .map(org.eclipse.sirius.components.collaborative.diagrams.dto.ITool::label).toList();
    }

    // ---- helpers ----

    private Diagram render(DiagramDescription description, EObject root, Optional<Diagram> previous) {
        DiagramComponentProps props = DiagramComponentProps.newDiagramComponentProps()
                .variableManager(this.rootVariables(root, previous))
                .diagramDescription(description)
                .allDiagramDescriptions(List.of(this.bdd, this.ibd))
                .operationValidator((operation, variables) -> { })
                .viewCreationRequests(List.of())
                .viewDeletionRequests(List.of())
                .previousDiagram(previous)
                .diagramEvents(List.of())
                .nodeAppearanceHandlers(List.of())
                .edgeAppearanceHandlers(List.of())
                .build();
        return new DiagramRenderer().render(new Element(DiagramComponent.class, props));
    }

    private VariableManager rootVariables(EObject root, Optional<Diagram> previous) {
        VariableManager vm = new VariableManager();
        vm.put(VariableManager.SELF, root);
        vm.put(DiagramContext.DIAGRAM_CONTEXT, previous.map(DiagramContext::new).orElse(null));
        return vm;
    }

    private Node nodeFor(Diagram diagram, EObject element) {
        String id = DiagramProjector.elementId(element);
        return allNodes(diagram.getNodes()).filter(n -> id.equals(n.getTargetObjectId())).findFirst().orElseThrow();
    }

    private Edge edge(Diagram diagram, String styleId) {
        List<Edge> edges = diagram.getEdges().stream().filter(e -> e.getDescriptionId().endsWith("/edge/" + styleId)).toList();
        assertEquals(1, edges.size(), "edges of style " + styleId);
        return edges.get(0);
    }

    private NodeDescription nodeDescription(DiagramDescription description, String suffix) {
        return description.getNodeDescriptions().stream().filter(n -> n.getId().endsWith(suffix)).findFirst().orElseThrow();
    }

    private static Stream<Node> allNodes(List<Node> nodes) {
        return nodes.stream().flatMap(n -> Stream.concat(Stream.of(n), Stream.concat(allNodes(n.getChildNodes()), allNodes(n.getBorderNodes()))));
    }

    private static List<String> labels(List<Node> nodes) {
        return nodes.stream().map(n -> n.getInsideLabel().getText()).toList();
    }

    private PartDefinition block(String name, boolean isAbstract) {
        PartDefinition block = FACTORY.createPartDefinition();
        block.setId("PD-" + ++this.counter);
        block.setName(name);
        block.setIsAbstract(isAbstract);
        return block;
    }

    private Part part(String name, PartDefinition type) {
        Part part = FACTORY.createPart();
        part.setId("PA-" + ++this.counter);
        part.setName(name);
        part.setTypedBy(type);
        return part;
    }

    private Port port(String name, String interfaceType) {
        Port port = FACTORY.createPort();
        port.setId("PO-" + ++this.counter);
        port.setName(name);
        port.setInterfaceType(com.example.sysarch.TestInterfaces.named(interfaceType));
        return port;
    }
}

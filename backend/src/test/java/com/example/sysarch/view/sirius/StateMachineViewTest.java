package com.example.sysarch.view.sirius;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.behavior.BehaviorChecks;
import com.example.sysarch.model.State;
import com.example.sysarch.model.StateMachine;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.model.Transition;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.view.generated.StateMachineDiagramDescriptionProvider;
import com.example.sysarch.view.projection.DiagramProjector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.Edge;
import org.eclipse.sirius.components.diagrams.ImageNodeStyle;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.components.diagrams.NodeType;
import org.eclipse.sirius.components.diagrams.RectangularNodeStyle;
import org.eclipse.sirius.components.diagrams.components.DiagramComponent;
import org.eclipse.sirius.components.diagrams.components.DiagramComponentProps;
import org.eclipse.sirius.components.diagrams.description.DiagramDescription;
import org.eclipse.sirius.components.diagrams.description.EdgeDescription;
import org.eclipse.sirius.components.diagrams.renderer.DiagramRenderer;
import org.eclipse.sirius.components.diagrams.tools.SingleClickOnDiagramElementTool;
import org.eclipse.sirius.components.diagrams.tools.SingleClickOnTwoDiagramElementsTool;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.components.representations.Element;
import org.eclipse.sirius.components.representations.Success;
import org.eclipse.sirius.components.representations.VariableManager;
import org.eclipse.sirius.web.application.diagram.EllipseNodeStyle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * P3 T5: the state-machine view (views/state-machine.vsm.yaml, from the shared behaviour template). Acceptance: "5 状态
 * 7 迁移的图可完整渲染，标签无空段" — initial pseudo-state a filled circle, final state a ring around a disc, states rounded,
 * transition labels {@code trigger[guard]/effect} without empty brackets or slashes. Also P3-AC-5 on the drawn machine:
 * a second initial state and an unreachable state are R7/R8.
 */
class StateMachineViewTest {

    private static final SysarchFactory F = SysarchFactory.eINSTANCE;

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
    static void dispose() {
        VALIDATOR.close();
    }

    private final SiriusDiagramDescriptionFactory factory = new SiriusDiagramDescriptionFactory(SysarchPackage.eINSTANCE, IDS, new ProjectionCache(),
            new DiagramFilterState(), VALIDATOR);

    private final DiagramDescription view = this.factory.create(new StateMachineDiagramDescriptionProvider().build());

    private Resource model;

    private StateMachine door;

    @BeforeEach
    void fixture() {
        ResourceSet set = new ResourceSetImpl();
        this.model = new ResourceImpl(URI.createURI("memory:/door.json"));
        set.getResources().add(this.model);
        // 5 states (initial, 3 states, final), 7 transitions with every combination of label segments
        this.door = F.createStateMachine();
        this.door.setId("SM-1");
        this.door.setName("车门");
        State start = state("S-0", "开始", true, false);
        State closed = state("S-1", "关闭", false, false);
        closed.setEntryBehavior("上锁");
        closed.setDoBehavior("监测");
        State opening = state("S-2", "开启中", false, false);
        State open = state("S-3", "已开启", false, false);
        open.setExitBehavior("鸣笛");
        State end = state("S-4", "结束", false, true);
        this.door.getStates().addAll(List.of(start, closed, opening, open, end));
        this.door.getTransitions().addAll(List.of(
                transition("T-1", start, closed, null, null, null),                // completion: no label
                transition("T-2", closed, opening, "开门", null, null),             // trigger only
                transition("T-3", opening, open, "到位", "速度 = 0", null),          // trigger[guard]
                transition("T-4", open, closed, "关门", null, "解锁"),               // trigger/effect
                transition("T-5", opening, closed, "超时", "障碍", "报警"),           // all three
                transition("T-6", closed, closed, null, null, "自检"),              // effect only, a self-transition
                transition("T-7", closed, end, "停用", "", "")));                 // empty strings count as empty
        this.model.getContents().add(this.door);
    }

    @Test
    void fiveStatesSevenTransitionsAreDrawnWithoutEmptySegments() {
        Diagram diagram = this.render();
        assertEquals(1, diagram.getNodes().size(), "the machine is the frame");
        assertEquals("车门", diagram.getNodes().get(0).getInsideLabel().getText());
        assertEquals(5, states(diagram).size(), "every state, inside the frame");
        Node initial = this.node(diagram, "S-0");
        assertEquals(SiriusDiagramDescriptionFactory.ELLIPSE_NODE_TYPE, initial.getType(), "initial pseudo-state: a disc");
        assertTrue(initial.getStyle() instanceof EllipseNodeStyle ellipse && ellipse.getBackground().equals("#1A2B4A"), "filled");
        Node fin = this.node(diagram, "S-4");
        assertEquals(NodeType.NODE_IMAGE, fin.getType(), "final state: an image node");
        assertEquals(SiriusDiagramDescriptionFactory.DOUBLE_CIRCLE_IMAGE, ((ImageNodeStyle) fin.getStyle()).getImageURL());
        Node closed = this.node(diagram, "S-1");
        assertEquals(NodeType.NODE_RECTANGLE, closed.getType());
        assertEquals(SiriusDiagramDescriptionFactory.ROUNDED_RADIUS, ((RectangularNodeStyle) closed.getStyle()).getBorderRadius(), "states rounded");
        assertEquals("关闭\nentry/上锁\ndo/监测", closed.getInsideLabel().getText(), "entry/do/exit, empty ones left out");
        assertEquals("已开启\nexit/鸣笛", this.node(diagram, "S-3").getInsideLabel().getText());
        assertEquals("开启中", this.node(diagram, "S-2").getInsideLabel().getText());

        assertEquals(7, diagram.getEdges().size(), "every transition, the self-transition included");
        assertEquals(List.of("", "开门", "到位[速度 = 0]", "关门/解锁", "超时[障碍]/报警", "/自检", "停用"), diagram.getEdges().stream().map(StateMachineViewTest::label).toList());
        for (Edge edge : diagram.getEdges()) {
            String label = label(edge);
            assertFalse(label.contains("[]") || label.endsWith("/") || label.startsWith("[]"), "no empty segment: " + label);
        }
    }

    @Test
    void theDiagramSurvivesSaveAndReload() throws Exception {
        Resource reloaded = this.saveAndReload();
        this.model = reloaded;
        this.door = (StateMachine) reloaded.getContents().get(0);
        Diagram diagram = this.render();
        assertEquals(5, states(diagram).size());
        assertEquals(7, diagram.getEdges().size());
        assertEquals(SiriusDiagramDescriptionFactory.ELLIPSE_NODE_TYPE, this.node(diagram, "S-0").getType());
    }

    @Test
    void theToolsCreateStatesAndTransitionsInTheMachine() {
        VariableManager vm = new VariableManager();
        vm.put(VariableManager.SELF, this.door);
        assertTrue(this.tool("createInitial").getHandler().apply(vm) instanceof Success);
        State created = this.door.getStates().get(this.door.getStates().size() - 1);
        assertTrue(created.isIsInitial() && !created.isIsFinal(), "defaults: isInitial = true");
        assertTrue(this.tool("createFinal").getHandler().apply(vm) instanceof Success);
        assertTrue(this.door.getStates().get(this.door.getStates().size() - 1).isIsFinal());
        assertTrue(this.tool("createState").getHandler().apply(vm) instanceof Success);
        assertEquals(8, this.door.getStates().size());

        SingleClickOnTwoDiagramElementsTool connect = SysarchPaletteProvider.allTools(this.view).filter(SingleClickOnTwoDiagramElementsTool.class::isInstance)
                .map(SingleClickOnTwoDiagramElementsTool.class::cast).findFirst().orElseThrow();
        VariableManager edge = new VariableManager();
        edge.put(VariableManager.SELF, this.door);
        edge.put(EdgeDescription.SEMANTIC_EDGE_SOURCE, this.door.getStates().get(2));
        edge.put(EdgeDescription.SEMANTIC_EDGE_TARGET, this.door.getStates().get(3));
        assertTrue(connect.getHandler().apply(edge) instanceof Success);
        Transition added = this.door.getTransitions().get(this.door.getTransitions().size() - 1);
        assertEquals(8, this.door.getTransitions().size(), "stored in the machine's transitions, not at the document top");
        assertEquals(this.door.getStates().get(2), added.getSource());
        assertEquals(1, this.model.getContents().size());
        assertEquals(8, this.render().getEdges().size());
    }

    /** P3-AC-5: draw a 5-state machine with a second initial state and an unreachable state: R7 and R8. */
    @Test
    void aSecondInitialStateAndAnUnreachableStateAreR7AndR8() {
        State second = this.door.getStates().get(2);
        second.setIsInitial(true); // 开启中 becomes a second initial state
        this.door.getTransitions().removeIf(t -> t.getTarget() == this.door.getStates().get(4)); // 结束 can no longer be reached
        this.door.getStates().get(4).setIsFinal(false);
        Diagram diagram = this.render();
        assertEquals(SiriusDiagramDescriptionFactory.ELLIPSE_NODE_TYPE, this.node(diagram, "S-2").getType(), "drawn as an initial state");
        List<String> rules = BehaviorChecks.evaluate(this.model.getAllContents()).stream().map(i -> i.rule()).distinct().sorted().toList();
        assertTrue(rules.contains("R7") && rules.contains("R8"), rules.toString());
    }

    // ------------------------------------------------------------------ helpers

    private SingleClickOnDiagramElementTool tool(String id) {
        return SysarchPaletteProvider.allTools(this.view).filter(t -> t.getId().endsWith("/tool/" + id)).map(SingleClickOnDiagramElementTool.class::cast)
                .findFirst().orElseThrow();
    }

    private Node node(Diagram diagram, String stateId) {
        return states(diagram).stream().filter(n -> n.getTargetObjectId().equals(stateId)).findFirst().orElseThrow();
    }

    private static List<Node> states(Diagram diagram) {
        return diagram.getNodes().get(0).getChildNodes();
    }

    private static String label(Edge edge) {
        return edge.getCenterLabel() == null ? "" : edge.getCenterLabel().text();
    }

    private Diagram render() {
        VariableManager vm = new VariableManager();
        vm.put(VariableManager.SELF, this.door);
        vm.put(DiagramContext.DIAGRAM_CONTEXT, null);
        DiagramComponentProps props = DiagramComponentProps.newDiagramComponentProps().variableManager(vm).diagramDescription(this.view)
                .allDiagramDescriptions(List.of(this.view)).operationValidator((operation, variables) -> { }).viewCreationRequests(List.of())
                .viewDeletionRequests(List.of()).previousDiagram(Optional.empty()).diagramEvents(List.of()).nodeAppearanceHandlers(List.of())
                .edgeAppearanceHandlers(List.of()).build();
        return new DiagramRenderer().render(new Element(DiagramComponent.class, props));
    }

    private Resource saveAndReload() throws Exception {
        ResourceSet out = new ResourceSetImpl();
        out.getPackageRegistry().put(SysarchPackage.eNS_URI, SysarchPackage.eINSTANCE);
        Resource json = new JSONResourceFactory().createResourceFromPath("door");
        out.getResources().add(json);
        json.getContents().addAll(this.model.getContents());
        var bytes = new ByteArrayOutputStream();
        json.save(bytes, Map.of());
        ResourceSet in = new ResourceSetImpl();
        in.getPackageRegistry().put(SysarchPackage.eNS_URI, SysarchPackage.eINSTANCE);
        Resource loaded = new JSONResourceFactory().createResourceFromPath("loaded");
        in.getResources().add(loaded);
        loaded.load(new ByteArrayInputStream(bytes.toByteArray()), Map.of());
        return loaded;
    }

    private static State state(String id, String name, boolean initial, boolean fin) {
        State s = F.createState();
        s.setId(id);
        s.setName(name);
        s.setIsInitial(initial);
        s.setIsFinal(fin);
        return s;
    }

    private static Transition transition(String id, State source, State target, String trigger, String guard, String effect) {
        Transition t = F.createTransition();
        t.setId(id);
        t.setName(id);
        t.setSource(source);
        t.setTarget(target);
        t.setTrigger(trigger);
        t.setGuard(guard);
        t.setEffect(effect);
        return t;
    }
}

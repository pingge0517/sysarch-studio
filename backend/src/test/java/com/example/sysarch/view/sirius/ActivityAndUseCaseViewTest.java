package com.example.sysarch.view.sirius;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.model.Action;
import com.example.sysarch.model.ActionKind;
import com.example.sysarch.model.Activity;
import com.example.sysarch.model.ActivityFlow;
import com.example.sysarch.model.Actor;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.model.UseCase;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.view.generated.ActivityDiagramDescriptionProvider;
import com.example.sysarch.view.generated.UseCaseDiagramDescriptionProvider;
import com.example.sysarch.view.projection.DiagramProjector;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.Edge;
import org.eclipse.sirius.components.diagrams.ImageNodeStyle;
import org.eclipse.sirius.components.diagrams.LineStyle;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.components.diagrams.NodeType;
import org.eclipse.sirius.components.diagrams.components.DiagramComponent;
import org.eclipse.sirius.components.diagrams.components.DiagramComponentProps;
import org.eclipse.sirius.components.diagrams.description.DiagramDescription;
import org.eclipse.sirius.components.diagrams.description.EdgeDescription;
import org.eclipse.sirius.components.diagrams.renderer.DiagramRenderer;
import org.eclipse.sirius.components.diagrams.tools.SingleClickOnDiagramElementTool;
import org.eclipse.sirius.components.diagrams.tools.SingleClickOnTwoDiagramElementsTool;
import org.eclipse.sirius.components.representations.Element;
import org.eclipse.sirius.components.representations.IStatus;
import org.eclipse.sirius.components.representations.Success;
import org.eclipse.sirius.components.representations.VariableManager;
import org.eclipse.sirius.web.application.diagram.EllipseNodeStyle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * P3 T6: the activity and use-case views, both from the behaviour template. Acceptance: "各画一张，元素类型与 YAML 映射一致" —
 * activity: actions rectangles, a DECIDE action a diamond, object flows dashed, control flows solid (shape and line, not
 * colour); use case: use cases ellipses, actors stick figures, associations solid lines without arrow.
 */
class ActivityAndUseCaseViewTest {

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

    private final DiagramDescription activityView = this.factory.create(new ActivityDiagramDescriptionProvider().build());

    private final DiagramDescription useCaseView = this.factory.create(new UseCaseDiagramDescriptionProvider().build());

    private Resource model;

    private Activity unlock;

    private UseCase open;

    private UseCase lock;

    private Actor driver;

    private Actor key;

    private Actor service;

    @BeforeEach
    void fixture() {
        this.model = new ResourceImpl(URI.createURI("memory:/behaviour.json"));
        new ResourceSetImpl().getResources().add(this.model);
        this.unlock = F.createActivity();
        this.unlock.setId("AC-1");
        this.unlock.setName("解锁");
        Action read = action("A-1", "读取钥匙", ActionKind.CALL, "readKey()");
        Action check = action("A-2", "校验通过？", ActionKind.DECIDE, null);
        Action release = action("A-3", "释放门锁", ActionKind.CALL, "release()");
        Action count = action("A-4", "计数", ActionKind.ASSIGN, "n = n + 1");
        this.unlock.getActions().addAll(List.of(read, check, release, count));
        this.unlock.getFlows().addAll(List.of(flow("F-1", read, check, false), flow("F-2", check, release, false), flow("F-3", check, count, false),
                flow("F-4", read, release, true)));
        this.open = useCase("UC-1", "开门");
        this.lock = useCase("UC-2", "锁门");
        this.driver = actor("AR-1", "驾驶员");
        this.key = actor("AR-2", "钥匙");
        this.service = actor("AR-3", "维修技师"); // not linked to any use case yet
        this.open.getActors().addAll(List.of(this.driver, this.key));
        this.lock.getActors().add(this.driver);
        this.model.getContents().addAll(List.of(this.unlock, this.open, this.lock, this.driver, this.key, this.service));
    }

    @Test
    void theActivityDrawsActionsDecisionsAndBothKindsOfFlow() {
        Diagram diagram = render(this.activityView, this.unlock);
        Map<String, Node> nodes = byTarget(diagram.getNodes().get(0).getChildNodes()); // the activity is the frame
        assertEquals(4, nodes.size());
        for (String id : List.of("A-1", "A-3", "A-4")) {
            assertEquals(NodeType.NODE_RECTANGLE, nodes.get(id).getType(), id + " is an action rectangle");
        }
        assertEquals("读取钥匙\nreadKey()", nodes.get("A-1").getInsideLabel().getText(), "the expression under the name");
        Node decision = nodes.get("A-2");
        assertEquals(NodeType.NODE_IMAGE, decision.getType(), "DECIDE is a diamond");
        assertEquals(SysarchImages.PATH + "diamond.svg", ((ImageNodeStyle) decision.getStyle()).getImageURL());
        assertEquals("校验通过？", decision.getOutsideLabels().get(0).text());

        assertEquals(4, diagram.getEdges().size());
        Map<String, Edge> edges = diagram.getEdges().stream().collect(Collectors.toMap(Edge::getTargetObjectId, Function.identity()));
        for (String id : List.of("F-1", "F-2", "F-3")) {
            assertEquals(LineStyle.Solid, edges.get(id).getStyle().getLineStyle(), id + " control flow solid");
        }
        assertEquals(LineStyle.Dash, edges.get("F-4").getStyle().getLineStyle(), "object flow dashed");
        assertEquals(edges.get("F-1").getStyle().getColor(), edges.get("F-4").getStyle().getColor(), "told apart by line, not colour");
    }

    @Test
    void theActivityToolsCreateActionsDecisionsAndFlowsInTheActivity() {
        VariableManager vm = new VariableManager();
        vm.put(VariableManager.SELF, this.unlock);
        assertTrue(this.tool(this.activityView, "createDecision").getHandler().apply(vm) instanceof Success);
        Action decision = this.unlock.getActions().get(4);
        assertEquals(ActionKind.DECIDE, decision.getKind(), "defaults: kind = DECIDE");
        assertTrue(this.tool(this.activityView, "createAction").getHandler().apply(vm) instanceof Success);
        assertEquals(ActionKind.CALL, this.unlock.getActions().get(5).getKind());
        assertTrue(this.connect(this.activityView, this.unlock, this.unlock.getActions().get(5), decision) instanceof Success);
        assertEquals(5, this.unlock.getFlows().size(), "stored in the activity's flows");
        assertEquals(NodeType.NODE_IMAGE, byTarget(render(this.activityView, this.unlock).getNodes().get(0).getChildNodes())
                .get(DiagramProjector.elementId(decision)).getType());
        this.unlock.getFlows().get(4).setIsObjectFlow(true);
        assertEquals(LineStyle.Dash, render(this.activityView, this.unlock).getEdges().stream()
                .filter(e -> e.getTargetObjectId().equals(DiagramProjector.elementId(this.unlock.getFlows().get(4)))).findFirst().orElseThrow()
                .getStyle().getLineStyle(), "switching isObjectFlow restyles the flow");
    }

    @Test
    void theUseCaseDiagramDrawsUseCasesActorsAndAssociations() {
        Diagram diagram = render(this.useCaseView, this.open);
        Map<String, Node> nodes = byTarget(diagram.getNodes());
        assertEquals(5, nodes.size(), "both use cases (peers) and every actor, linked or not");
        for (String id : List.of("UC-1", "UC-2")) {
            assertEquals(SiriusDiagramDescriptionFactory.ELLIPSE_NODE_TYPE, nodes.get(id).getType());
            assertTrue(nodes.get(id).getStyle() instanceof EllipseNodeStyle ellipse && ellipse.getBackground().equals("#FFFFFF"), "an outlined ellipse");
        }
        assertEquals("开门", nodes.get("UC-1").getInsideLabel().getText(), "the name inside");
        for (String id : List.of("AR-1", "AR-2", "AR-3")) {
            assertEquals(NodeType.NODE_IMAGE, nodes.get(id).getType());
            assertEquals(SysarchImages.PATH + "stick-figure.svg", ((ImageNodeStyle) nodes.get(id).getStyle()).getImageURL());
        }
        assertEquals("维修技师", nodes.get("AR-3").getOutsideLabels().get(0).text());
        assertEquals(3, diagram.getEdges().size(), "开门–驾驶员, 开门–钥匙, 锁门–驾驶员");
        for (Edge edge : diagram.getEdges()) {
            assertEquals(LineStyle.Solid, edge.getStyle().getLineStyle());
            assertEquals(org.eclipse.sirius.components.diagrams.ArrowStyle.None, edge.getStyle().getTargetArrow(), "association: no arrow");
        }
    }

    @Test
    void associatingLinksTheActorAndCreatesNothing() {
        int before = this.model.getContents().size();
        assertTrue(this.connect(this.useCaseView, this.open, this.lock, this.service) instanceof Success);
        assertEquals(List.of(this.driver, this.service), this.lock.getActors(), "the actor is added to the use case's actors");
        assertEquals(before, this.model.getContents().size(), "no new element");
        assertTrue(this.connect(this.useCaseView, this.open, this.lock, this.service) instanceof Success);
        assertEquals(2, this.lock.getActors().size(), "linking twice changes nothing");
        assertEquals(4, render(this.useCaseView, this.open).getEdges().size());

        VariableManager vm = new VariableManager();
        vm.put(VariableManager.SELF, this.open);
        assertTrue(this.tool(this.useCaseView, "createActor").getHandler().apply(vm) instanceof Success);
        assertTrue(this.tool(this.useCaseView, "createUseCase").getHandler().apply(vm) instanceof Success);
        assertEquals(before + 2, this.model.getContents().size(), "both are document roots");
        assertEquals(7, byTarget(render(this.useCaseView, this.open).getNodes()).size(), "a new actor shows at once, before any link");
    }

    // ------------------------------------------------------------------ helpers

    private IStatus connect(DiagramDescription view, EObject root, EObject source, EObject target) {
        SingleClickOnTwoDiagramElementsTool tool = SysarchPaletteProvider.allTools(view).filter(SingleClickOnTwoDiagramElementsTool.class::isInstance)
                .map(SingleClickOnTwoDiagramElementsTool.class::cast).findFirst().orElseThrow();
        VariableManager vm = new VariableManager();
        vm.put(VariableManager.SELF, root);
        vm.put(EdgeDescription.SEMANTIC_EDGE_SOURCE, source);
        vm.put(EdgeDescription.SEMANTIC_EDGE_TARGET, target);
        return tool.getHandler().apply(vm);
    }

    private SingleClickOnDiagramElementTool tool(DiagramDescription view, String id) {
        return SysarchPaletteProvider.allTools(view).filter(t -> t.getId().endsWith("/tool/" + id)).map(SingleClickOnDiagramElementTool.class::cast)
                .findFirst().orElseThrow();
    }

    private static Map<String, Node> byTarget(List<Node> nodes) {
        return nodes.stream().collect(Collectors.toMap(Node::getTargetObjectId, Function.identity()));
    }

    private static Diagram render(DiagramDescription view, EObject root) {
        VariableManager vm = new VariableManager();
        vm.put(VariableManager.SELF, root);
        vm.put(DiagramContext.DIAGRAM_CONTEXT, null);
        DiagramComponentProps props = DiagramComponentProps.newDiagramComponentProps().variableManager(vm).diagramDescription(view)
                .allDiagramDescriptions(List.of(view)).operationValidator((operation, variables) -> { }).viewCreationRequests(List.of())
                .viewDeletionRequests(List.of()).previousDiagram(Optional.empty()).diagramEvents(List.of()).nodeAppearanceHandlers(List.of())
                .edgeAppearanceHandlers(List.of()).build();
        return new DiagramRenderer().render(new Element(DiagramComponent.class, props));
    }

    private static Action action(String id, String name, ActionKind kind, String expression) {
        Action a = F.createAction();
        a.setId(id);
        a.setName(name);
        a.setKind(kind);
        a.setExpression(expression);
        return a;
    }

    private static ActivityFlow flow(String id, Action source, Action target, boolean objectFlow) {
        ActivityFlow f = F.createActivityFlow();
        f.setId(id);
        f.setName(id);
        f.setSource(source);
        f.setTarget(target);
        f.setIsObjectFlow(objectFlow);
        return f;
    }

    private static UseCase useCase(String id, String name) {
        UseCase u = F.createUseCase();
        u.setId(id);
        u.setName(name);
        return u;
    }

    private static Actor actor(String id, String name) {
        Actor a = F.createActor();
        a.setId(id);
        a.setName(name);
        return a;
    }
}

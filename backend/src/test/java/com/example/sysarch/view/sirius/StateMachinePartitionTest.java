package com.example.sysarch.view.sirius;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.model.StateMachine;
import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.view.generated.StateMachineDiagramDescriptionProvider;
import com.example.sysarch.view.projection.DiagramProjector;
import com.example.sysarch.view.projection.DiagramSnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.components.diagrams.components.DiagramComponent;
import org.eclipse.sirius.components.diagrams.components.DiagramComponentProps;
import org.eclipse.sirius.components.diagrams.description.DiagramDescription;
import org.eclipse.sirius.components.diagrams.renderer.DiagramRenderer;
import org.eclipse.sirius.components.diagrams.tools.SingleClickOnDiagramElementTool;
import org.eclipse.sirius.components.representations.Element;
import org.eclipse.sirius.components.representations.Failure;
import org.eclipse.sirius.components.representations.Success;
import org.eclipse.sirius.components.representations.VariableManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * Spec P3 §9 "大状态机渲染超 3s → 状态机节点上限 200，超出启用分区视图". Measured first (StateMachineScaleTest and the
 * browser): the server renders 2000 states in 0.2 s, but Sirius' frontend took 6.3 s for 1000 states and then froze, and
 * zooming 500 states took 1.9 s. So the behaviour views carry {@code partitionSize: 200}: above 200 states one partition
 * of at most 200 (breadth-first along the transitions) is drawn, the frame says which, and palette tools page through.
 */
class StateMachinePartitionTest {

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

    private final DiagramFilterState state = new DiagramFilterState();

    private final DiagramDescription view = new SiriusDiagramDescriptionFactory(SysarchPackage.eINSTANCE, IDS, new ProjectionCache(), this.state, VALIDATOR)
            .create(new StateMachineDiagramDescriptionProvider().build());

    @Test
    void aLargeMachineIsShownOnePartitionAtATime() {
        StateMachine m = machine(1000);
        Diagram first = this.render(m, "d1");
        Node frame = first.getNodes().get(0);
        assertEquals(200, frame.getChildNodes().size(), "at most 200 states");
        assertTrue(frame.getInsideLabel().getText().startsWith("大状态机 1000（分区 1/5：第 1–200 个（共 1000 个），跨分区连线 "), frame.getInsideLabel().getText());
        assertTrue(first.getEdges().size() > 199, "the transitions inside the partition, at least the chain: " + first.getEdges().size());
        Set<String> firstStates = ids(frame);
        assertTrue(firstStates.contains("S-0") && firstStates.contains("S-1"), "breadth-first from the first state: its neighbours come with it");

        assertTrue(this.tool("next").getHandler().apply(this.vm(m, "d1")) instanceof Success);
        Node second = this.render(m, "d1").getNodes().get(0);
        assertTrue(second.getInsideLabel().getText().contains("分区 2/5：第 201–400 个"), second.getInsideLabel().getText());
        Set<String> secondStates = ids(second);
        assertEquals(200, secondStates.size());
        assertFalse(secondStates.stream().anyMatch(firstStates::contains), "partitions do not overlap");

        for (int i = 0; i < 6; i++) {
            this.tool("next").getHandler().apply(this.vm(m, "d1"));
        }
        assertTrue(this.render(m, "d1").getNodes().get(0).getInsideLabel().getText().contains("分区 5/5"), "stops at the last");
        for (int i = 0; i < 9; i++) {
            this.tool("previous").getHandler().apply(this.vm(m, "d1"));
        }
        assertTrue(this.render(m, "d1").getNodes().get(0).getInsideLabel().getText().contains("分区 1/5"), "and at the first");
        assertTrue(this.render(m, "d2").getNodes().get(0).getInsideLabel().getText().contains("分区 1/5"), "per diagram");

        Set<String> all = new HashSet<>();
        for (int p = 0; p < 5; p++) {
            all.addAll(DiagramProjector.project(new StateMachineDiagramDescriptionProvider().build(), m, p == 0 ? Set.of() : Set.of(DiagramProjector.PARTITION_TOKEN + p))
                    .nodes().stream().map(DiagramSnapshot.Node::id).collect(Collectors.toSet()));
        }
        assertEquals(1000, all.size(), "every state is in exactly one partition");
    }

    @Test
    void aSmallMachineIsNotPartitioned() {
        StateMachine m = machine(150);
        Node frame = this.render(m, "d3").getNodes().get(0);
        assertEquals(150, frame.getChildNodes().size());
        assertEquals("大状态机 150", frame.getInsideLabel().getText());
        assertTrue(this.tool("next").getHandler().apply(this.vm(m, "d3")) instanceof Failure, "nothing to page through");
    }

    // ------------------------------------------------------------------ helpers

    private static StateMachine machine(int states) {
        StateMachine m = StateMachineScaleTest.machine(states, 7);
        Resource r = new ResourceImpl(URI.createURI("memory:/partition" + states + ".json"));
        new ResourceSetImpl().getResources().add(r);
        r.getContents().add(m);
        return m;
    }

    private static Set<String> ids(Node frame) {
        return frame.getChildNodes().stream().map(Node::getTargetObjectId).collect(Collectors.toSet());
    }

    private SingleClickOnDiagramElementTool tool(String which) {
        return SysarchPaletteProvider.allTools(this.view).filter(t -> t.getId().endsWith("/tool/partition/" + which)).map(SingleClickOnDiagramElementTool.class::cast)
                .findFirst().orElseThrow();
    }

    /** A variable manager as Sirius gives a tool: the diagram (for its id) and the root. */
    private VariableManager vm(EObject root, String diagramId) {
        VariableManager top = new VariableManager();
        top.put(VariableManager.SELF, root);
        top.put(DiagramContext.DIAGRAM_CONTEXT, new DiagramContext(Diagram.newDiagram(diagramId).descriptionId(this.view.getId()).targetObjectId("x")
                .nodes(List.of()).edges(List.of()).layoutData(new org.eclipse.sirius.components.diagrams.layoutdata.DiagramLayoutData(java.util.Map.of(), java.util.Map.of(), java.util.Map.of()))
                .build()));
        return top;
    }

    private Diagram render(EObject root, String diagramId) {
        VariableManager vm = this.vm(root, diagramId);
        DiagramComponentProps props = DiagramComponentProps.newDiagramComponentProps().variableManager(vm).diagramDescription(this.view)
                .allDiagramDescriptions(List.of(this.view)).operationValidator((operation, variables) -> { }).viewCreationRequests(List.of())
                .viewDeletionRequests(List.of()).previousDiagram(Optional.empty()).diagramEvents(List.of()).nodeAppearanceHandlers(List.of())
                .edgeAppearanceHandlers(List.of()).build();
        return new DiagramRenderer().render(new Element(DiagramComponent.class, props));
    }
}

package com.example.sysarch.view.sirius;

import com.example.sysarch.model.State;
import com.example.sysarch.model.StateMachine;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.model.Transition;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.view.generated.StateMachineDiagramDescriptionProvider;
import com.example.sysarch.view.projection.DiagramProjector;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.components.DiagramComponent;
import org.eclipse.sirius.components.diagrams.components.DiagramComponentProps;
import org.eclipse.sirius.components.diagrams.description.DiagramDescription;
import org.eclipse.sirius.components.diagrams.renderer.DiagramRenderer;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.components.representations.Element;
import org.eclipse.sirius.components.representations.VariableManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * Spec P3 §9 risk "行为视图拖慢整体性能 — 大状态机渲染超 3s → 状态机节点上限 200，超出启用分区视图": measures whether the
 * trigger is met. Renders state machines of 200 / 500 / 1000 / 2000 states (about two transitions per state) through
 * Sirius' renderer (projection + Sirius diagram), cold and warm; writes build/perf/state-machine-scale.json and the
 * 200 / 500 / 1000-state machines as Sirius documents (build/perf/state-machine-N.json) for the browser check.
 */
class StateMachineScaleTest {

    /**
     * Created in {@link #measure()} and closed in {@link #dispose()}, never in a static initializer: other tests call
     * {@link #machine(int, long)}, and a second live validator breaks Eclipse OCL for whichever class runs next (CLAUDE.md).
     */
    private static ConstraintValidator validator;

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
        if (validator != null) {
            validator.close();
            validator = null;
        }
    }

    static StateMachine machine(int states, long seed) {
        var f = SysarchFactory.eINSTANCE;
        StateMachine m = f.createStateMachine();
        m.setId("SM-" + states);
        m.setName("大状态机 " + states);
        List<State> all = new ArrayList<>();
        for (int i = 0; i < states; i++) {
            State s = f.createState();
            s.setId("S-" + i);
            s.setName("状态 " + i);
            s.setIsInitial(i == 0);
            s.setIsFinal(i == states - 1);
            if (i % 5 == 0) {
                s.setDoBehavior("处理 " + i);
            }
            all.add(s);
        }
        m.getStates().addAll(all);
        Random random = new Random(seed);
        int n = 0;
        for (int i = 0; i < states - 1; i++) { // a chain, so everything is reachable
            m.getTransitions().add(transition(f, "T-" + n++, all.get(i), all.get(i + 1), "e" + i, i % 3 == 0 ? "g" + i : null, i % 4 == 0 ? "a" + i : null));
        }
        for (int i = 0; i < states; i++) { // and as many back/cross edges
            m.getTransitions().add(transition(f, "T-" + n++, all.get(random.nextInt(states - 1)), all.get(1 + random.nextInt(states - 1)), "x" + i, null, null));
        }
        return m;
    }

    private static Transition transition(SysarchFactory f, String id, State s, State t, String trigger, String guard, String effect) {
        Transition tr = f.createTransition();
        tr.setId(id);
        tr.setName(id);
        tr.setSource(s);
        tr.setTarget(t);
        tr.setTrigger(trigger);
        tr.setGuard(guard);
        tr.setEffect(effect);
        return tr;
    }

    @Test
    void measure() throws Exception {
        validator = new ConstraintValidator();
        SiriusDiagramDescriptionFactory factory = new SiriusDiagramDescriptionFactory(SysarchPackage.eINSTANCE, IDS, new ProjectionCache(), new DiagramFilterState(),
                validator);
        DiagramDescription view = factory.create(new StateMachineDiagramDescriptionProvider().build());
        StringBuilder json = new StringBuilder("{\"budgetMs\": 3000, \"results\": [");
        for (int states : List.of(200, 500, 1000, 2000)) {
            StateMachine m = machine(states, 42);
            Resource r = new ResourceImpl(URI.createURI("memory:/sm" + states + ".json"));
            new ResourceSetImpl().getResources().add(r);
            r.getContents().add(m);
            long t0 = System.nanoTime();
            Diagram cold = render(view, m);
            long cold_ms = (System.nanoTime() - t0) / 1_000_000;
            long warm = Long.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                long t1 = System.nanoTime();
                render(view, m);
                warm = Math.min(warm, (System.nanoTime() - t1) / 1_000_000);
            }
            int nodes = cold.getNodes().get(0).getChildNodes().size();
            System.out.printf("state machine %d states, %d transitions: %d nodes, %d edges, cold %d ms, warm %d ms%n", states, m.getTransitions().size(), nodes,
                    cold.getEdges().size(), cold_ms, warm);
            json.append(states == 200 ? "" : ",").append(String.format("{\"states\": %d, \"transitions\": %d, \"nodes\": %d, \"edges\": %d, \"coldMs\": %d, \"warmMs\": %d}",
                    states, m.getTransitions().size(), nodes, cold.getEdges().size(), cold_ms, warm));
            if (states <= 1000) {
                var set = new ResourceSetImpl();
                set.getPackageRegistry().put(SysarchPackage.eNS_URI, SysarchPackage.eINSTANCE);
                Resource doc = new JSONResourceFactory().createResourceFromPath("state-machine-" + states);
                set.getResources().add(doc);
                doc.getContents().add(machine(states, 42));
                var out = new ByteArrayOutputStream();
                doc.save(out, Map.of());
                Files.createDirectories(Path.of("build/perf"));
                Files.writeString(Path.of("build/perf/state-machine-" + states + ".json"), out.toString(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            }
        }
        Files.createDirectories(Path.of("build/perf"));
        Files.writeString(Path.of("build/perf/state-machine-scale.json"), json.append("]}\n").toString(), StandardCharsets.UTF_8);
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
}

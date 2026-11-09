package com.example.sysarch.view.sirius;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.model.Allocate;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.ConnectionKind;
import com.example.sysarch.model.PortDirection;
import com.example.sysarch.model.InterfaceDef;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.view.generated.LaArchitectureDiagramDescriptionProvider;
import com.example.sysarch.view.generated.PaArchitectureDiagramDescriptionProvider;
import com.example.sysarch.view.generated.SaFunctionChainDiagramDescriptionProvider;
import com.example.sysarch.view.projection.DiagramProjector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.components.DiagramComponent;
import org.eclipse.sirius.components.diagrams.components.DiagramComponentProps;
import org.eclipse.sirius.components.diagrams.description.DiagramDescription;
import org.eclipse.sirius.components.diagrams.renderer.DiagramRenderer;
import org.eclipse.sirius.components.representations.Element;
import org.eclipse.sirius.components.representations.VariableManager;
import org.eclipse.sirius.emfjson.resource.JsonResourceFactoryImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * P1 T9 step 3 performance baseline: 500 elements, the SA, LA and PA views open together, switching view &lt; 1 s.
 *
 * <p>In Sirius, every open diagram is re-rendered on each semantic change, and showing a view renders it with its
 * previous diagram; so a switch costs one render of the target view, and the three views share the projection cache
 * as in the server. Two cases are measured round-robin: a plain switch (model unchanged since the view was last
 * drawn) and a switch after an edit (one rename, so the projection is recomputed). The report goes to
 * {@code build/perf-view-switch.json}; {@code checkPerfThreshold} holds the 1 s limit. The model is also written as a
 * Sirius JSON document ({@code build/perf-views-500.json}) for the live check over GraphQL (CLAUDE.md).
 */
class ViewSwitchPerfTest {

    private static final SysarchFactory FACTORY = SysarchFactory.eINSTANCE;

    private static final ConstraintValidator VALIDATOR = new ConstraintValidator();

    private static final int WARMUP = 15;

    private static final int SWITCHES = 60;

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

    private final List<DiagramDescription> views = List.of(
            this.factory.create(new SaFunctionChainDiagramDescriptionProvider().build()),
            this.factory.create(new LaArchitectureDiagramDescriptionProvider().build()),
            this.factory.create(new PaArchitectureDiagramDescriptionProvider().build()));

    private Resource model;

    private final List<PartDefinition> roots = new ArrayList<>();

    @BeforeEach
    void model() {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("json", new JsonResourceFactoryImpl());
        this.model = resourceSet.createResource(URI.createFileURI(Path.of("build", "perf-views-500.json").toAbsolutePath().toString()));
        this.model.getContents().addAll(build(this.roots));
    }

    @Test
    void switchingBetweenTheThreeLayerViewsTakesLessThanOneSecond() throws IOException {
        long elements = count(this.model);
        assertTrue(elements >= 500, "the scenario needs 500 elements, has " + elements);
        this.model.save(Map.of());

        Diagram[] open = new Diagram[3];
        for (int i = 0; i < 3; i++) {
            open[i] = this.render(i, null);
        }
        assertEquals(31, open[0].getNodes().size(), "SA draws the system blocks");
        assertEquals(29, open[1].getEdges().size(), "LA draws the logical chain");

        for (int i = 0; i < WARMUP; i++) {
            this.switchTo(i % 3, open, i % 2 == 0);
        }
        List<Double> plain = new ArrayList<>();
        List<Double> afterEdit = new ArrayList<>();
        for (int i = 0; i < SWITCHES; i++) {
            plain.add(this.switchTo(i % 3, open, false));
            afterEdit.add(this.switchTo((i + 1) % 3, open, true));
        }

        Stats plainStats = Stats.of(plain);
        Stats editStats = Stats.of(afterEdit);
        Path report = Path.of("build", "perf-view-switch.json");
        Files.createDirectories(report.getParent());
        Files.writeString(report, String.format(Locale.ROOT,
                "{\"elements\": %d, \"views\": 3, \"switches\": %d, \"plainMedianMs\": %.2f, \"plainP95Ms\": %.2f, \"plainMaxMs\": %.2f, "
                        + "\"afterEditMedianMs\": %.2f, \"afterEditP95Ms\": %.2f, \"afterEditMaxMs\": %.2f}%n",
                elements, SWITCHES * 2, plainStats.median, plainStats.p95, plainStats.max, editStats.median, editStats.p95, editStats.max),
                StandardCharsets.UTF_8);
        assertTrue(Math.max(plainStats.max, editStats.max) < 1000, "a switch took " + Math.max(plainStats.max, editStats.max) + " ms");
    }

    /** Shows view {@code index} as Sirius does, after an edit if asked; returns the time in ms. */
    private double switchTo(int index, Diagram[] open, boolean edit) {
        if (edit) {
            PartDefinition renamed = this.roots.get(index);
            renamed.setName(renamed.getName().endsWith("'") ? renamed.getName().substring(0, renamed.getName().length() - 1) : renamed.getName() + "'");
        }
        long start = System.nanoTime();
        open[index] = this.render(index, open[index]);
        return (System.nanoTime() - start) / 1e6;
    }

    private Diagram render(int index, Diagram previous) {
        VariableManager vm = new VariableManager();
        vm.put(VariableManager.SELF, this.roots.get(index));
        vm.put(DiagramContext.DIAGRAM_CONTEXT, null);
        DiagramComponentProps props = DiagramComponentProps.newDiagramComponentProps()
                .variableManager(vm)
                .diagramDescription(this.views.get(index))
                .allDiagramDescriptions(this.views)
                .operationValidator((operation, variables) -> { })
                .viewCreationRequests(List.of())
                .viewDeletionRequests(List.of())
                .previousDiagram(Optional.ofNullable(previous))
                .diagramEvents(List.of())
                .nodeAppearanceHandlers(List.of())
                .edgeAppearanceHandlers(List.of())
                .build();
        return new DiagramRenderer().render(new Element(DiagramComponent.class, props));
    }

    /**
     * Per layer SYSTEM, LOGICAL, PHYSICAL: a root block holding 30 parts, each typed by its own block, with an in and an
     * out port, chained by 29 connections (150 elements). Plus one interface and 49 allocations down the layers: 500.
     */
    static List<EObject> build(List<PartDefinition> roots) {
        List<EObject> contents = new ArrayList<>();
        InterfaceDef signal = FACTORY.createInterfaceDef();
        signal.setId("IF-SIG");
        signal.setName("Signal");
        signal.setLayer(Layer.LOGICAL);
        signal.setProtocol("CAN FD");
        contents.add(signal);
        List<List<PartDefinition>> typesByLayer = new ArrayList<>();
        for (Layer layer : List.of(Layer.SYSTEM, Layer.LOGICAL, Layer.PHYSICAL)) {
            String prefix = layer.getLiteral().substring(0, 1);
            PartDefinition root = named(FACTORY.createPartDefinition(), prefix + "-ROOT", layer + " 根", layer);
            roots.add(root);
            contents.add(root);
            List<PartDefinition> types = new ArrayList<>();
            Port previousOut = null;
            for (int i = 1; i <= 30; i++) {
                PartDefinition type = named(FACTORY.createPartDefinition(), prefix + "-PD-" + i, layer + " 组件 " + i, layer);
                types.add(type);
                contents.add(type);
                Part part = named(FACTORY.createPart(), prefix + "-PT-" + i, "实例 " + i, layer);
                part.setTypedBy(type);
                root.getOwnedParts().add(part);
                Port in = port(prefix + "-IN-" + i, PortDirection.IN, signal, layer);
                Port out = port(prefix + "-OUT-" + i, PortDirection.OUT, signal, layer);
                part.getPorts().add(in);
                part.getPorts().add(out);
                if (previousOut != null) {
                    Connection connection = named(FACTORY.createConnection(), prefix + "-CO-" + i, "链 " + i, layer);
                    connection.setKind(ConnectionKind.ASSEMBLY);
                    connection.setSource(previousOut);
                    connection.setTarget(in);
                    connection.setInterfaceType(signal);
                    contents.add(connection);
                }
                previousOut = out;
            }
            typesByLayer.add(types);
        }
        for (int i = 0; i < 49; i++) {
            int down = i < 25 ? 0 : 1;
            Allocate allocate = named(FACTORY.createAllocate(), "AL-" + (i + 1), "分配 " + (i + 1), Layer.LOGICAL);
            allocate.setFrom(typesByLayer.get(down).get(i % 30));
            allocate.setTo(typesByLayer.get(down + 1).get(i % 30));
            contents.add(allocate);
        }
        return contents;
    }

    private static Port port(String id, PortDirection direction, InterfaceDef type, Layer layer) {
        Port port = named(FACTORY.createPort(), id, id.toLowerCase(Locale.ROOT), layer);
        port.setDirection(direction);
        port.setInterfaceType(type);
        return port;
    }

    private static <T extends ArchElement> T named(T element, String id, String name, Layer layer) {
        element.setId(id);
        element.setName(name);
        element.setLayer(layer);
        return element;
    }

    private static long count(Resource resource) {
        List<EObject> all = new ArrayList<>();
        resource.getAllContents().forEachRemaining(all::add);
        return all.stream().filter(ArchElement.class::isInstance).count();
    }

    private record Stats(double median, double p95, double max) {

        static Stats of(List<Double> samples) {
            List<Double> sorted = new ArrayList<>(samples);
            Collections.sort(sorted);
            return new Stats(sorted.get(sorted.size() / 2), sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1), sorted.get(sorted.size() - 1));
        }
    }
}

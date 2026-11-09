package com.example.sysarch.view.refresh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.model.Connection;
import com.example.sysarch.model.ConnectionKind;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.view.api.DiagramDescription;
import com.example.sysarch.view.generated.IbdDiagramDescriptionProvider;
import com.example.sysarch.view.layout.DiagramLayoutStore;
import com.example.sysarch.view.layout.LayoutCascade;
import com.example.sysarch.view.projection.DiagramSnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.util.EContentAdapter;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T5 acceptance (spec §5.2, §8): the IBD follows the model through incremental diffs only, layout is kept
 * apart from the model and cascades on delete, and refresh meets the 500-element budget (perf report in
 * {@code build/perf-refresh.json}).
 */
class IbdSyncTest {

    private static final SysarchFactory FACTORY = SysarchFactory.eINSTANCE;

    private static final String IBD = "ibd";

    private final DiagramDescription ibd = new IbdDiagramDescriptionProvider().build();

    private final List<SnapshotDiff> published = new ArrayList<>();

    private Resource model;

    private DiagramLayoutStore layouts;

    private PartDefinition vehicle;

    private PartDefinition controller;

    private Part ctrl;

    private Port ctrlPort;

    private Port external;

    private Connection bus;

    private DiagramSynchronizer synchronizer;

    private LayoutCascade cascade;

    private int counter;

    @BeforeEach
    void fixture() {
        this.model = new ResourceImpl(URI.createURI("memory:/model.xmi"));
        this.layouts = new DiagramLayoutStore(new ResourceImpl(URI.createURI("memory:/layout.xmi")));
        this.controller = this.definition("Controller");
        this.vehicle = this.definition("Vehicle");
        this.external = this.port("ext", "CAN");
        this.vehicle.getOwnedPorts().add(this.external);
        this.ctrl = this.part("ctrl", this.controller);
        this.ctrlPort = this.port("can0", "CAN");
        this.ctrl.getPorts().add(this.ctrlPort);
        this.vehicle.getOwnedParts().add(this.ctrl);
        this.bus = this.connection(this.external, this.ctrlPort, ConnectionKind.ASSEMBLY);
        this.model.getContents().addAll(List.of(this.controller, this.vehicle, this.bus));

        this.synchronizer = new DiagramSynchronizer(this.ibd, this.vehicle, Runnable::run, this.published::add);
        this.cascade = LayoutCascade.attach(this.model, this.layouts);
    }

    @AfterEach
    void detach() {
        this.synchronizer.close();
        this.cascade.close();
    }

    /** AC-4: a Part added in the model tree shows up in the open IBD as one incremental insert. */
    @Test
    void newPartAppearsAsAnIncrementalInsert() {
        Part sensor = this.part("sensor", this.controller);
        Port sensorPort = this.port("s0", "CAN");
        sensor.getPorts().add(sensorPort);

        this.vehicle.getOwnedParts().add(sensor);

        assertEquals(1, this.published.size());
        SnapshotDiff diff = this.published.get(0);
        assertEquals(List.of(sensor.getId(), sensorPort.getId()), ids(diff.addedNodes()));
        assertEquals(2, diff.size(), "only the new part and its port; existing nodes are not re-sent");
        assertEquals("sensor", this.synchronizer.snapshot().node(sensor.getId()).orElseThrow().label());
    }

    /** Spec §5.2: a rename recomputes the label only. */
    @Test
    void renameUpdatesOnlyThatLabel() {
        this.ctrl.setName("mainController");

        SnapshotDiff diff = this.single();
        assertEquals(List.of(this.ctrl.getId()), ids(diff.updatedNodes()));
        assertEquals("mainController", diff.updatedNodes().get(0).label());
        assertEquals(1, diff.size());
    }

    /** Renaming a type updates the sub-label of every part typed by it, which an autoExpand-only view would miss. */
    @Test
    void renamingATypeUpdatesThePartsThatShowIt() {
        this.controller.setName("ECU");

        SnapshotDiff diff = this.single();
        assertEquals(List.of(this.ctrl.getId()), ids(diff.updatedNodes()));
        assertEquals("ECU [1]", diff.updatedNodes().get(0).subLabel());
    }

    /** Spec §5.2: creating and deleting a connection adds and removes one edge; other edges keep their waypoints. */
    @Test
    void connectionCreateAndDeleteKeepOtherWaypoints() {
        String busEdge = "assembly:" + this.external.getId() + "->" + this.ctrlPort.getId();
        this.layouts.saveEdge(IBD, busEdge, List.of("10,20", "10,80"));
        Port spare = this.port("spare", "CAN");
        this.vehicle.getOwnedPorts().add(spare);
        this.published.clear();

        Connection second = this.connection(spare, this.ctrlPort, ConnectionKind.ASSEMBLY);
        this.model.getContents().add(second);
        assertEquals(1, this.single().addedEdges().size());

        this.published.clear();
        EcoreUtil.delete(second);
        assertEquals(1, this.single().removedEdgeIds().size());

        assertEquals(List.of("10,20", "10,80"), this.layouts.find(IBD, busEdge).orElseThrow().getWaypoints());
    }

    /** Spec §5.1 invariant 2 / §5.2: deleting an element removes its node, border nodes, edges and their layouts. */
    @Test
    void deletingAnElementCascadesToNodesEdgesAndLayout() {
        Part other = this.part("other", this.controller);
        this.vehicle.getOwnedParts().add(other);
        String busEdge = "assembly:" + this.external.getId() + "->" + this.ctrlPort.getId();
        this.layouts.saveNode(IBD, this.ctrl.getId(), 100, 100, 180, 90);
        this.layouts.saveNode(IBD, this.ctrlPort.getId(), 275, 140, 10, 10);
        this.layouts.saveEdge(IBD, busEdge, List.of("50,145"));
        this.layouts.saveNode(IBD, other.getId(), 400, 100, 180, 90);
        this.layouts.saveNode("bdd", this.ctrl.getId(), 0, 0, 180, 90); // another diagram: also cascaded
        this.published.clear();

        EcoreUtil.delete(this.ctrl);

        SnapshotDiff diff = this.single();
        assertEquals(List.of(this.ctrl.getId(), this.ctrlPort.getId()), diff.removedNodeIds());
        assertEquals(List.of(busEdge), diff.removedEdgeIds());
        assertTrue(this.layouts.find(IBD, this.ctrl.getId()).isEmpty());
        assertTrue(this.layouts.find(IBD, this.ctrlPort.getId()).isEmpty());
        assertTrue(this.layouts.find(IBD, busEdge).isEmpty());
        assertTrue(this.layouts.find("bdd", this.ctrl.getId()).isEmpty());
        assertTrue(this.layouts.find(IBD, other.getId()).isPresent(), "unrelated layout is kept");
    }

    /** Moving an element to another container is not a deletion: its layout survives. */
    @Test
    void movingAnElementKeepsItsLayout() {
        this.layouts.saveNode(IBD, this.ctrl.getId(), 100, 100, 180, 90);
        this.controller.getOwnedParts().add(this.ctrl); // moves it out of Vehicle

        assertTrue(this.layouts.find(IBD, this.ctrl.getId()).isPresent());
        assertTrue(this.synchronizer.snapshot().node(this.ctrl.getId()).isEmpty(), "no longer part of the Vehicle IBD");
    }

    /** Spec §5.2 last row: dragging writes only DiagramLayout and produces no model change event. */
    @Test
    void layoutChangeRaisesNoModelEvent() {
        List<Notification> modelEvents = new ArrayList<>();
        this.model.eAdapters().add(new EContentAdapter() {
            @Override
            public void notifyChanged(Notification notification) {
                super.notifyChanged(notification);
                if (!notification.isTouch() && notification.getEventType() != Notification.REMOVING_ADAPTER) {
                    modelEvents.add(notification);
                }
            }
        });

        this.layouts.saveNode(IBD, this.ctrl.getId(), 300, 200, 180, 90);
        this.layouts.saveEdge(IBD, "assembly:x->y", List.of("1,2"));

        assertEquals(List.of(), modelEvents);
        assertEquals(List.of(), this.published);
    }

    /** Spec §5.1 invariant 2: deleting layout does not affect the model. */
    @Test
    void deletingLayoutLeavesTheModelUntouched() {
        this.layouts.saveNode(IBD, this.ctrl.getId(), 300, 200, 180, 90);
        Collection<EObject> before = EcoreUtil.copyAll(this.model.getContents());

        assertTrue(this.layouts.delete(IBD, this.ctrl.getId()));

        assertTrue(EcoreUtil.equals(List.copyOf(before), this.model.getContents()));
        assertTrue(this.synchronizer.snapshot().node(this.ctrl.getId()).isPresent());
    }

    @Test
    void layoutsMustNotLiveInTheModelResource() {
        assertThrows(IllegalArgumentException.class, () -> LayoutCascade.attach(this.model, new DiagramLayoutStore(this.model)));
    }

    /** Spec §11.2 #5: changes made before the refresh runs are coalesced into one diff. */
    @Test
    void changesAreCoalescedIntoOneRefresh() {
        this.synchronizer.close();
        List<Runnable> queue = new ArrayList<>();
        List<SnapshotDiff> diffs = new ArrayList<>();
        DiagramSynchronizer queued = new DiagramSynchronizer(this.ibd, this.vehicle, queue::add, diffs::add);

        this.ctrl.setName("a");
        this.ctrl.setName("b");
        this.vehicle.getOwnedParts().add(this.part("extra", this.controller));
        assertEquals(1, queue.size(), "one refresh scheduled for three changes");
        queue.get(0).run();

        assertEquals(1, diffs.size());
        assertEquals(1, diffs.get(0).addedNodes().size());
        assertEquals("b", diffs.get(0).updatedNodes().get(0).label());
        queued.close();
    }

    /** A delegation connection uses the delegation style; changing kind restyles the same edge (keeps its id). */
    @Test
    void connectionKindSelectsTheEdgeStyle() {
        String busEdge = "assembly:" + this.external.getId() + "->" + this.ctrlPort.getId();
        assertEquals("assembly", this.synchronizer.snapshot().edges().get(0).styleId());

        this.bus.setKind(ConnectionKind.DELEGATION);

        SnapshotDiff diff = this.single();
        assertEquals(1, diff.updatedEdges().size());
        assertEquals(busEdge, diff.updatedEdges().get(0).id());
        assertEquals("delegation", diff.updatedEdges().get(0).styleId());
        assertEquals("", diff.updatedEdges().get(0).label());
    }

    @Test
    void closedSynchronizerPublishesNothing() {
        this.synchronizer.close();
        this.ctrl.setName("ignored");
        assertEquals(List.of(), this.published);
    }

    /**
     * T5 step 6: 500-element model, 20 consecutive additions. Measures model change to published diff (projection
     * and diff, the server side of the refresh) and writes build/perf-refresh.json.
     */
    @Test
    void refreshMeetsTheBudgetAt500Elements() throws IOException {
        this.synchronizer.close();
        this.cascade.close();
        Resource big = new ResourceImpl(URI.createURI("memory:/perf.xmi"));
        List<PartDefinition> types = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            types.add(this.definition("Type" + i));
        }
        PartDefinition system = this.definition("System");
        List<Port> ports = new ArrayList<>();
        for (int i = 0; i < 160; i++) {
            Part part = this.part("p" + i, types.get(i % types.size()));
            for (int j = 0; j < 2; j++) {
                Port port = this.port("p" + i + "_" + j, "IF" + (i % 4));
                part.getPorts().add(port);
                ports.add(port);
            }
            system.getOwnedParts().add(part);
        }
        List<Connection> connections = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            connections.add(this.connection(ports.get(2 * i + 1), ports.get(2 * i + 2), ConnectionKind.ASSEMBLY));
        }
        big.getContents().addAll(types);
        big.getContents().add(system);
        big.getContents().addAll(connections);
        int elements = count(big);
        assertEquals(500, elements);

        List<SnapshotDiff> diffs = new ArrayList<>();
        DiagramSynchronizer perf = new DiagramSynchronizer(this.ibd, system, Runnable::run, diffs::add);
        List<Double> samples = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Part added = this.part("added" + i, types.get(0));
            added.getPorts().add(this.port("added" + i + "_0", "IF0"));
            long start = System.nanoTime();
            system.getOwnedParts().add(added);
            samples.add((System.nanoTime() - start) / 1_000_000.0);
        }
        perf.close();

        assertEquals(20, diffs.size());
        diffs.forEach(diff -> assertEquals(2, diff.size(), "each addition is a 2-node insert, not a redraw"));
        double median = percentile(samples, 50);
        double p95 = percentile(samples, 95);
        writeReport(elements, samples, median, p95);
        assertTrue(median < 800, "median " + median + " ms");
        assertTrue(p95 < 2000, "P95 " + p95 + " ms");
    }

    private SnapshotDiff single() {
        assertEquals(1, this.published.size(), "expected exactly one published diff");
        return this.published.get(0);
    }

    private PartDefinition definition(String name) {
        PartDefinition definition = FACTORY.createPartDefinition();
        definition.setId("PD-" + ++this.counter);
        definition.setName(name);
        return definition;
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

    private Connection connection(Port source, Port target, ConnectionKind kind) {
        Connection connection = FACTORY.createConnection();
        connection.setId("CO-" + ++this.counter);
        connection.setName("c" + this.counter);
        connection.setSource(source);
        connection.setTarget(target);
        connection.setKind(kind);
        connection.setInterfaceType(source.getInterfaceType());
        return connection;
    }

    private static List<String> ids(List<DiagramSnapshot.Node> nodes) {
        return nodes.stream().map(DiagramSnapshot.Node::id).toList();
    }

    private static int count(Resource resource) {
        int count = 0;
        for (var iterator = resource.getAllContents(); iterator.hasNext(); iterator.next()) {
            count++;
        }
        return count;
    }

    private static double percentile(List<Double> samples, int percentile) {
        List<Double> sorted = samples.stream().sorted().toList();
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.size());
        return sorted.get(Math.max(0, rank - 1));
    }

    private static void writeReport(int elements, List<Double> samples, double median, double p95) throws IOException {
        Path report = Path.of(System.getProperty("sysarch.perfReport", "build/perf-refresh.json"));
        Files.createDirectories(report.toAbsolutePath().getParent());
        String json = String.format(Locale.ROOT, """
                {
                  "scenario": "IBD auto-sync, 20 consecutive Part additions",
                  "scope": "server side: model change to published diff (projection + diff)",
                  "elements": %d,
                  "additions": %d,
                  "medianMs": %.3f,
                  "p95Ms": %.3f,
                  "maxMs": %.3f,
                  "thresholds": { "medianMs": 800, "p95Ms": 2000 },
                  "pass": %s,
                  "samplesMs": [%s]
                }
                """, elements, samples.size(), median, p95, samples.stream().mapToDouble(Double::doubleValue).max().orElse(0), median < 800 && p95 < 2000,
                samples.stream().map(s -> String.format(Locale.ROOT, "%.3f", s)).collect(Collectors.joining(", ")));
        Files.writeString(report, json, StandardCharsets.UTF_8);
    }
}

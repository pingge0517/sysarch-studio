package com.example.sysarch.view.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.model.Layer;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.view.api.DiagramDescription;
import com.example.sysarch.view.generated.BddDiagramDescriptionProvider;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T4 acceptance (spec §8): the BDD generated from {@code views/sysarch-core.vsm.yaml} projects the fixed fixture
 * of 3 blocks and 2 generalizations to the expected nodes, edges and labels. Uses the real generated provider,
 * so the chain YAML, generated code, projection is under test.
 */
class BddSnapshotTest {

    private static final SysarchFactory FACTORY = SysarchFactory.eINSTANCE;

    private final DiagramDescription bdd = new BddDiagramDescriptionProvider().build();

    private Resource document;

    private PartDefinition vehicle;

    private PartDefinition car;

    @BeforeEach
    void fixture() {
        this.document = new ResourceImpl(URI.createURI("memory:/bdd-fixture.xmi"));
        this.vehicle = block("PD-1", "Vehicle", true);
        this.car = block("PD-2", "Car", false);
        PartDefinition truck = block("PD-3", "Truck", false);
        this.car.getSuperTypes().add(this.vehicle);
        truck.getSuperTypes().add(this.vehicle);
        this.document.getContents().addAll(List.of(this.vehicle, this.car, truck));
    }

    @Test
    void fixtureWithThreeBlocksAndTwoGeneralizations() {
        DiagramSnapshot snapshot = DiagramProjector.project(this.bdd, this.vehicle);

        assertEquals(3, snapshot.nodes().size());
        assertEquals(2, snapshot.edges().size());
        assertEquals(2, snapshot.edges("generalization").size());
        assertEquals("""
                node PD-1 [abstractBlock] "Vehicle"
                node PD-2 [block] "Car" / "LOGICAL"
                  ports: -
                node PD-3 [block] "Truck" / "LOGICAL"
                  ports: -
                edge PD-2 -> PD-1 [generalization] ""
                edge PD-3 -> PD-1 [generalization] ""
                """, snapshot.describe());
    }

    /** Generalization is drawn subtype to supertype; the YAML styles it as a hollow triangle (checked by vsm-compiler). */
    @Test
    void generalizationRunsFromSubtypeToSupertype() {
        DiagramSnapshot snapshot = DiagramProjector.project(this.bdd, this.car);
        for (DiagramSnapshot.Edge edge : snapshot.edges("generalization")) {
            assertEquals("PD-1", edge.targetId());
        }
    }

    /** T4 step 3: a new PartDefinition appears in the diagram without touching the diagram. */
    @Test
    void newPartDefinitionAppears() {
        PartDefinition bus = block("PD-4", "Bus", false);
        bus.getSuperTypes().add(this.vehicle);
        this.document.getContents().add(bus);

        DiagramSnapshot snapshot = DiagramProjector.project(this.bdd, this.vehicle);

        assertEquals("Bus", snapshot.node("PD-4").orElseThrow().label());
        assertEquals(3, snapshot.edges("generalization").size());
    }

    /** T4 step 3: ownedParts is drawn as a composition to the part's type, labelled with the part's name. */
    @Test
    void compositionRunsFromOwnerToPartTypeWithPartName() {
        PartDefinition engine = block("PD-5", "Engine", false);
        this.document.getContents().add(engine);
        Part enginePart = FACTORY.createPart();
        enginePart.setId("PA-1");
        enginePart.setName("engine");
        enginePart.setTypedBy(engine);
        this.car.getOwnedParts().add(enginePart);

        DiagramSnapshot snapshot = DiagramProjector.project(this.bdd, this.car);

        List<DiagramSnapshot.Edge> compositions = snapshot.edges("composition");
        assertEquals(1, compositions.size());
        DiagramSnapshot.Edge composition = compositions.get(0);
        assertEquals("PD-2", composition.sourceId());
        assertEquals("PD-5", composition.targetId());
        assertEquals("engine", composition.label());
        assertEquals("PA-1", composition.elementId());
        assertTrue(snapshot.node("PA-1").isEmpty(), "parts are not BDD nodes");
    }

    @Test
    void portsAreListedInTheBlockCompartment() {
        Port port = FACTORY.createPort();
        port.setId("PO-1");
        port.setName("can0");
        port.setInterfaceType(com.example.sysarch.TestInterfaces.named("CAN"));
        this.car.getOwnedPorts().add(port);

        DiagramSnapshot snapshot = DiagramProjector.project(this.bdd, this.car);

        DiagramSnapshot.Compartment ports = snapshot.node("PD-2").orElseThrow().compartments().get(0);
        assertEquals("ports", ports.label());
        assertEquals(List.of("can0 : CAN"), ports.items());
        assertTrue(snapshot.node("PO-1").isEmpty(), "ports are compartment items, not BDD nodes");
    }

    @Test
    void labelsFollowTheModel() {
        this.car.setName("Sedan");
        this.car.setLayer(Layer.PHYSICAL);

        DiagramSnapshot.Node node = DiagramProjector.project(this.bdd, this.vehicle).node("PD-2").orElseThrow();

        assertEquals("Sedan", node.label());
        assertEquals("PHYSICAL", node.subLabel());
    }

    /** T4 step 4: hideAbstract removes abstract blocks (and their edges) from the canvas; the model is unchanged. */
    @Test
    void hideAbstractFilterHidesAbstractBlocksWithoutChangingTheModel() {
        Collection<EObject> before = EcoreUtil.copyAll(this.document.getContents());

        DiagramSnapshot filtered = DiagramProjector.project(this.bdd, this.car, Set.of("hideAbstract"));

        assertEquals("""
                node PD-2 [block] "Car" / "LOGICAL"
                  ports: -
                node PD-3 [block] "Truck" / "LOGICAL"
                  ports: -
                """, filtered.describe());
        assertTrue(EcoreUtil.equals(List.copyOf(before), this.document.getContents()), "filtering must not change the model");
        assertTrue(this.vehicle.isIsAbstract());

        DiagramSnapshot unfiltered = DiagramProjector.project(this.bdd, this.car);
        assertFalse(unfiltered.node("PD-1").isEmpty(), "switching the filter off shows the block again");
    }

    @Test
    void projectionIsDeterministic() {
        assertEquals(DiagramProjector.project(this.bdd, this.vehicle), DiagramProjector.project(this.bdd, this.vehicle));
    }

    @Test
    void unknownFilterIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DiagramProjector.project(this.bdd, this.vehicle, Set.of("hideAbstrct")));
    }

    @Test
    void rootMustHaveTheDiagramType() {
        Part part = FACTORY.createPart();
        this.document.getContents().add(part);
        assertThrows(IllegalArgumentException.class, () -> DiagramProjector.project(this.bdd, part));
    }

    private static PartDefinition block(String id, String name, boolean isAbstract) {
        PartDefinition block = FACTORY.createPartDefinition();
        block.setId(id);
        block.setName(name);
        block.setIsAbstract(isAbstract);
        return block;
    }
}

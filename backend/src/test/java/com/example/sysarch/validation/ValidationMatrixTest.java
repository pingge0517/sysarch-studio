package com.example.sysarch.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.model.Allocate;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.ConnectionKind;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.PortDirection;
import com.example.sysarch.model.SysarchFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * T6 acceptance (spec §8): the six cases of the validation matrix, evaluated by Eclipse OCL from the Ecore's
 * invariants, plus the error shape, the edge-drop path and the save path.
 */
class ValidationMatrixTest {

    private static final SysarchFactory FACTORY = SysarchFactory.eINSTANCE;

    private static ConstraintValidator validator;

    private int counter;

    @BeforeAll
    static void loadOcl() {
        validator = new ConstraintValidator();
    }

    @AfterAll
    static void disposeOcl() {
        validator.close();
    }

    // ---- The six cases of spec T6 step 5 ----

    @Test
    void case1SameInterfaceTypeIsAccepted() {
        assertEquals(List.of(), validator.validateEdgeDrop(this.port("CAN", PortDirection.OUT), this.port("CAN", PortDirection.IN), ConnectionKind.ASSEMBLY));
    }

    @Test
    void case2DifferentInterfaceTypeIsRejectedAndBothPortsAreHighlighted() {
        Port can = this.port("CAN", PortDirection.OUT);
        Port eth = this.port("ETH", PortDirection.IN);

        List<ValidationError> errors = validator.validateEdgeDrop(can, eth, ConnectionKind.ASSEMBLY);

        assertEquals(List.of("PortInterfaceAssignable"), codes(errors));
        ValidationError error = errors.get(0);
        assertEquals(List.of(can.getId(), eth.getId()), error.anchors());
        assertEquals("端口接口不可赋值：「CAN」与「ETH」不在同一继承链上", error.message());
    }

    @Test
    void case3InToInIsRejected() {
        List<ValidationError> errors = validator.validateEdgeDrop(this.port("CAN", PortDirection.IN), this.port("CAN", PortDirection.IN), ConnectionKind.ASSEMBLY);

        assertEquals(List.of("DirectionCompatible"), codes(errors));
        assertEquals("端口方向不兼容：IN → IN", errors.get(0).message());
    }

    @Test
    void case4InoutIsAccepted() {
        assertEquals(List.of(), validator.validateEdgeDrop(this.port("CAN", PortDirection.IN), this.port("CAN", PortDirection.INOUT), ConnectionKind.ASSEMBLY));
        assertEquals(List.of(), validator.validateEdgeDrop(this.port("CAN", PortDirection.INOUT), this.port("CAN", PortDirection.INOUT), ConnectionKind.ASSEMBLY));
    }

    @Test
    void case5SelfLoopIsRejected() {
        Port port = this.port("CAN", PortDirection.INOUT);

        List<ValidationError> errors = validator.validateEdgeDrop(port, port, ConnectionKind.ASSEMBLY);

        assertEquals(List.of("NoSelfLoop"), codes(errors));
        assertEquals(List.of(port.getId()), errors.get(0).anchors());
    }

    @Test
    void case6AllocationSkippingALayerIsRejected() {
        Allocate allocate = this.allocate(Layer.LOGICAL, Layer.EPBS);

        List<ValidationError> errors = validator.validate(allocate);

        assertEquals(List.of("LayerAdjacentOnly"), codes(errors));
        assertEquals("跨层跳过：LA → EPBS（跳过 PA）", errors.get(0).message());
        assertEquals(List.of(allocate.getFrom().getId(), allocate.getTo().getId()), errors.get(0).anchors());
    }

    // ---- Around the matrix ----

    @Test
    void adjacentAllocationsAreAccepted() {
        Layer[] layers = Layer.values();
        for (int i = 0; i + 1 < layers.length; i++) {
            assertEquals(List.of(), validator.validate(this.allocate(layers[i], layers[i + 1])), layers[i] + " -> " + layers[i + 1]);
        }
    }

    @Test
    void allocationWithinOrAgainstTheLayerOrderIsRejected() {
        assertEquals("分配只能指向相邻的下一层：LA → LA", validator.validate(this.allocate(Layer.LOGICAL, Layer.LOGICAL)).get(0).message());
        assertEquals("分配只能指向相邻的下一层：PA → LA", validator.validate(this.allocate(Layer.PHYSICAL, Layer.LOGICAL)).get(0).message());
    }

    /** Only ASSEMBLY connections require matching types and directions (the Ecore uses "kind = ASSEMBLY implies"). */
    @Test
    void delegationDoesNotRequireMatchingTypesOrDirections() {
        assertEquals(List.of(), validator.validateEdgeDrop(this.port("CAN", PortDirection.IN), this.port("ETH", PortDirection.IN), ConnectionKind.DELEGATION));
    }

    /** Unrelated interfaces are not assignable, even with similar names (P1: entities, not strings; inheritance is in InterfaceCompatibilityTest). */
    @Test
    void unrelatedInterfacesAreNotAssignable() {
        assertEquals(List.of("PortInterfaceAssignable"), codes(validator.validateEdgeDrop(this.port("CAN", PortDirection.OUT),
                this.port("CAN-FD", PortDirection.IN), ConnectionKind.ASSEMBLY)));
    }

    @Test
    void edgeDropCheckDoesNotTouchTheModel() {
        Resource model = new ResourceImpl(URI.createURI("memory:/model.xmi"));
        PartDefinition block = this.named(FACTORY.createPartDefinition(), "Block");
        Port can = this.port("CAN", PortDirection.OUT);
        Port eth = this.port("ETH", PortDirection.IN);
        block.getOwnedPorts().addAll(List.of(can, eth));
        model.getContents().add(block);
        Collection<EObject> before = EcoreUtil.copyAll(model.getContents());

        validator.validateEdgeDrop(can, eth, ConnectionKind.ASSEMBLY);

        assertTrue(EcoreUtil.equals(List.copyOf(before), model.getContents()));
        assertEquals(1, model.getContents().size(), "no connection was stored");
    }

    @Test
    void saveChecksNamesAndPortInterfaceTypes() {
        PartDefinition unnamed = FACTORY.createPartDefinition();
        unnamed.setId("PD-x");
        Port untyped = this.port(null, PortDirection.IN);
        Port inout = this.port(null, PortDirection.INOUT);

        List<ValidationError> errors = validator.validateAll(List.of(unnamed, untyped, inout));

        assertEquals(List.of("NameNotEmpty", "InterfaceTypeRequired"), codes(errors));
        assertEquals("PD-x", errors.get(0).elementId());
        assertEquals(List.of("PD-x"), errors.get(0).anchors());
        assertEquals(List.of(untyped.getId()), errors.get(1).anchors());
    }

    @Test
    void savedConnectionIsCheckedWithAllItsConstraints() {
        Connection connection = this.named(FACTORY.createConnection(), "bus");
        connection.setSource(this.port("CAN", PortDirection.IN));
        connection.setTarget(this.port("ETH", PortDirection.IN));

        assertEquals(List.of("PortInterfaceAssignable", "DirectionCompatible"), codes(validator.validate(connection)));
    }

    @Test
    void allocationToItselfIsRejected() {
        PartDefinition block = this.named(FACTORY.createPartDefinition(), "Block");
        Allocate allocate = this.named(FACTORY.createAllocate(), "self");
        allocate.setFrom(block);
        allocate.setTo(block);

        assertTrue(codes(validator.validate(allocate)).contains("NoSelfAllocate"));
    }

    /** The invariants come from the Ecore annotations, not from Java: all eight are loaded (P0 text says six; P1 adds ProtocolRequired). */
    @Test
    void invariantsAreLoadedFromTheEcore() {
        assertEquals(Map.of(
                "ArchElement", List.of("NameNotEmpty"),
                "Port", List.of("InterfaceTypeRequired"),
                "Connection", List.of("PortInterfaceAssignable", "NoSelfLoop", "DirectionCompatible"),
                "Allocate", List.of("LayerAdjacentOnly", "NoSelfAllocate"),
                "InterfaceDef", List.of("ProtocolRequired"),
                "ConstraintBlock", List.of("HasExpression", "ParamsHaveUnit", "NoDuplicateParamName"), // P3 T1–T2, metamodel v1.3
                "Parameter", List.of("DirectionKnown"),
                "StateMachine", List.of("SingleInitial"), // P3 T2
                "Transition", List.of("GuardNotEmpty")), validator.constraintNames());
    }

    private Port port(String interfaceType, PortDirection direction) {
        Port port = this.named(FACTORY.createPort(), "p" + (this.counter + 1));
        port.setInterfaceType(com.example.sysarch.TestInterfaces.named(interfaceType));
        port.setDirection(direction);
        return port;
    }

    private Allocate allocate(Layer fromLayer, Layer toLayer) {
        PartDefinition from = this.named(FACTORY.createPartDefinition(), "from");
        from.setLayer(fromLayer);
        PartDefinition to = this.named(FACTORY.createPartDefinition(), "to");
        to.setLayer(toLayer);
        Allocate allocate = this.named(FACTORY.createAllocate(), "alloc");
        allocate.setFrom(from);
        allocate.setTo(to);
        return allocate;
    }

    private <T extends ArchElement> T named(T element, String name) {
        element.setId("E-" + ++this.counter);
        element.setName(name);
        return element;
    }

    private static List<String> codes(List<ValidationError> errors) {
        return errors.stream().map(ValidationError::code).toList();
    }
}

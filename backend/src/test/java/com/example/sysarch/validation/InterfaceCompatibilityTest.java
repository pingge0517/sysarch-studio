package com.example.sysarch.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.model.ConnectionKind;
import com.example.sysarch.model.InterfaceDef;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.PortDirection;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.model.SysarchPackage;

import java.util.List;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.impl.EValidatorRegistryImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * P1 T2: interface compatibility. {@code PortInterfaceAssignable} replaces P0's {@code PortTypeCompatible}: on an
 * ASSEMBLY connection both ports need an interface, and the two must be the same entity or one an ancestor of the
 * other ({@code superTypes}, transitively). A port without an interface is never assignable (user's decision).
 */
class InterfaceCompatibilityTest {

    private static final SysarchFactory FACTORY = SysarchFactory.eINSTANCE;

    private static ConstraintValidator validator;

    private Resource model;

    private InterfaceDef can;

    private InterfaceDef canFd;

    private InterfaceDef canFd500k;

    private InterfaceDef ethernet;

    @BeforeAll
    static void loadOcl() {
        validator = new ConstraintValidator();
    }

    @AfterAll
    static void disposeOcl() {
        validator.close();
    }

    /** CAN ← CAN-FD ← CAN-FD-500k (child to parent), and an unrelated Ethernet. */
    @BeforeEach
    void interfaces() {
        this.model = new ResourceImpl(URI.createURI("memory:/interfaces.xmi"));
        this.can = this.interfaceDef("CAN");
        this.canFd = this.interfaceDef("CAN-FD");
        this.canFd.getSuperTypes().add(this.can);
        this.canFd500k = this.interfaceDef("CAN-FD-500k");
        this.canFd500k.getSuperTypes().add(this.canFd);
        this.ethernet = this.interfaceDef("Ethernet");
    }

    // =========================================================== the five cases of spec P1 T2

    @Test
    void theSameEntityIsAssignable() {
        assertEquals(List.of(), this.drop(this.can, this.can));
    }

    @Test
    void aSubInterfaceIsAssignableToItsAncestors() {
        assertEquals(List.of(), this.drop(this.canFd, this.can));
        assertEquals(List.of(), this.drop(this.canFd500k, this.can), "transitively");
    }

    @Test
    void aSuperInterfaceIsAssignableToItsDescendants() {
        assertEquals(List.of(), this.drop(this.can, this.canFd500k), "either side may be the ancestor");
    }

    @Test
    void unrelatedInterfacesAreRejected() {
        List<ValidationError> errors = this.drop(this.can, this.ethernet);

        assertEquals(List.of("PortInterfaceAssignable"), codes(errors));
        assertEquals("端口接口不可赋值：「CAN」与「Ethernet」不在同一继承链上", errors.get(0).message());
        assertEquals(List.of(), this.drop(this.canFd, this.canFd500k), "parent and child are assignable");
    }

    @Test
    void aPortWithoutInterfaceIsNeverAssignable() {
        List<ValidationError> oneSide = this.drop(this.can, null);

        assertEquals(List.of("PortInterfaceAssignable"), codes(oneSide));
        assertEquals("端口接口不可赋值：目标端口未设置接口，请先给端口设置接口", oneSide.get(0).message());
        assertEquals(List.of("PortInterfaceAssignable"), codes(this.drop(null, null)), "both empty is rejected too");
    }

    // =========================================================== beyond the matrix

    /** The old string rule is gone: one rule, one error (spec: "删除旧约束，避免双重报错"). */
    @Test
    void theP0StringRuleIsRemoved() {
        assertFalse(validator.constraintNames().getOrDefault("Connection", List.of()).contains("PortTypeCompatible"));
        assertTrue(validator.constraintNames().get("Connection").contains("PortInterfaceAssignable"));
    }

    /** Results are cached per pair; changing the inheritance in the model must not leave a stale answer. */
    @Test
    void changingTheInheritanceInvalidatesTheCache() {
        assertEquals(List.of("PortInterfaceAssignable"), codes(this.drop(this.ethernet, this.can)));

        this.ethernet.getSuperTypes().add(this.can);

        assertEquals(List.of(), this.drop(this.ethernet, this.can));
    }

    @Test
    void cyclicInheritanceDoesNotLoop() {
        this.can.getSuperTypes().add(this.canFd500k); // CAN → CAN-FD-500k → CAN-FD → CAN

        assertEquals(List.of(), this.drop(this.can, this.canFd));
        assertEquals(List.of("PortInterfaceAssignable"), codes(this.drop(this.can, this.ethernet)));
    }

    /** Only ASSEMBLY connections are typed, as in P0. */
    @Test
    void delegationIsNotChecked() {
        assertEquals(List.of(), validator.validateEdgeDrop(this.port(this.can, PortDirection.IN), this.port(this.ethernet, PortDirection.IN),
                ConnectionKind.DELEGATION));
    }

    /** ProtocolRequired (spec P1 §2.2) is marked on the Protocol field, not vetoed: the migration creates entities without one. */
    @Test
    void anInterfaceWithoutProtocolIsMarkedOnTheProtocolField() {
        this.can.setProtocol(null);
        EValidator.Registry registry = new EValidatorRegistryImpl();
        registry.put(SysarchPackage.eINSTANCE, new SysarchEValidator(validator, new VetoNotices()));

        Diagnostic diagnostic = new Diagnostician(registry).validate(this.can);

        Diagnostic missing = diagnostic.getChildren().stream().filter(d -> d.getMessage().equals("接口「CAN」未设置协议")).findFirst()
                .orElseThrow(() -> new AssertionError(diagnostic.getChildren().toString()));
        assertEquals(List.of(this.can, SysarchPackage.eINSTANCE.getInterfaceDef_Protocol()), missing.getData());
        assertFalse(ChangeGuard.VETOED.contains("ProtocolRequired"));
    }

    // =========================================================== helpers

    private List<ValidationError> drop(InterfaceDef source, InterfaceDef target) {
        return validator.validateEdgeDrop(this.port(source, PortDirection.OUT), this.port(target, PortDirection.IN), ConnectionKind.ASSEMBLY);
    }

    private InterfaceDef interfaceDef(String name) {
        InterfaceDef interfaceDef = FACTORY.createInterfaceDef();
        interfaceDef.setId("IF-" + name);
        interfaceDef.setName(name);
        interfaceDef.setProtocol(name);
        this.model.getContents().add(interfaceDef);
        return interfaceDef;
    }

    private Port port(InterfaceDef interfaceDef, PortDirection direction) {
        Port port = FACTORY.createPort();
        port.setName("p");
        port.setDirection(direction);
        port.setInterfaceType(interfaceDef);
        return port;
    }

    private static List<String> codes(List<ValidationError> errors) {
        return errors.stream().map(ValidationError::code).toList();
    }
}

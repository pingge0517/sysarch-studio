package com.example.sysarch.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.model.Allocate;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.PortDirection;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.model.SysarchPackage;

import java.util.List;
import java.util.function.Consumer;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.eclipse.emf.ecore.impl.EValidatorRegistryImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T6 step 2, save path (decision "按约束分开处理"): Allocate and connection rules are vetoed by rolling the edit back;
 * name and interface type are only marked, through the EMF validator Sirius' properties view uses.
 */
class SaveTimeValidationTest {

    private static final SysarchFactory FACTORY = SysarchFactory.eINSTANCE;

    private static ConstraintValidator constraints;

    private ResourceSet resourceSet;

    private Resource model;

    private PartDefinition logical;

    private PartDefinition physical;

    private Allocate allocate;

    private int counter;

    private final VetoNotices notices = new VetoNotices();

    @BeforeAll
    static void loadOcl() {
        constraints = new ConstraintValidator();
    }

    @AfterAll
    static void disposeOcl() {
        constraints.close();
    }

    @BeforeEach
    void fixture() {
        this.resourceSet = new ResourceSetImpl();
        this.model = new ResourceImpl(URI.createURI("memory:/model.xmi"));
        this.resourceSet.getResources().add(this.model);
        this.logical = this.block("Controller", Layer.LOGICAL);
        this.physical = this.block("ECU", Layer.PHYSICAL);
        this.allocate = this.named(FACTORY.createAllocate(), "ctrl-to-ecu");
        this.allocate.setFrom(this.logical);
        this.allocate.setTo(this.physical);
        this.model.getContents().addAll(List.of(this.logical, this.physical, this.allocate));
    }

    // ---- veto (rolled back) ----

    @Test
    void retargetingAnAllocationAcrossTwoLayersIsRolledBack() {
        PartDefinition product = this.block("Board", Layer.EPBS);
        this.model.getContents().add(product);

        List<ValidationError> rejected = this.edit(m -> this.allocate.setTo(product));

        assertEquals(List.of("LayerAdjacentOnly"), codes(rejected));
        assertEquals("跨层跳过：LA → EPBS", rejected.get(0).message());
        assertEquals(this.physical, this.allocate.getTo(), "the edit was rolled back");
    }

    /** The edit changes a block, but it is the Allocate pointing at it that breaks: referrers are checked too. */
    @Test
    void changingTheLayerOfAnAllocatedBlockIsRolledBack() {
        List<ValidationError> rejected = this.edit(m -> this.physical.setLayer(Layer.EPBS));

        assertEquals(List.of("LayerAdjacentOnly"), codes(rejected));
        assertEquals(Layer.PHYSICAL, this.physical.getLayer());
    }

    @Test
    void creatingAnInvalidAllocationIsRolledBack() {
        List<ValidationError> rejected = this.edit(m -> {
            Allocate self = this.named(FACTORY.createAllocate(), "self");
            self.setFrom(this.logical);
            self.setTo(this.logical);
            this.model.getContents().add(self);
        });

        assertTrue(codes(rejected).contains("NoSelfAllocate"));
        assertEquals(3, this.model.getContents().size(), "the new Allocate is gone again");
    }

    /**
     * Found in the live check: the explorer creates an Allocate with empty ends, which the OCL invariants reject
     * (null layers, null = null). Incomplete is not wrong, so creating it and filling one end must be kept.
     */
    @Test
    void anAllocationCanBeCreatedEmptyAndFilledInStepByStep() {
        Allocate fresh = FACTORY.createAllocate();

        assertEquals(List.of(), this.edit(m -> this.model.getContents().add(fresh)), "an empty new Allocate is kept");
        assertTrue(this.model.getContents().contains(fresh));
        assertEquals(List.of(), this.edit(m -> fresh.setFrom(this.logical)), "one end set is still incomplete");
        assertEquals(this.logical, fresh.getFrom());

        PartDefinition sameLayer = this.block("Other controller", Layer.LOGICAL);
        this.model.getContents().add(sameLayer);
        List<ValidationError> rejected = this.edit(m -> fresh.setTo(sameLayer));

        assertEquals(List.of("LayerAdjacentOnly"), codes(rejected), "complete and wrong: vetoed");
        assertEquals(null, fresh.getTo());
        assertEquals(List.of(), this.edit(m -> fresh.setTo(this.physical)), "complete and right: kept");
    }

    @Test
    void reconnectingToAnIncompatiblePortOutsideTheCanvasIsRolledBack() {
        Port can = this.port("can0", "CAN", PortDirection.OUT);
        Port can2 = this.port("can1", "CAN", PortDirection.IN);
        Port eth = this.port("eth0", "ETH", PortDirection.IN);
        this.logical.getOwnedPorts().addAll(List.of(can, can2, eth));
        Connection connection = this.named(FACTORY.createConnection(), "bus");
        connection.setSource(can);
        connection.setTarget(can2);
        this.model.getContents().add(connection);

        List<ValidationError> rejected = this.edit(m -> connection.setTarget(eth));

        assertEquals(List.of("PortTypeCompatible"), codes(rejected));
        assertEquals(can2, connection.getTarget());
    }

    // ---- kept ----

    @Test
    void validEditsAreKept() {
        PartDefinition other = this.block("Other ECU", Layer.PHYSICAL);
        this.model.getContents().add(other);

        assertEquals(List.of(), this.edit(m -> this.allocate.setTo(other)));
        assertEquals(other, this.allocate.getTo());
    }

    /** Old bad data must not block unrelated edits: only violations the edit introduces are vetoed. */
    @Test
    void preExistingViolationsDoNotBlockOtherEdits() {
        PartDefinition product = this.block("Board", Layer.EPBS);
        this.model.getContents().add(product);
        this.allocate.setTo(product); // already broken before recording starts

        assertEquals(List.of(), this.edit(m -> this.allocate.setRationale("legacy, to be cleaned up")));
        assertEquals("legacy, to be cleaned up", this.allocate.getRationale());
    }

    /** Fields being typed in are marked, not vetoed. */
    @Test
    void emptyNameAndMissingInterfaceTypeAreKept() {
        Port port = this.port("p", null, PortDirection.INOUT);
        this.logical.getOwnedPorts().add(port);

        assertEquals(List.of(), this.edit(m -> {
            this.logical.setName("");
            port.setDirection(PortDirection.IN);
        }));
        assertEquals("", this.logical.getName());
        assertEquals(PortDirection.IN, port.getDirection());
    }

    // ---- marked (Sirius properties view / Validation view) ----

    @Test
    void emfValidatorMarksTheNameField() {
        this.logical.setName("");

        Diagnostic diagnostic = this.diagnostician().validate(this.logical);

        Diagnostic name = this.child(diagnostic, "名称不能为空");
        assertEquals(Diagnostic.ERROR, name.getSeverity());
        assertEquals(SysarchEValidator.SOURCE, name.getSource());
        assertEquals(List.of(this.logical, SysarchPackage.eINSTANCE.getArchElement_Name()), name.getData());
    }

    @Test
    void emfValidatorMarksTheInterfaceTypeField() {
        Port port = this.port("p", null, PortDirection.IN);
        this.logical.getOwnedPorts().add(port);

        Diagnostic diagnostic = this.diagnostician().validate(port);

        Diagnostic missing = this.child(diagnostic, "端口「p」未设置接口类型（仅 INOUT 端口可以不设）");
        assertTrue(missing.getData().contains(SysarchPackage.eINSTANCE.getPort_InterfaceType()));
    }

    @Test
    void emfValidatorKeepsEmfsBasicChecks() {
        var part = this.named(FACTORY.createPart(), "untyped");
        this.logical.getOwnedParts().add(part);

        Diagnostic diagnostic = this.diagnostician().validate(part);

        assertTrue(diagnostic.getChildren().stream().anyMatch(d -> d.getMessage().contains("typedBy")),
                "EMF's required-feature check still reports the missing type: " + diagnostic.getChildren());
    }

    @Test
    void validElementsHaveNoDiagnostics() {
        assertEquals(Diagnostic.OK, this.diagnostician().validate(this.allocate).getSeverity());
    }

    // ---- notice of a vetoed edit (the handler has already answered, so it is shown on the edited field) ----

    /** The vetoed edit is rolled back, and the field the user edited says why until the next edit. */
    @Test
    void aVetoedEditIsExplainedOnTheEditedField() {
        List<ValidationError> rejected = this.editWithNotice(m -> this.physical.setLayer(Layer.EPBS)); // breaks the Allocate LA -> PA

        assertEquals(List.of("LayerAdjacentOnly"), codes(rejected));
        assertEquals(Layer.PHYSICAL, this.physical.getLayer(), "rolled back");
        Diagnostic notice = this.child(this.diagnostician().validate(this.physical), "修改未保存：跨层跳过：LA → EPBS");
        assertEquals(Diagnostic.ERROR, notice.getSeverity());
        assertEquals(List.of(this.physical, SysarchPackage.eINSTANCE.getArchElement_Layer()), notice.getData());
        assertEquals(Diagnostic.OK, this.diagnostician().validate(this.logical).getSeverity(), "only the edited element carries the notice");
    }

    @Test
    void theNoticeClearsOnTheNextEdit() {
        this.editWithNotice(m -> this.physical.setLayer(Layer.EPBS));

        this.editWithNotice(m -> this.logical.setName("Main controller"));

        assertEquals(Diagnostic.OK, this.diagnostician().validate(this.physical).getSeverity());
    }

    @Test
    void aKeptEditLeavesNoNotice() {
        this.editWithNotice(m -> this.physical.setName("ECU 2"));

        assertEquals(Diagnostic.OK, this.diagnostician().validate(this.physical).getSeverity());
    }

    // ---- helpers ----

    /** Records {@code edit} the way the Sirius pipeline does, then lets the guard decide. */
    private List<ValidationError> edit(Consumer<Resource> edit) {
        ChangeRecorder recorder = new ChangeRecorder(this.resourceSet);
        edit.accept(this.model);
        var change = recorder.endRecording();
        recorder.dispose();
        return new ChangeGuard(constraints).check(this.resourceSet, change);
    }

    /** Like {@link #edit}, and then what ConstraintEnforcement.Guard does next: record the notice of a veto. */
    private List<ValidationError> editWithNotice(Consumer<Resource> edit) {
        ChangeRecorder recorder = new ChangeRecorder(this.resourceSet);
        edit.accept(this.model);
        var change = recorder.endRecording();
        recorder.dispose();
        var edited = ChangeGuard.edited(change);
        List<ValidationError> rejected = new ChangeGuard(constraints).check(this.resourceSet, change);
        this.notices.record(this.resourceSet, edited, rejected);
        return rejected;
    }

    private Diagnostician diagnostician() {
        EValidator.Registry registry = new EValidatorRegistryImpl();
        registry.put(SysarchPackage.eINSTANCE, new SysarchEValidator(constraints, this.notices));
        return new Diagnostician(registry);
    }

    private Diagnostic child(Diagnostic diagnostic, String message) {
        return diagnostic.getChildren().stream().filter(d -> d.getMessage().equals(message)).findFirst()
                .orElseThrow(() -> new AssertionError("no diagnostic '" + message + "' in " + diagnostic.getChildren()));
    }

    private PartDefinition block(String name, Layer layer) {
        PartDefinition block = this.named(FACTORY.createPartDefinition(), name);
        block.setLayer(layer);
        return block;
    }

    private Port port(String name, String interfaceType, PortDirection direction) {
        Port port = this.named(FACTORY.createPort(), name);
        port.setInterfaceType(com.example.sysarch.TestInterfaces.named(interfaceType));
        port.setDirection(direction);
        return port;
    }

    private <T extends com.example.sysarch.model.ArchElement> T named(T element, String name) {
        element.setId("E-" + ++this.counter);
        element.setName(name);
        return element;
    }

    private static List<String> codes(List<ValidationError> errors) {
        return errors.stream().map(ValidationError::code).toList();
    }
}

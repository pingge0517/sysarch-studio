package com.example.sysarch.view.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.model.Allocate;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.view.api.Axis;
import com.example.sysarch.view.api.CellCondition;
import com.example.sysarch.view.api.TableAction;
import com.example.sysarch.view.api.TableDescription;
import com.example.sysarch.view.generated.AllocationMatrixTableDescriptionProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * P1 T4 acceptance (P1-AC-2): the allocation matrix of the generated description, its cells and actions. A link that
 * skips a layer is refused with "跳过 LA", a link within one layer is refused, and a refused link changes nothing.
 */
class AllocationMatrixTest {

    private static final SysarchFactory FACTORY = SysarchFactory.eINSTANCE;

    private static final ConstraintValidator VALIDATOR = new ConstraintValidator();

    private final TableDescription table = new AllocationMatrixTableDescriptionProvider().build();

    private Resource model;

    private PartDefinition braking;

    private PartDefinition controller;

    private PartDefinition sensor;

    private PartDefinition ecu;

    @AfterAll
    static void disposeOcl() {
        VALIDATOR.close();
    }

    @BeforeEach
    void fixture() {
        this.model = new ResourceImpl(URI.createURI("memory:/matrix.xmi"));
        this.braking = this.block("PD-SA", "制动功能", Layer.SYSTEM);
        this.controller = this.block("PD-LA1", "制动控制器", Layer.LOGICAL);
        this.sensor = this.block("PD-LA2", "轮速传感", Layer.LOGICAL);
        this.ecu = this.block("PD-PA", "制动 ECU", Layer.PHYSICAL);
    }

    // =========================================================== projection

    @Test
    void rowsAreSystemAndLogicalColumnsLogicalAndPhysicalGroupedByLayer() {
        Matrix matrix = this.matrix();

        assertEquals(List.of("制动功能:SYSTEM", "制动控制器:LOGICAL", "轮速传感:LOGICAL"), headers(matrix.rows()));
        assertEquals(List.of("制动控制器:LOGICAL", "轮速传感:LOGICAL", "制动 ECU:PHYSICAL"), headers(matrix.columns()));
        assertEquals(List.of("SA 系统分析", "LA 逻辑架构", "PA 物理架构"), matrix.groups().stream().map(Matrix.Group::label).toList());
        assertEquals("●", matrix.markSymbol());
        assertEquals("#1C3D2E", matrix.markColor());
        assertEquals(List.of(), matrix.cells());
    }

    /** Groups are by layer order, whatever the document order. */
    @Test
    void headersAreOrderedByLayerThenDocumentOrder() {
        this.model.getContents().move(0, this.sensor);

        assertEquals(List.of("制动功能:SYSTEM", "轮速传感:LOGICAL", "制动控制器:LOGICAL"), headers(this.matrix().rows()));
    }

    /** User's decision: an ArchElement axis holds blocks and parts only, not ports, requirements, interfaces or the allocations. */
    @Test
    void onlyStructuralElementsAreRowsAndColumns() {
        Requirement requirement = FACTORY.createRequirement();
        requirement.setName("需求");
        requirement.setLayer(Layer.LOGICAL);
        this.model.getContents().add(requirement);
        var port = FACTORY.createPort();
        port.setName("p");
        this.controller.getOwnedPorts().add(port);
        var part = FACTORY.createPart();
        part.setId("PT-1");
        part.setName("左前轮速");
        part.setTypedBy(this.sensor);
        this.controller.getOwnedParts().add(part);
        this.allocate(this.controller, this.ecu);

        Matrix matrix = this.matrix();

        assertEquals(List.of("制动功能", "制动控制器", "左前轮速", "轮速传感"), matrix.rows().stream().map(Matrix.Header::label).toList(),
                "a Part is allocatable; ports, requirements and Allocates are not");
    }

    @Test
    void anAllocationMarksItsCell() {
        this.allocate(this.controller, this.ecu);

        assertEquals(List.of(new Matrix.Cell("PD-LA1", "PD-PA")), this.matrix().cells());
    }

    /** On the demo: 4 logical blocks are rows and columns, the 2 ECUs columns, and the 3 allocations are marked. */
    @Test
    void theDemo() {
        Resource demo = new ResourceImpl(URI.createURI("memory:/demo.xmi"));
        demo.getContents().addAll(DomainControllerDemo.build());

        Matrix matrix = MatrixProjector.project(this.table, List.of(demo));

        assertEquals(List.of("域控制器", "感知融合", "决策规划", "执行控制", "感知融合单元", "决策规划单元", "执行控制单元"),
                matrix.rows().stream().map(Matrix.Header::label).toList(), "blocks and their parts, all LOGICAL, in document order");
        assertEquals(Set.of(new Matrix.Cell("PD-002", "PD-005"), new Matrix.Cell("PD-003", "PD-005"), new Matrix.Cell("PD-004", "PD-006")),
                Set.copyOf(matrix.cells()));
    }

    // =========================================================== actions (P1-AC-2)

    @Test
    void anAdjacentAllocationIsCreatedFromTheRowToTheColumn() {
        TableActions.Outcome outcome = this.act("createAllocate", "PD-SA", "PD-LA1");

        assertTrue(outcome.changed());
        assertEquals(List.of(), outcome.errors());
        Allocate allocate = (Allocate) this.model.getContents().get(this.model.getContents().size() - 1);
        assertSame(this.braking, allocate.getFrom());
        assertSame(this.controller, allocate.getTo());
        assertEquals("制动功能 → 制动控制器", allocate.getName());
        assertEquals(List.of(new Matrix.Cell("PD-SA", "PD-LA1")), this.matrix().cells());
    }

    /** "跨两层建分配被拒并提示'跳过 LA'". */
    @Test
    void skippingALayerIsRefusedAndNamesTheSkippedLayer() {
        List<Object> before = new ArrayList<>(EcoreUtil.copyAll(this.model.getContents()));

        TableActions.Outcome outcome = this.act("createAllocate", "PD-SA", "PD-PA");

        assertFalse(outcome.changed());
        assertEquals(List.of("LayerAdjacentOnly"), outcome.errors().stream().map(e -> e.code()).toList());
        assertEquals("跨层跳过：SA → PA（跳过 LA）", outcome.errors().get(0).message());
        assertTrue(outcome.errors().get(0).message().contains("跳过 LA"));
        assertEquals(List.of("PD-SA", "PD-PA"), outcome.errors().get(0).anchors());
        assertTrue(EcoreUtil.equals(before.stream().map(o -> (org.eclipse.emf.ecore.EObject) o).toList(), this.model.getContents()),
                "nothing was added");
        assertEquals(List.of(), this.matrix().cells());
    }

    /** "同层建分配被拒". */
    @Test
    void anAllocationWithinOneLayerIsRefused() {
        TableActions.Outcome outcome = this.act("createAllocate", "PD-LA1", "PD-LA2");

        assertFalse(outcome.changed());
        assertEquals("分配只能指向相邻的下一层：LA → LA", outcome.errors().get(0).message());
        assertEquals(4, this.model.getContents().size());
    }

    @Test
    void allocatingAnElementToItselfIsRefused() {
        TableActions.Outcome outcome = this.act("createAllocate", "PD-LA1", "PD-LA1");

        assertTrue(outcome.errors().stream().anyMatch(e -> e.code().equals("NoSelfAllocate")));
        assertEquals(4, this.model.getContents().size());
    }

    @Test
    void aSecondAllocationOfTheSameCellIsRefused() {
        this.act("createAllocate", "PD-LA1", "PD-PA");

        TableActions.Outcome again = this.act("createAllocate", "PD-LA1", "PD-PA");

        assertEquals(List.of("ALREADY_LINKED"), again.errors().stream().map(e -> e.code()).toList());
        assertEquals(5, this.model.getContents().size());
    }

    @Test
    void deleteRemovesTheAllocationsOfTheCell() {
        this.allocate(this.controller, this.ecu);
        this.allocate(this.controller, this.ecu);

        TableActions.Outcome outcome = this.act("deleteAllocate", "PD-LA1", "PD-PA");

        assertTrue(outcome.changed());
        assertEquals(List.of(), this.matrix().cells());
        assertEquals(4, this.model.getContents().size());
    }

    @Test
    void unknownRowsColumnsAndActionsAreErrors() {
        assertThrows(IllegalArgumentException.class, () -> this.act("createAllocate", "nope", "PD-PA"));
        assertThrows(IllegalArgumentException.class, () -> this.act("createAllocate", "PD-PA", "PD-PA"), "a PHYSICAL block is no row");
        assertThrows(IllegalArgumentException.class, () -> this.act("explode", "PD-LA1", "PD-PA"));
    }

    // =========================================================== self mode and link (requirement trace, T5)

    @Test
    void inSelfModeTheRowIsTheElementAndLinkAddsTheColumn() {
        Requirement requirement = FACTORY.createRequirement();
        requirement.setId("RQ-1");
        requirement.setReqId("R-1");
        requirement.setName("制动距离");
        this.model.getContents().add(requirement);
        TableDescription trace = TableDescription.newTableDescription("trace").domainType("sysarch::Requirement")
                .rows(Axis.of("sysarch::Requirement", "{reqId} {name}"))
                .columns(Axis.of("sysarch::PartDefinition", "{name}").filter("layer == 'LOGICAL'"))
                .cells(CellCondition.or(CellCondition.includes("satisfiedBy", CellCondition.Side.COLUMN),
                        CellCondition.includes("verifiedBy", CellCondition.Side.COLUMN)), "", "○", "#758092")
                .addAction(TableAction.link("satisfy", "建立满足关系", "satisfiedBy"))
                .build();

        TableActions.Outcome outcome = TableActions.apply(trace, List.of(this.model), "satisfy", "RQ-1", "PD-LA1", VALIDATOR);

        assertTrue(outcome.changed());
        assertEquals(List.of(this.controller), requirement.getSatisfiedBy());
        Matrix matrix = MatrixProjector.project(trace, List.of(this.model));
        assertEquals(List.of("R-1 制动距离"), matrix.rows().stream().map(Matrix.Header::label).toList());
        assertEquals(List.of(new Matrix.Cell("RQ-1", "PD-LA1", List.of("satisfy"))), matrix.cells());
    }

    // =========================================================== performance (T4 step 4)

    /** 50 × 50 = 2500 cells and 100 allocations: the server side of the first screen stays far below 1.5 s. */
    @Test
    void fiftyByFiftyIsFast() {
        Resource big = new ResourceImpl(URI.createURI("memory:/big.xmi"));
        List<PartDefinition> logical = new ArrayList<>();
        List<PartDefinition> physical = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            logical.add(this.block(big, "L" + i, "逻辑" + i, Layer.LOGICAL));
            physical.add(this.block(big, "P" + i, "物理" + i, Layer.PHYSICAL));
        }
        for (int i = 0; i < 100; i++) {
            Allocate allocate = FACTORY.createAllocate();
            allocate.setFrom(logical.get(i % 50));
            allocate.setTo(physical.get((i * 7) % 50));
            big.getContents().add(allocate);
        }
        MatrixProjector.project(this.table, List.of(big)); // warm-up

        long start = System.nanoTime();
        Matrix matrix = MatrixProjector.project(this.table, List.of(big));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertEquals(50, matrix.rows().size());
        assertEquals(100, matrix.columns().size());
        assertTrue(millis < 500, "projection took " + millis + " ms");
    }

    // =========================================================== helpers

    private Matrix matrix() {
        return MatrixProjector.project(this.table, List.of(this.model));
    }

    private TableActions.Outcome act(String action, String row, String column) {
        return TableActions.apply(this.table, List.of(this.model), action, row, column, VALIDATOR);
    }

    private void allocate(PartDefinition from, PartDefinition to) {
        Allocate allocate = FACTORY.createAllocate();
        allocate.setName(from.getName() + " → " + to.getName());
        allocate.setFrom(from);
        allocate.setTo(to);
        this.model.getContents().add(allocate);
    }

    private PartDefinition block(String id, String name, Layer layer) {
        return this.block(this.model, id, name, layer);
    }

    private PartDefinition block(Resource resource, String id, String name, Layer layer) {
        PartDefinition block = FACTORY.createPartDefinition();
        block.setId(id);
        block.setName(name);
        block.setLayer(layer);
        resource.getContents().add(block);
        return block;
    }

    private static List<String> headers(List<Matrix.Header> headers) {
        return headers.stream().map(h -> h.label() + ":" + h.group()).collect(Collectors.toList());
    }
}

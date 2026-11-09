package com.example.sysarch.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.sync.SyncProperties;
import com.example.sysarch.sync.consistency.ConsistencyRules;
import com.example.sysarch.sync.model.ModelOperations;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.view.api.TableDescription;
import com.example.sysarch.view.generated.RequirementTraceTableDescriptionProvider;
import com.example.sysarch.view.table.Matrix;
import com.example.sysarch.view.table.MatrixProjector;
import com.example.sysarch.view.table.TableActions;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * P1 T5 acceptance: the requirement trace matrix (verbatim YAML), its link actions (and undoing them, user's decision),
 * {@code /trace/matrix} data, and R6: "演示数据 5 条需求中，2 条故意不满足，报告命中 2 条".
 */
class RequirementTraceTest {

    private static final ConstraintValidator VALIDATOR = new ConstraintValidator();

    private final TableDescription trace = new RequirementTraceTableDescriptionProvider().build();

    private Resource demo;

    @AfterAll
    static void disposeOcl() {
        VALIDATOR.close();
    }

    @BeforeEach
    void demo() {
        this.demo = new ResourceImpl(URI.createURI("memory:/demo.xmi"));
        this.demo.getContents().addAll(DomainControllerDemo.build());
    }

    // =========================================================== the trace matrix

    @Test
    void rowsAreRequirementsColumnsAreBlocksOfTheThreeLayers() {
        Matrix matrix = this.matrix();

        assertEquals(List.of("DC-REQ-001 目标列表周期", "DC-REQ-002 轨迹时延", "DC-REQ-003 状态反馈", "DC-REQ-004 算力余量", "DC-REQ-005 功能安全"),
                matrix.rows().stream().map(Matrix.Header::label).toList());
        assertEquals(List.of("域控制器", "感知融合单元", "决策规划单元", "执行控制单元", "域控 SoC", "安全 MCU"),
                matrix.columns().stream().map(Matrix.Header::label).toList());
        assertEquals("○", matrix.markSymbol());
        assertEquals("#758092", matrix.markColor());
    }

    @Test
    void satisfyAndVerifyMarkTheCellAndSayWhichRelationsItHolds() {
        assertEquals(Set.of(new Matrix.Cell("RQ-001", "PD-002", List.of("satisfy", "verify")), new Matrix.Cell("RQ-002", "PD-003", List.of("satisfy")),
                new Matrix.Cell("RQ-003", "PD-004", List.of("satisfy"))), Set.copyOf(this.matrix().cells()));
    }

    @Test
    void theLinkActionsAddAndRemoveRelations() {
        TableActions.Outcome satisfy = TableActions.apply(this.trace, List.of(this.demo), "satisfy", "RQ-004", "PD-005", false, VALIDATOR);

        assertTrue(satisfy.changed());
        assertEquals(List.of("域控 SoC"), this.requirement("RQ-004").getSatisfiedBy().stream().map(e -> e.getName()).toList());
        assertTrue(this.matrix().cells().contains(new Matrix.Cell("RQ-004", "PD-005", List.of("satisfy"))));

        TableActions.Outcome undo = TableActions.apply(this.trace, List.of(this.demo), "satisfy", "RQ-004", "PD-005", true, VALIDATOR);

        assertTrue(undo.changed());
        assertEquals(List.of(), this.requirement("RQ-004").getSatisfiedBy());
        assertFalse(this.matrix().cells().stream().anyMatch(c -> c.rowId().equals("RQ-004")));
    }

    @Test
    void removingARelationThatIsNotThereIsRefused() {
        TableActions.Outcome outcome = TableActions.apply(this.trace, List.of(this.demo), "verify", "RQ-002", "PD-003", true, VALIDATOR);

        assertEquals(List.of("NOT_LINKED"), outcome.errors().stream().map(e -> e.code()).toList());
        assertEquals(List.of("satisfy"), this.matrix().cells().stream().filter(c -> c.rowId().equals("RQ-002")).findFirst().orElseThrow().links());
    }

    @Test
    void linkActionsCarryTheirUndoLabel() {
        assertEquals(List.of("satisfy:建立满足关系:取消满足关系", "verify:建立验证关系:取消验证关系"),
                this.matrix().actions().stream().map(a -> a.id() + ":" + a.label() + ":" + a.undoLabel()).toList());
    }

    // =========================================================== /trace/matrix

    @Test
    void theTraceDataOfTheDemo() {
        TraceMatrix matrix = TraceMatrix.of("demo", List.of(this.demo));

        assertEquals(new TraceMatrix.Summary(5, 3, 1, 2), matrix.summary());
        TraceMatrix.Entry first = matrix.requirements().get(0);
        assertEquals("DC-REQ-001", first.reqId());
        assertEquals(List.of(new TraceMatrix.Ref("PD-002", "感知融合单元", "PartDefinition", "LOGICAL")), first.satisfiedBy());
        assertEquals(List.of("RQ-004", "RQ-005"), matrix.requirements().stream().filter(e -> !e.satisfied()).map(TraceMatrix.Entry::id).toList());
    }

    // =========================================================== R6

    /** The acceptance case: 5 requirements, 2 deliberately unsatisfied, 2 R6 issues. */
    @Test
    void theConsistencyReportFindsTheTwoUnsatisfiedRequirements() {
        var index = ModelOperations.index(this.demo.getAllContents());
        var snapshots = ModelOperations.snapshots(index, null, VALIDATOR);
        var facts = new ConsistencyRules.Facts("demo", snapshots, List.of(), Map.of(), Map.of(), null);

        List<ConsistencyRules.Issue> r6 = new ConsistencyRules(new SyncProperties()).evaluate(facts).stream().filter(i -> i.rule().equals("R6")).toList();

        assertEquals(List.of("R6:RQ-004", "R6:RQ-005"), r6.stream().map(ConsistencyRules.Issue::id).toList());
        assertTrue(r6.stream().allMatch(i -> i.severity().equals(ConsistencyRules.MINOR) && !i.repairable()));
        assertTrue(r6.get(0).message().startsWith("需求未被满足：Requirement RQ-004（算力余量）"), r6.get(0).message());
    }

    @Test
    void satisfyingARequirementClearsItsR6() {
        PartDefinition soc = (PartDefinition) this.demo.getContents().stream().filter(e -> e instanceof PartDefinition p && "PD-005".equals(p.getId()))
                .findFirst().orElseThrow();
        this.requirement("RQ-004").getSatisfiedBy().add(soc);

        var snapshots = ModelOperations.snapshots(ModelOperations.index(this.demo.getAllContents()), null, VALIDATOR);
        var issues = new ConsistencyRules(new SyncProperties()).evaluate(new ConsistencyRules.Facts("demo", snapshots, List.of(), Map.of(), Map.of(), null));

        assertEquals(List.of("R6:RQ-005"), issues.stream().filter(i -> i.rule().equals("R6")).map(ConsistencyRules.Issue::id).toList());
    }

    // =========================================================== helpers

    private Matrix matrix() {
        return MatrixProjector.project(this.trace, List.of(this.demo));
    }

    private Requirement requirement(String id) {
        return (Requirement) this.demo.getContents().stream().filter(e -> e instanceof Requirement r && id.equals(r.getId())).findFirst().orElseThrow();
    }
}

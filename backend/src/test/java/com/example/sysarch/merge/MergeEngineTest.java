package com.example.sysarch.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.baseline.ModelSnapshot;
import com.example.sysarch.baseline.ModelSnapshot.ElementState;
import com.example.sysarch.baseline.SnapshotModels;
import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.merge.MergeEngine.Kind;
import com.example.sysarch.merge.MergeEngine.Resolution;
import com.example.sysarch.merge.MergeEngine.Result;
import com.example.sysarch.merge.MergeEngine.Side;
import com.example.sysarch.merge.MergeEngine.Strategy;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.model.SysarchFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

/**
 * P2 T4 acceptance: "构造四类场景（双方改同字段、一方删一方改、双方加同 ID、无冲突），结果符合预期", and P2-AC-4 "冲突列出
 * elementId 与字段名，不静默覆盖", with the user's decisions (only Polarion fields auto-resolved; delete vs modify is a
 * conflict; every conflict needs a choice).
 */
class MergeEngineTest {

    private final Map<String, ElementState> base = ModelSnapshot.capture(List.of(demo()));

    // =========================================================== the four scenarios

    @Test
    void noConflictMergesBothSides() {
        Result result = this.merge(m -> block(m, "PD-002").setName("融合 v2"), m -> block(m, "PD-005").setDescription("SoC 新说明"));

        assertTrue(result.applicable());
        assertEquals(List.of(), result.conflicts());
        assertEquals("融合 v2", result.merged().get("PD-002").name(), "the branch's change");
        assertEquals("SoC 新说明", result.merged().get("PD-005").attributes().get("description"), "the trunk's change stays");
        assertEquals(List.of("PD-002:MODIFY[name]"), ops(result), "only the branch's change touches the trunk");
    }

    /** P2-AC-4: listed with element and field, the trunk's value kept until someone chooses. */
    @Test
    void bothChangedTheSameFieldIsAConflictNotAnOverwrite() {
        Result result = this.merge(m -> block(m, "PD-002").setName("分支的名字"), m -> block(m, "PD-002").setName("主干的名字"));

        assertEquals(1, result.conflicts().size());
        MergeEngine.Conflict conflict = result.conflicts().get(0);
        assertEquals("PD-002", conflict.elementId());
        assertEquals("name", conflict.field());
        assertEquals(Kind.FIELD, conflict.kind());
        assertEquals("感知融合单元", conflict.base());
        assertEquals("分支的名字", conflict.theirs());
        assertEquals("主干的名字", conflict.ours());
        assertFalse(result.applicable());
        assertEquals("主干的名字", result.merged().get("PD-002").name(), "not silently overwritten");

        Result chosen = this.merge(m -> block(m, "PD-002").setName("分支的名字"), m -> block(m, "PD-002").setName("主干的名字"),
                List.of(new Resolution("PD-002", "name", Side.THEIRS)));
        assertTrue(chosen.applicable());
        assertEquals("分支的名字", chosen.merged().get("PD-002").name());
    }

    @Test
    void deletedOnOneSideAndModifiedOnTheOtherIsAConflict() {
        Result result = this.merge(m -> EcoreUtil.delete(find(m, "RQ-005"), true), m -> ((Requirement) find(m, "RQ-005")).setDescription("另一方改了"));
        assertEquals(List.of("RQ-005:DELETE_MODIFY"), result.conflicts().stream().map(c -> c.elementId() + ":" + c.kind()).toList());
        assertTrue(result.merged().containsKey("RQ-005"), "kept until chosen");

        Result deleted = this.merge(m -> EcoreUtil.delete(find(m, "RQ-005"), true), m -> ((Requirement) find(m, "RQ-005")).setDescription("另一方改了"),
                List.of(new Resolution("RQ-005", null, Side.THEIRS)));
        assertEquals(List.of("RQ-005:DELETE"), ops(deleted));

        Result reAdded = this.merge(m -> ((Requirement) find(m, "RQ-005")).setDescription("另一方改了"), m -> EcoreUtil.delete(find(m, "RQ-005"), true),
                List.of(new Resolution("RQ-005", null, Side.THEIRS)));
        assertEquals(List.of("RQ-005:ADD"), ops(reAdded), "the branch's modified element comes back");
    }

    /** Spec step 3: deleted on one side, untouched on the other → deleted. */
    @Test
    void deletedOnOneSideAndUntouchedOnTheOtherIsDeleted() {
        Result result = this.merge(m -> EcoreUtil.delete(find(m, "RQ-005"), true), m -> { });
        assertTrue(result.applicable());
        assertEquals(List.of("RQ-005:DELETE"), ops(result));
    }

    @Test
    void bothAddedTheSameIdIsAConflictUnlessEqual() {
        Result different = this.merge(m -> addBlock(m, "PD-NEW", "分支加的"), m -> addBlock(m, "PD-NEW", "主干加的"));
        assertEquals(List.of("PD-NEW:DUPLICATE_ID"), different.conflicts().stream().map(c -> c.elementId() + ":" + c.kind()).toList());

        Result same = this.merge(m -> addBlock(m, "PD-NEW", "一样"), m -> addBlock(m, "PD-NEW", "一样"));
        assertTrue(same.applicable());
        assertEquals(List.of(), ops(same), "already in the trunk");

        Result theirs = this.merge(m -> addBlock(m, "PD-NEW", "分支加的"), m -> addBlock(m, "PD-NEW", "主干加的"),
                List.of(new Resolution("PD-NEW", null, Side.THEIRS)));
        assertEquals(List.of("PD-NEW:MODIFY[name]"), ops(theirs));
    }

    // =========================================================== the strategy, structure, references, the limit

    /** User's decision: only Polarion-owned fields follow the strategy; they are listed, not hidden. */
    @Test
    void polarionFieldsFollowTheStrategy() {
        Consumer<Resource> branch = m -> ((Requirement) find(m, "RQ-001")).setText("分支的需求正文");
        Consumer<Resource> trunk = m -> ((Requirement) find(m, "RQ-001")).setText("Polarion 拉下来的正文");

        Result polarion = MergeEngine.merge(this.base, edited(branch), edited(trunk), Strategy.POLARION_WINS, List.of());
        assertTrue(polarion.applicable());
        assertEquals("Polarion 拉下来的正文", polarion.merged().get("RQ-001").attributes().get("text"));
        assertEquals(1, polarion.autoResolved().size());

        Result model = MergeEngine.merge(this.base, edited(branch), edited(trunk), Strategy.MODEL_WINS, List.of());
        assertEquals("分支的需求正文", model.merged().get("RQ-001").attributes().get("text"));
    }

    @Test
    void anAddedPartUnderABlockArrivesWithItsPlace() {
        Result result = this.merge(m -> {
            Part part = SysarchFactory.eINSTANCE.createPart();
            part.setId("PT-NEW");
            part.setName("新部件");
            part.setTypedBy(block(m, "PD-002"));
            block(m, "PD-001").getOwnedParts().add(part);
        }, m -> { });
        assertEquals(List.of("PT-NEW:ADD"), ops(result));
        assertEquals("PD-001", result.merged().get("PT-NEW").attributes().get(ModelSnapshot.CONTAINER));
    }

    /** The branch uses a block the trunk deleted: a reference into nothing blocks the merge. */
    @Test
    void aReferenceToADeletedElementBlocks() {
        Result result = this.merge(m -> ((Requirement) find(m, "RQ-004")).getSatisfiedBy().add(block(m, "PD-006")),
                m -> EcoreUtil.delete(find(m, "PD-006"), true));
        assertFalse(result.applicable());
        assertTrue(result.unresolved().stream().anyMatch(c -> c.kind() == Kind.DANGLING && c.elementId().equals("RQ-004") && c.field().equals("satisfiedBy")),
                String.valueOf(result.unresolved()));
    }

    @Test
    void moreThanFiftyConflictsRefuseTheAutomaticMerge() {
        Consumer<Resource> branch = m -> m.getAllContents().forEachRemaining(e -> {
            if (e instanceof com.example.sysarch.model.ArchElement a) {
                a.setDescription("分支 " + a.getId());
            }
        });
        Consumer<Resource> trunk = m -> m.getAllContents().forEachRemaining(e -> {
            if (e instanceof com.example.sysarch.model.ArchElement a) {
                a.setDescription("主干 " + a.getId());
            }
        });
        Result few = MergeEngine.merge(this.base, edited(branch), edited(trunk), Strategy.POLARION_WINS, List.of());
        assertEquals(this.base.size(), few.conflicts().size());
        assertFalse(few.autoMergeRefused(), this.base.size() + " ≤ 50");

        Map<String, ElementState> big = ModelSnapshot.capture(List.of(bigModel()));
        Result many = MergeEngine.merge(big, edited(big, branch), edited(big, trunk), Strategy.POLARION_WINS, List.of());
        assertTrue(many.conflicts().size() > 50);
        assertTrue(many.autoMergeRefused());
    }

    /** Spec §6.1 "合并预演（200 变更）< 10s": the engine's part, on 2000 elements with 200 changes on each side. */
    @Test
    void twoHundredChangesMergeWellUnderTenSeconds() {
        Resource big = demo();
        for (int i = 0; i < 2000; i++) {
            addBlock(big, "PD-B" + i, "块 " + i);
        }
        Map<String, ElementState> base = ModelSnapshot.capture(List.of(big));
        Map<String, ElementState> theirs = edited(base, m -> {
            for (int i = 0; i < 200; i++) {
                block(m, "PD-B" + i).setName("分支改 " + i);
            }
        });
        Map<String, ElementState> ours = edited(base, m -> {
            for (int i = 1000; i < 1200; i++) {
                block(m, "PD-B" + i).setDescription("主干改 " + i);
            }
        });
        long start = System.nanoTime();
        Result result = MergeEngine.merge(base, theirs, ours, Strategy.POLARION_WINS, List.of());
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertEquals(200, result.operations().size());
        assertTrue(ms < 10_000, ms + " ms");
    }

    @Test
    void theSameInputsGiveTheSameResult() {
        Consumer<Resource> branch = m -> block(m, "PD-002").setName("a");
        Consumer<Resource> trunk = m -> block(m, "PD-003").setName("b");
        assertEquals(this.merge(branch, trunk), this.merge(branch, trunk));
        assertNull(this.merge(branch, trunk).merged().get("nope"));
    }

    // =========================================================== helpers

    private Result merge(Consumer<Resource> branch, Consumer<Resource> trunk) {
        return this.merge(branch, trunk, List.of());
    }

    private Result merge(Consumer<Resource> branch, Consumer<Resource> trunk, List<Resolution> resolutions) {
        return MergeEngine.merge(this.base, edited(branch), edited(trunk), Strategy.POLARION_WINS, resolutions);
    }

    private Map<String, ElementState> edited(Consumer<Resource> edit) {
        return edited(this.base, edit);
    }

    private static Map<String, ElementState> edited(Map<String, ElementState> from, Consumer<Resource> edit) {
        Resource model = new ResourceImpl(URI.createURI("memory:/copy.json"));
        model.getContents().addAll(SnapshotModels.build(from));
        edit.accept(model);
        return ModelSnapshot.capture(List.of(model));
    }

    private static List<String> ops(Result result) {
        return result.operations().stream().map(o -> o.elementId() + ":" + o.op() + (o.fields().isEmpty() ? "" : o.fields().toString().replace(" ", ""))).toList();
    }

    private static Resource demo() {
        Resource resource = new ResourceImpl(URI.createURI("memory:/demo.json"));
        resource.getContents().addAll(DomainControllerDemo.build());
        return resource;
    }

    private static Resource bigModel() {
        Resource resource = demo();
        for (int i = 0; i < 40; i++) {
            addBlock(resource, "PD-X" + i, "块 " + i);
        }
        return resource;
    }

    private static void addBlock(Resource model, String id, String name) {
        PartDefinition block = SysarchFactory.eINSTANCE.createPartDefinition();
        block.setId(id);
        block.setName(name);
        model.getContents().add(block);
    }

    private static PartDefinition block(Resource model, String id) {
        return (PartDefinition) find(model, id);
    }

    private static EObject find(Resource model, String id) {
        for (var it = model.getAllContents(); it.hasNext();) {
            EObject e = it.next();
            if (id.equals(EcoreUtil.getID(e))) {
                return e;
            }
        }
        throw new IllegalArgumentException(id);
    }
}

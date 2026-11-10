package com.example.sysarch.baseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.baseline.ModelSnapshot.ElementState;
import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.SysarchFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.Test;

/**
 * P2 T3: a model rebuilt from its snapshot has exactly the same snapshot (so a branch workspace starts equal to its
 * baseline), and the structure fields keep P1's diff semantics.
 */
class SnapshotModelsTest {

    @Test
    void theDemoRebuiltFromItsSnapshotHasTheSameSnapshot() {
        Map<String, ElementState> original = ModelSnapshot.capture(List.of(demo()));
        assertTrue(ModelSnapshot.isStructural(original));

        Resource rebuilt = new ResourceImpl(URI.createURI("memory:/rebuilt.json"));
        rebuilt.getContents().addAll(SnapshotModels.build(original));

        assertEquals(original, ModelSnapshot.capture(List.of(rebuilt)));
    }

    /** Elements whose id was their place in the document get it as their id, so they keep their identity. */
    @Test
    void elementsWithoutAnIdKeepTheirDocumentId() {
        Resource model = demo();
        PartDefinition anonymous = SysarchFactory.eINSTANCE.createPartDefinition();
        anonymous.setName("资源树里建的块");
        model.getContents().add(anonymous);
        Map<String, ElementState> original = ModelSnapshot.capture(List.of(model));
        String documentId = model.getURIFragment(anonymous);
        assertTrue(original.containsKey(documentId));

        Resource rebuilt = new ResourceImpl(URI.createURI("memory:/rebuilt.json"));
        rebuilt.getContents().addAll(SnapshotModels.build(original));
        assertEquals(original, ModelSnapshot.capture(List.of(rebuilt)));
    }

    /** P1 semantics kept: adding a part is one "added"; moving it is one "modified" (container). */
    @Test
    void structureFieldsKeepTheDiffSemantics() {
        Resource model = demo();
        Map<String, ElementState> before = ModelSnapshot.capture(List.of(model));
        PartDefinition controller = (PartDefinition) model.getContents().get(0);
        Part part = SysarchFactory.eINSTANCE.createPart();
        part.setId("PT-NEW");
        part.setName("新部件");
        controller.getOwnedParts().add(part);

        var added = ModelDiffs.diff(before, ModelSnapshot.capture(List.of(model)));
        assertEquals(List.of("PT-NEW"), added.getAdded());
        assertEquals(List.of(), added.getModified());

        Map<String, ElementState> withPart = ModelSnapshot.capture(List.of(model));
        PartDefinition soc = (PartDefinition) model.getContents().stream().filter(e -> e instanceof PartDefinition p && "PD-005".equals(p.getId()))
                .findFirst().orElseThrow();
        soc.getOwnedParts().add(part);
        var moved = ModelDiffs.diff(withPart, ModelSnapshot.capture(List.of(model)));
        assertEquals(1, moved.getModified().size());
        assertEquals("PT-NEW", moved.getModified().get(0).getElementId());
        assertEquals(List.of("container"), moved.getModified().get(0).getFields());
    }

    /** A baseline from before P2 (no structure) still diffs cleanly, but cannot be rebuilt. */
    @Test
    void anOldBaselineDiffsWithoutStructureNoise() {
        Map<String, ElementState> now = ModelSnapshot.capture(List.of(demo()));
        Map<String, ElementState> old = new TreeMap<>();
        now.forEach((id, state) -> {
            Map<String, Object> attributes = new HashMap<>(state.attributes());
            attributes.remove(ModelSnapshot.CONTAINER);
            attributes.remove(ModelSnapshot.FEATURE);
            old.put(id, new ElementState(state.id(), state.type(), state.name(), state.layer(), attributes));
        });
        var diff = ModelDiffs.diff(old, now);
        assertEquals(List.of(), diff.getModified());
        assertFalse(ModelSnapshot.isStructural(old));
    }

    @Test
    void anUnbuildableStateIsRefusedWholly() {
        Map<String, ElementState> states = new TreeMap<>(ModelSnapshot.capture(List.of(demo())));
        ElementState part = states.get("PT-001");
        Map<String, Object> broken = new HashMap<>(part.attributes());
        broken.put("typedBy", "PD-404");
        states.put("PT-001", new ElementState(part.id(), part.type(), part.name(), part.layer(), broken));
        assertThrows(SnapshotModels.UnbuildableException.class, () -> SnapshotModels.build(states));
    }

    private static Resource demo() {
        Resource resource = new ResourceImpl(URI.createURI("memory:/demo.json"));
        resource.getContents().addAll(DomainControllerDemo.build());
        return resource;
    }
}

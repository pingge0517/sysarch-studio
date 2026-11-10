package com.example.sysarch.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.emf.common.command.BasicCommandStack;
import org.eclipse.emf.ecore.change.ChangeFactory;
import org.eclipse.emf.edit.domain.AdapterFactoryEditingDomain;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.web.application.editingcontext.EditingContext;
import org.junit.jupiter.api.Test;

/** P2 T9: Sirius' undo history per editing context stays at the configured number of steps (the load test's growth). */
class UndoHistoryLimitTest {

    private record Input(UUID id) implements IInput {
    }

    @Test
    void theOldestStepsAreDroppedTheNewestKept() {
        EditingContext context = new EditingContext("ec", new AdapterFactoryEditingDomain(new ComposedAdapterFactory(), new BasicCommandStack()), Map.of(),
                List.of());
        UndoHistoryLimit limit = new UndoHistoryLimit(500);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            // what Sirius' UndoRedoRecorder keeps for every input
            context.getInputId2change().put(id, ChangeFactory.eINSTANCE.createChangeDescription());
            context.getInputId2RepresentationChanges().put(id, new ArrayList<>());
            limit.postProcess(context, new Input(id), null);
        }
        assertEquals(500, context.getInputId2change().size());
        assertEquals(500, context.getInputId2RepresentationChanges().size());
        assertTrue(context.getInputId2change().containsKey(ids.get(1999)), "the last step can be undone");
        assertTrue(context.getInputId2change().containsKey(ids.get(1500)), "and the 500th from the end");
        assertFalse(context.getInputId2change().containsKey(ids.get(1499)));
        assertEquals(500, limit.tracked(context));
    }

    @Test
    void anEntryRecordedAfterThisProcessorRanIsStillTrimmedLater() {
        EditingContext context = new EditingContext("ec", new AdapterFactoryEditingDomain(new ComposedAdapterFactory(), new BasicCommandStack()), Map.of(),
                List.of());
        UndoHistoryLimit limit = new UndoHistoryLimit(10);
        UUID first = UUID.randomUUID();
        limit.postProcess(context, new Input(first), null);
        context.getInputId2change().put(first, ChangeFactory.eINSTANCE.createChangeDescription()); // Sirius' recorder ran second
        for (int i = 0; i < 10; i++) {
            limit.postProcess(context, new Input(UUID.randomUUID()), null);
        }
        assertFalse(context.getInputId2change().containsKey(first));
    }
}

package com.example.sysarch.config;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.eclipse.sirius.components.collaborative.api.ChangeDescription;
import org.eclipse.sirius.components.collaborative.api.IInputPostProcessor;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.web.application.editingcontext.EditingContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Sinks;

/**
 * P2 T9: a bounded undo history. Sirius Web 2025.8.11 keeps, for undo/redo, the EMF change description of every input
 * of an editing context ({@code EditingContext.inputId2change}, and {@code inputId2RepresentationChanges}), in plain
 * hash maps that nothing ever trims: they live until the project is closed. The 30-user load test held 68,496 change
 * descriptions after half an hour, about 100 MB an hour of heap that never came back while the project stayed open.
 *
 * <p>This post-processor remembers, per editing context, the order of its inputs and drops the oldest entries from both
 * maps once there are more than {@code sysarch.undo.max-steps} (default 500). Undoing a dropped input finds no change
 * and does nothing (Sirius checks for null), so undo still reaches the last 500 steps. Additive: Sirius' maps are
 * reached through their public getters on its own editing-context thread, where the inputs are processed one at a time.
 */
@Component
public class UndoHistoryLimit implements IInputPostProcessor {

    private final int maxSteps;

    /** Input ids per editing context, oldest first; weak keys, so a disposed context takes its list with it. */
    private final Map<IEditingContext, Deque<UUID>> order = Collections.synchronizedMap(new WeakHashMap<>());

    public UndoHistoryLimit(@Value("${sysarch.undo.max-steps:500}") int maxSteps) {
        this.maxSteps = Math.max(1, maxSteps);
    }

    @Override
    public void postProcess(IEditingContext editingContext, IInput input, Sinks.Many<ChangeDescription> changeDescriptionSink) {
        if (!(editingContext instanceof EditingContext context)) {
            return;
        }
        Deque<UUID> inputs = this.order.computeIfAbsent(editingContext, k -> new ArrayDeque<>());
        synchronized (inputs) {
            inputs.addLast(input.id());
            // also ids recorded after this processor ran (Sirius' recorder may come later in the same input): every
            // input id enters the queue, and trimming removes whatever the maps hold for it
            while (inputs.size() > this.maxSteps) {
                UUID oldest = inputs.pollFirst();
                context.getInputId2change().remove(oldest);
                context.getInputId2RepresentationChanges().remove(oldest);
            }
        }
    }

    int tracked(IEditingContext editingContext) {
        Deque<UUID> inputs = this.order.get(editingContext);
        return inputs == null ? 0 : inputs.size();
    }
}

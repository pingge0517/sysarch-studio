package com.example.sysarch.sync.consistency;

import com.example.sysarch.sync.model.ModelGateway;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Consumes the webhook queue: each event only marks its Work Item "待校验" (T8 step 3); R1-R5 judge the mark in the
 * next report. Spec §6.3: background jobs detect, they never repair or change data.
 */
@Component
public class PolarionEventConsumer {

    private final ConsistencyStore store;

    private final ModelGateway model;

    public PolarionEventConsumer(ConsistencyStore store, ModelGateway model) {
        this.store = Objects.requireNonNull(store);
        this.model = Objects.requireNonNull(model);
    }

    @Scheduled(fixedDelayString = "${sysarch.polarion.event-poll-ms:2000}")
    public void consumeQueued() {
        Set<String> projects = new LinkedHashSet<>();
        for (ConsistencyStore.QueuedEvent event : this.store.unprocessed(100)) {
            this.store.consume(event);
            projects.add(event.projectId());
        }
        // The marks live outside the model; without this the Validation view would show them only after the next edit.
        projects.forEach(this.model::refreshViews);
    }
}

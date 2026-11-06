package com.example.sysarch.sync.consistency;

import java.util.Objects;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Consumes the webhook queue: each event only marks its Work Item "待校验" (T8 step 3); R1-R5 judge the mark in the
 * next report. Spec §6.3: background jobs detect, they never repair or change data.
 */
@Component
public class PolarionEventConsumer {

    private final ConsistencyStore store;

    public PolarionEventConsumer(ConsistencyStore store) {
        this.store = Objects.requireNonNull(store);
    }

    @Scheduled(fixedDelayString = "${sysarch.polarion.event-poll-ms:2000}")
    public void consumeQueued() {
        this.store.unprocessed(100).forEach(this.store::consume);
    }
}

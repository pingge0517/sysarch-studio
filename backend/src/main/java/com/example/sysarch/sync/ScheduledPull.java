package com.example.sysarch.sync;

import com.example.sysarch.sync.polarion.PolarionToken;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The scheduled incremental pull (P1 T6 step 2, spec P1 §4.3): every {@code schedule.interval} (15 min) per mapped
 * Polarion project, {@link SyncService#pullIncremental} with {@code since} = the start of the last successful run.
 *
 * <ul>
 * <li>Credentials: the service account token {@code POLARION_SYNC_TOKEN} (user's decision), from the environment only.
 * Unset: nothing runs, and the panel says so. The audit actor is {@code scheduler}.</li>
 * <li>Failure: one retry after {@code retry-delay} (2 min), then back to the interval; {@code max-failures} (3) in a
 * row pause the project's schedule and raise the alert (log ERROR, red entry in the Validation view, status API).
 * {@code POST /api/v1/sync/status/resume} or a successful manual pull resumes it.</li>
 * <li>It never blocks editing: Polarion is called outside the editing context, and only a pull that found
 * Polarion-owned changes writes to the model, as one short edit (spec P1-AC-6).</li>
 * <li>Like every scheduled job it only detects (spec §6.3): it applies Polarion-owned values and marks conflicts, and
 * repairs nothing.</li>
 * </ul>
 */
@Component
public class ScheduledPull {

    static final String ACTOR = "scheduler";

    /** The errors of a pull that make the run a failure; the others (a deleted Work Item...) are findings. */
    static final Set<String> FAILURES = Set.of("POLARION_ERROR", "POLARION_RATE_LIMITED", "PROJECT_NOT_MAPPED");

    private final Logger logger = LoggerFactory.getLogger(ScheduledPull.class);

    private final SyncService sync;

    private final SyncStatusStore status;

    private final SyncProperties properties;

    private final Clock clock;

    @Autowired
    public ScheduledPull(SyncService sync, SyncStatusStore status, SyncProperties properties) {
        this(sync, status, properties, Clock.systemUTC());
    }

    ScheduledPull(SyncService sync, SyncStatusStore status, SyncProperties properties, Clock clock) {
        this.sync = Objects.requireNonNull(sync);
        this.status = Objects.requireNonNull(status);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    public boolean enabled() {
        return this.properties.getSyncToken() != null && !this.properties.getSyncToken().isBlank();
    }

    @Scheduled(fixedDelayString = "${sysarch.polarion.schedule.tick:PT30S}", initialDelayString = "${sysarch.polarion.schedule.tick:PT30S}")
    public void tick() {
        if (!this.enabled()) {
            return;
        }
        for (String projectId : List.copyOf(this.properties.getProjects().keySet())) {
            try {
                this.runIfDue(projectId);
            } catch (RuntimeException e) {
                this.logger.error("scheduled pull of {} failed unexpectedly", projectId, e);
            }
        }
    }

    /** One project: pulls if due; returns whether it ran. */
    boolean runIfDue(String projectId) {
        SyncStatusStore.Status state = this.status.status(projectId);
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        if (state.schedulePaused() || (state.scheduleNextAttemptAt() != null && now.isBefore(state.scheduleNextAttemptAt()))) {
            return false;
        }
        OffsetDateTime since = state.scheduleLastSuccessAt() != null ? state.scheduleLastSuccessAt()
                : state.lastPullAt() != null ? state.lastPullAt() : now.minus(this.properties.getSchedule().getInterval());
        String failure;
        try {
            SyncService.Outcome outcome = this.sync.pullIncremental(projectId, since, new PolarionToken(this.properties.getSyncToken()), ACTOR);
            failure = outcome.status() == 200 ? outcome.result().getErrors().stream().filter(e -> FAILURES.contains(e.getCode()))
                    .map(e -> e.getCode() + ": " + e.getMessage()).findFirst().orElse(null) : "HTTP " + outcome.status();
        } catch (RuntimeException e) {
            failure = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        OffsetDateTime finished = OffsetDateTime.now(this.clock);
        if (failure == null) {
            this.status.scheduledSuccess(projectId, now, finished.plus(this.properties.getSchedule().getInterval()));
            return true;
        }
        int failures = state.scheduleFailures() + 1;
        boolean paused = failures >= this.properties.getSchedule().getMaxFailures();
        OffsetDateTime next = paused ? null
                : finished.plus(failures == 1 ? this.properties.getSchedule().getRetryDelay() : this.properties.getSchedule().getInterval());
        this.status.scheduledFailure(projectId, now, failure, failures, paused, next);
        if (paused) {
            this.logger.error("Scheduled pull of Polarion project {} paused after {} failures in a row; last: {}. Resume: POST /api/v1/sync/status/resume",
                    projectId, failures, failure);
        } else {
            this.logger.warn("Scheduled pull of Polarion project {} failed ({} in a row), next attempt {}: {}", projectId, failures, next, failure);
        }
        return true;
    }
}

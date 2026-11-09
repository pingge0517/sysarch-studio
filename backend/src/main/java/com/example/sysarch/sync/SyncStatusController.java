package com.example.sysarch.sync;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The sync panel's data (P1 T6 step 4; not in the spec's OpenAPI): {@code GET /api/v1/sync/status?projectId=} gives
 * the last push, pull and consistency check and the scheduled pull's state; {@code POST /api/v1/sync/status/resume}
 * resumes a paused schedule. {@code projectId} is the Polarion project id, as in /sync/push.
 */
@RestController
@RequestMapping("/api/v1/sync/status")
public class SyncStatusController {

    public record Schedule(boolean enabled, Duration interval, OffsetDateTime lastSuccessAt, OffsetDateTime lastAttemptAt, int consecutiveFailures,
            boolean paused, String lastError, OffsetDateTime nextAttemptAt) {
    }

    public record Status(String projectId, OffsetDateTime lastPushAt, OffsetDateTime lastPullAt, OffsetDateTime lastCheckAt, Schedule schedule) {
    }

    private final SyncStatusStore store;

    private final SyncProperties properties;

    private final ScheduledPull scheduler;

    public SyncStatusController(SyncStatusStore store, SyncProperties properties, ScheduledPull scheduler) {
        this.store = Objects.requireNonNull(store);
        this.properties = Objects.requireNonNull(properties);
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    @GetMapping
    public Status status(@RequestParam("projectId") String projectId) {
        this.requireMapped(projectId);
        SyncStatusStore.Status s = this.store.status(projectId);
        return new Status(projectId, s.lastPushAt(), s.lastPullAt(), s.lastCheckAt(), new Schedule(this.scheduler.enabled(),
                this.properties.getSchedule().getInterval(), s.scheduleLastSuccessAt(), s.scheduleLastAttemptAt(), s.scheduleFailures(), s.schedulePaused(),
                s.scheduleLastError(), s.scheduleNextAttemptAt()));
    }

    @PostMapping("/resume")
    public Status resume(@RequestParam("projectId") String projectId) {
        this.requireMapped(projectId);
        this.store.resume(projectId);
        return this.status(projectId);
    }

    private void requireMapped(String projectId) {
        if (!this.properties.getProjects().containsKey(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Polarion project '" + projectId + "' is not mapped (sysarch.polarion.projects)");
        }
    }
}

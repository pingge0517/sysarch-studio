package com.example.sysarch.sync;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code sync_status} (P1 T6): per Polarion project, the times the sync panel shows (last push, last pull, last
 * consistency check) and the state of the scheduled pull.
 */
@Repository
public class SyncStatusStore {

    /** The consistency check runs after every edit; its time is written at most this often. */
    static final Duration CHECK_WRITE_INTERVAL = Duration.ofMinutes(1);

    public record Status(String projectId, OffsetDateTime lastPushAt, OffsetDateTime lastPullAt, OffsetDateTime lastCheckAt,
            OffsetDateTime scheduleLastSuccessAt, OffsetDateTime scheduleLastAttemptAt, int scheduleFailures, boolean schedulePaused,
            String scheduleLastError, OffsetDateTime scheduleNextAttemptAt) {

        static Status empty(String projectId) {
            return new Status(projectId, null, null, null, null, null, 0, false, null, null);
        }
    }

    private final JdbcClient jdbc;

    public SyncStatusStore(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public Status status(String projectId) {
        return this.jdbc.sql("SELECT project_id, last_push_at, last_pull_at, last_check_at, schedule_last_success_at, schedule_last_attempt_at, "
                + "schedule_failures, schedule_paused, schedule_last_error, schedule_next_attempt_at FROM sync_status WHERE project_id = :p")
                .param("p", projectId)
                .query((rs, i) -> new Status(rs.getString(1), rs.getObject(2, OffsetDateTime.class), rs.getObject(3, OffsetDateTime.class),
                        rs.getObject(4, OffsetDateTime.class), rs.getObject(5, OffsetDateTime.class), rs.getObject(6, OffsetDateTime.class), rs.getInt(7),
                        rs.getBoolean(8), rs.getString(9), rs.getObject(10, OffsetDateTime.class)))
                .optional().orElse(Status.empty(projectId));
    }

    public void pushed(String projectId, OffsetDateTime at) {
        this.ensure(projectId);
        this.jdbc.sql("UPDATE sync_status SET last_push_at = :at WHERE project_id = :p").param("at", at).param("p", projectId).update();
    }

    /** Any successful pull, manual or scheduled. */
    public void pulled(String projectId, OffsetDateTime at) {
        this.ensure(projectId);
        this.jdbc.sql("UPDATE sync_status SET last_pull_at = :at WHERE project_id = :p").param("at", at).param("p", projectId).update();
    }

    /** A consistency evaluation; written at most once per {@link #CHECK_WRITE_INTERVAL}. */
    public void checked(String projectId, OffsetDateTime at) {
        this.ensure(projectId);
        this.jdbc.sql("UPDATE sync_status SET last_check_at = :at WHERE project_id = :p AND (last_check_at IS NULL OR last_check_at < :before)")
                .param("at", at).param("p", projectId).param("before", at.minus(CHECK_WRITE_INTERVAL)).update();
    }

    /** @param startedAt when the successful run started: the next run pulls what changed since then */
    public void scheduledSuccess(String projectId, OffsetDateTime startedAt, OffsetDateTime next) {
        this.ensure(projectId);
        this.jdbc.sql("UPDATE sync_status SET schedule_last_success_at = :at, schedule_last_attempt_at = :at, last_pull_at = :at, schedule_failures = 0, "
                + "schedule_paused = FALSE, schedule_last_error = NULL, schedule_next_attempt_at = :next WHERE project_id = :p")
                .param("at", startedAt).param("next", next).param("p", projectId).update();
    }

    public void scheduledFailure(String projectId, OffsetDateTime at, String error, int failures, boolean paused, OffsetDateTime next) {
        this.ensure(projectId);
        this.jdbc.sql("UPDATE sync_status SET schedule_last_attempt_at = :at, schedule_failures = :f, schedule_paused = :paused, "
                + "schedule_last_error = :error, schedule_next_attempt_at = :next WHERE project_id = :p")
                .param("at", at).param("f", failures).param("paused", paused).param("error", error).param("next", next).param("p", projectId).update();
    }

    /** Resumes a paused schedule: runs on the next tick. */
    public void resume(String projectId) {
        this.ensure(projectId);
        this.jdbc.sql("UPDATE sync_status SET schedule_paused = FALSE, schedule_failures = 0, schedule_next_attempt_at = NULL WHERE project_id = :p")
                .param("p", projectId).update();
    }

    /** Insert-if-absent without an error (a failed insert would abort a PostgreSQL transaction). */
    private void ensure(String projectId) {
        this.jdbc.sql("INSERT INTO sync_status (project_id) SELECT :p WHERE NOT EXISTS (SELECT 1 FROM sync_status WHERE project_id = :p)")
                .param("p", projectId).update();
    }
}

package com.example.sysarch.sync;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

/**
 * Sync bookkeeping (spec §6.3): the audit trail, the idempotency table and the unbound pool, plus their retention.
 */
@Repository
public class SyncStore {

    /** Audit rows are kept 180 days (spec §6.3). */
    static final Duration AUDIT_RETENTION = Duration.ofDays(180);

    /** Replays of an Idempotency-Key within 24 hours return the first result (spec §6.3). */
    static final Duration IDEMPOTENCY_WINDOW = Duration.ofHours(24);

    private final Logger logger = LoggerFactory.getLogger(SyncStore.class);

    private final JdbcClient jdbc;

    public SyncStore(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    /** One field written by a push or pull. */
    public void audit(String projectId, String elementId, String workItemId, String direction, String field, String oldValue, String newValue,
            String actor) {
        this.jdbc.sql("INSERT INTO sync_audit (project_id, element_id, work_item_id, direction, field, old_value, new_value, actor, at) "
                + "VALUES (:project, :element, :workItem, :direction, :field, :old, :new, :actor, :at)")
                .param("project", projectId).param("element", elementId).param("workItem", workItemId).param("direction", direction)
                .param("field", field).param("old", oldValue).param("new", newValue).param("actor", actor).param("at", OffsetDateTime.now())
                .update();
    }

    /** The stored response of a key, if it was used within the window. */
    public Optional<StoredResponse> replay(String key) {
        return this.jdbc.sql("SELECT response_status, response_body FROM sync_idempotency WHERE idempotency_key = :key AND created_at > :since")
                .param("key", key).param("since", OffsetDateTime.now().minus(IDEMPOTENCY_WINDOW))
                .query(StoredResponse.class)
                .optional();
    }

    /**
     * Stores the first response of a key. Keys older than the window are replaced, so a key may be reused a day later.
     */
    public void remember(String key, String endpoint, int status, String body) {
        this.jdbc.sql("DELETE FROM sync_idempotency WHERE idempotency_key = :key AND created_at <= :since")
                .param("key", key).param("since", OffsetDateTime.now().minus(IDEMPOTENCY_WINDOW)).update();
        this.jdbc.sql("INSERT INTO sync_idempotency (idempotency_key, endpoint, response_status, response_body, created_at) "
                + "VALUES (:key, :endpoint, :status, :body, :at)")
                .param("key", key).param("endpoint", endpoint).param("status", status).param("body", body).param("at", OffsetDateTime.now())
                .update();
    }

    /** A Work Item created in Polarion that no element is bound to yet (spec §6.2 "未绑定池"); duplicates are ignored. */
    public boolean addToUnboundPool(String projectId, String workItemId, String workItemType, String title) {
        int existing = this.jdbc.sql("SELECT COUNT(*) FROM polarion_unbound_pool WHERE project_id = :project AND work_item_id = :id")
                .param("project", projectId).param("id", workItemId).query(Integer.class).single();
        if (existing > 0) {
            return false;
        }
        this.jdbc.sql("INSERT INTO polarion_unbound_pool (project_id, work_item_id, work_item_type, title, discovered_at) "
                + "VALUES (:project, :id, :type, :title, :at)")
                .param("project", projectId).param("id", workItemId).param("type", workItemType).param("title", title).param("at", OffsetDateTime.now())
                .update();
        return true;
    }

    /** One Work Item of the unbound pool. */
    public record PooledWorkItem(String workItemId, String workItemType, String title, OffsetDateTime discoveredAt) {
    }

    /** The unbound pool of a Polarion project, oldest first. */
    public java.util.List<PooledWorkItem> unboundPool(String projectId) {
        return this.jdbc.sql("SELECT work_item_id, work_item_type, title, discovered_at FROM polarion_unbound_pool WHERE project_id = :project "
                + "ORDER BY discovered_at, work_item_id").param("project", projectId)
                .query((rs, row) -> new PooledWorkItem(rs.getString(1), rs.getString(2), rs.getString(3), rs.getObject(4, OffsetDateTime.class))).list();
    }

    /** Claiming a pooled Work Item (binding it) removes it from the pool. */
    public void removeFromUnboundPool(String projectId, String workItemId) {
        this.jdbc.sql("DELETE FROM polarion_unbound_pool WHERE project_id = :project AND work_item_id = :id")
                .param("project", projectId).param("id", workItemId).update();
    }

    /** Daily: audit older than 180 days and idempotency keys older than 24 hours are removed. */
    @Scheduled(cron = "${sysarch.polarion.retention-cron:0 30 3 * * *}")
    public void applyRetention() {
        OffsetDateTime now = OffsetDateTime.now();
        int audit = this.jdbc.sql("DELETE FROM sync_audit WHERE at < :before").param("before", now.minus(AUDIT_RETENTION)).update();
        int keys = this.jdbc.sql("DELETE FROM sync_idempotency WHERE created_at < :before").param("before", now.minus(IDEMPOTENCY_WINDOW)).update();
        this.logger.info("sync retention: removed {} audit rows and {} idempotency keys", audit, keys);
    }

    public record StoredResponse(int responseStatus, String responseBody) {
    }
}

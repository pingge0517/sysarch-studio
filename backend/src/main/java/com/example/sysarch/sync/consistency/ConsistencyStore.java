package com.example.sysarch.sync.consistency;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Webhook queue, pending checks and elements waiting to be claimed (tables of {@code 002-consistency.xml}). */
@Repository
public class ConsistencyStore {

    public static final String CREATED = "workitem.created";

    public static final String UPDATED = "workitem.updated";

    public static final String DELETED = "workitem.deleted";

    private final JdbcClient jdbc;

    public ConsistencyStore(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    // ---- webhook queue

    public void enqueue(String projectId, String event, String workItemId, String revision) {
        this.jdbc.sql("INSERT INTO polarion_event (project_id, event, work_item_id, revision) VALUES (:project, :event, :workItem, :revision)")
                .param("project", projectId).param("event", event).param("workItem", workItemId).param("revision", revision).update();
    }

    public List<QueuedEvent> unprocessed(int limit) {
        return this.jdbc.sql("SELECT id, project_id, event, work_item_id, revision FROM polarion_event WHERE processed_at IS NULL ORDER BY id LIMIT :limit")
                .param("limit", limit).query(QueuedEvent.class).list();
    }

    /**
     * Consumes one event: marks its Work Item for the next report to judge (spec T8 step 3). The latest event of a Work
     * Item wins. A created Work Item needs no mark: it is not bound yet, and pull puts it in the unbound pool.
     */
    @Transactional
    public void consume(QueuedEvent event) {
        if (!CREATED.equals(event.event())) {
            this.jdbc.sql("DELETE FROM polarion_pending_check WHERE project_id = :project AND work_item_id = :workItem")
                    .param("project", event.projectId()).param("workItem", event.workItemId()).update();
            this.jdbc.sql("INSERT INTO polarion_pending_check (project_id, work_item_id, event, revision) VALUES (:project, :workItem, :event, :revision)")
                    .param("project", event.projectId()).param("workItem", event.workItemId()).param("event", event.event())
                    .param("revision", event.revision()).update();
        }
        this.jdbc.sql("UPDATE polarion_event SET processed_at = :now WHERE id = :id").param("now", OffsetDateTime.now()).param("id", event.id()).update();
    }

    // ---- pending checks

    /** Work Item id to the latest event Polarion reported for it. */
    public Map<String, String> pendingChecks(String projectId) {
        Map<String, String> checks = new LinkedHashMap<>();
        this.jdbc.sql("SELECT work_item_id, event FROM polarion_pending_check WHERE project_id = :project ORDER BY work_item_id")
                .param("project", projectId).query((rs, i) -> checks.put(rs.getString(1), rs.getString(2))).list();
        return checks;
    }

    /** A report that looked the Work Item up in Polarion has judged the mark. */
    public void clearCheck(String projectId, String workItemId) {
        this.jdbc.sql("DELETE FROM polarion_pending_check WHERE project_id = :project AND work_item_id = :workItem")
                .param("project", projectId).param("workItem", workItemId).update();
    }

    // ---- pending claims

    public void addPendingClaim(String elementId, String projectId, String formerWorkItemId, String reason) {
        this.jdbc.sql("DELETE FROM element_pending_claim WHERE element_id = :element").param("element", elementId).update();
        this.jdbc.sql("INSERT INTO element_pending_claim (element_id, project_id, former_work_item_id, reason) VALUES (:element, :project, :workItem, :reason)")
                .param("element", elementId).param("project", projectId).param("workItem", formerWorkItemId).param("reason", reason).update();
    }

    /** Element id to the Work Item it was bound to. */
    public Map<String, String> pendingClaims(String projectId) {
        Map<String, String> claims = new LinkedHashMap<>();
        this.jdbc.sql("SELECT element_id, former_work_item_id FROM element_pending_claim WHERE project_id = :project ORDER BY element_id")
                .param("project", projectId).query((rs, i) -> claims.put(rs.getString(1), rs.getString(2))).list();
        return claims;
    }

    /** Processed webhook events are kept a week for diagnosis. */
    @Scheduled(cron = "${sysarch.polarion.retention-cron:0 30 3 * * *}")
    public void applyRetention() {
        this.jdbc.sql("DELETE FROM polarion_event WHERE processed_at < :cutoff").param("cutoff", OffsetDateTime.now().minusDays(7)).update();
    }

    public record QueuedEvent(long id, String projectId, String event, String workItemId, String revision) {
    }
}

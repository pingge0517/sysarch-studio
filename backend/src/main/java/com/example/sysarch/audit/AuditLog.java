package com.example.sysarch.audit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The append-only audit log (P2 §7.2, table {@code audit_log}, change set 008): who did what to which element, when.
 * Nothing here updates or deletes a row; on PostgreSQL a trigger (change set 016) refuses any UPDATE, and any DELETE
 * except of rows older than 365 days by the archive script ({@code ops/backup/audit-archive.sh}), so the application
 * could not remove a row even if it tried.
 *
 * <p>Sensitive actions (T8 step 2): {@value #LOCK_FORCE_RELEASE}, {@value #MERGE}, {@value #BASELINE_DELETE},
 * {@value #REPORT_GENERATE}; also {@value #ACCESS_DENIED} (every refused action), branch abandonment, imports and
 * changes of the role mapping.
 */
@Component
public class AuditLog {

    public static final String LOCK_FORCE_RELEASE = "LOCK_FORCE_RELEASE";

    public static final String MERGE = "MERGE";

    public static final String BASELINE_DELETE = "BASELINE_DELETE";

    public static final String REPORT_GENERATE = "REPORT_GENERATE";

    public static final String ACCESS_DENIED = "ACCESS_DENIED";

    public static final String BRANCH_ABANDON = "BRANCH_ABANDON";

    public static final String IMPORT = "IMPORT";

    public static final String ROLE_MAPPING_CHANGE = "ROLE_MAPPING_CHANGE";

    public record Entry(long id, String projectId, String actor, String action, String elementId, String detail, OffsetDateTime createdAt) {
    }

    private final JdbcClient jdbc;

    public AuditLog(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public void append(String projectId, String actor, String action, String elementId, String detail) {
        this.jdbc.sql("INSERT INTO audit_log (project_id, actor, action, element_id, detail) VALUES (:p, :a, :action, :e, :d)")
                .param("p", projectId).param("a", actor == null ? "anonymous" : actor).param("action", action).param("e", elementId).param("d", detail)
                .update();
    }

    /** The newest entries first, optionally of one project and/or one action, at most {@code limit}. */
    public List<Entry> recent(String projectId, String action, int limit) {
        StringBuilder sql = new StringBuilder("SELECT id, project_id, actor, action, element_id, detail, created_at FROM audit_log WHERE 1 = 1");
        if (projectId != null) {
            sql.append(" AND project_id = :p");
        }
        if (action != null) {
            sql.append(" AND action = :action");
        }
        sql.append(" ORDER BY created_at DESC, id DESC");
        var statement = this.jdbc.sql(sql.toString());
        if (projectId != null) {
            statement = statement.param("p", projectId);
        }
        if (action != null) {
            statement = statement.param("action", action);
        }
        return statement.query((rs, n) -> new Entry(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getObject(7, OffsetDateTime.class))).stream().limit(Math.max(1, Math.min(limit, 1000))).toList();
    }
}

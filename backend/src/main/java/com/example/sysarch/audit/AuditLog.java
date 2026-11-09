package com.example.sysarch.audit;

import java.util.Objects;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The append-only audit log (P2 §7.2, table {@code audit_log}, change set 008): who did what to which element, when.
 * Nothing here updates or deletes a row. T1 writes forced lock releases; T8 adds the other sensitive actions.
 */
@Component
public class AuditLog {

    public static final String LOCK_FORCE_RELEASE = "LOCK_FORCE_RELEASE";

    private final JdbcClient jdbc;

    public AuditLog(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public void append(String projectId, String actor, String action, String elementId, String detail) {
        this.jdbc.sql("INSERT INTO audit_log (project_id, actor, action, element_id, detail) VALUES (:p, :a, :action, :e, :d)")
                .param("p", projectId).param("a", actor == null ? "anonymous" : actor).param("action", action).param("e", elementId).param("d", detail)
                .update();
    }
}

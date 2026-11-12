package com.example.sysarch.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * P4 T5 (spec P4 §4.2): SysArch's own gauges for monitoring and alerts, read from the database when Prometheus scrapes
 * (no state kept here): {@code sysarch_sync_schedules_paused} (scheduled pulls paused after repeated failures),
 * {@code sysarch_edit_sessions_open}, {@code sysarch_locks_held}, {@code sysarch_report_jobs_waiting} (queued or
 * running), {@code sysarch_import_jobs_failed_24h}. A failing query gives NaN, never an error on the scrape.
 */
@Component
public class SysarchMetrics implements MeterBinder {

    private final JdbcClient jdbc;

    public SysarchMetrics(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        this.gauge(registry, "sysarch.sync.schedules.paused", "Scheduled Polarion pulls paused after repeated failures",
                "SELECT COUNT(*) FROM sync_status WHERE schedule_paused");
        this.gauge(registry, "sysarch.edit.sessions.open", "Open edit sessions", "SELECT COUNT(*) FROM edit_session WHERE closed_at IS NULL");
        Gauge.builder("sysarch.locks.held", this, m -> m.count("SELECT COUNT(*) FROM edit_lock WHERE expires_at > :now", true))
                .description("Element locks held").register(registry);
        this.gauge(registry, "sysarch.report.jobs.waiting", "Report jobs queued or running",
                "SELECT COUNT(*) FROM report_job WHERE status IN ('QUEUED', 'RUNNING')");
        Gauge.builder("sysarch.import.jobs.failed.24h", this,
                m -> m.count("SELECT COUNT(*) FROM import_job WHERE status = 'FAILED' AND created_at > :since", false))
                .description("Import jobs that failed in the last 24 hours").register(registry);
    }

    private void gauge(MeterRegistry registry, String name, String description, String sql) {
        Gauge.builder(name, this, m -> m.count(sql, null)).description(description).register(registry);
    }

    private double count(String sql, Boolean now) {
        try {
            var statement = this.jdbc.sql(sql);
            if (Boolean.TRUE.equals(now)) {
                statement = statement.param("now", OffsetDateTime.now(ZoneOffset.UTC));
            } else if (Boolean.FALSE.equals(now)) {
                statement = statement.param("since", OffsetDateTime.now(ZoneOffset.UTC).minusHours(24));
            }
            return statement.query(Long.class).single();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}

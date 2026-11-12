package com.example.sysarch.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;

import liquibase.integration.spring.SpringLiquibase;

/** P4 T5: SysArch's gauges, read from the database at each scrape. */
class SysarchMetricsTest {

    @Test
    void theGaugesCountWhatIsInTheDatabase() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/sysarch/changelog.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new SysarchMetrics(jdbc).bindTo(registry);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        assertEquals(0, registry.get("sysarch.edit.sessions.open").gauge().value());

        jdbc.sql("INSERT INTO sync_status (project_id, schedule_failures, schedule_paused) VALUES ('sysarch', 3, TRUE)").update();
        jdbc.sql("INSERT INTO edit_session (id, project_id, user_id, opened_at, last_active_at) VALUES ('s1', 'p', 'alice', :t, :t)").param("t", now).update();
        jdbc.sql("INSERT INTO edit_lock (id, project_id, element_id, owner_user, acquired_at, expires_at) VALUES ('l1', 'p', 'e1', 'alice', :t, :e), "
                + "('l2', 'p', 'e2', 'bob', :t, :old)").param("t", now).param("e", now.plusMinutes(1)).param("old", now.minusMinutes(1)).update();
        jdbc.sql("INSERT INTO report_job (id, project_id, source_label, template_id, cache_key, status, requested_by, created_at) VALUES "
                + "('r1', 'p', 'x', 'srs', 'k1', 'QUEUED', 'a', :t), ('r2', 'p', 'x', 'srs', 'k2', 'DONE', 'a', :t)").param("t", now).update();

        assertEquals(1, registry.get("sysarch.sync.schedules.paused").gauge().value());
        assertEquals(1, registry.get("sysarch.edit.sessions.open").gauge().value());
        assertEquals(1, registry.get("sysarch.locks.held").gauge().value(), "the expired lock does not count");
        assertEquals(1, registry.get("sysarch.report.jobs.waiting").gauge().value());
        assertEquals(0, registry.get("sysarch.import.jobs.failed.24h").gauge().value());

        jdbc.sql("DROP TABLE sync_status").update();
        assertTrue(Double.isNaN(registry.get("sysarch.sync.schedules.paused").gauge().value()), "a failing query is NaN, not an error");
    }
}

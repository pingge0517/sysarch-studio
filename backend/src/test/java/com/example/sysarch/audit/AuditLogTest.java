package com.example.sysarch.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import liquibase.integration.spring.SpringLiquibase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reading the audit log must give its pooled connection back (found by the end-to-end test, 2026-11-13: every call of
 * {@code GET /api/v1/audit} kept one connection, ten calls exhausted the pool and every database access then hung).
 */
class AuditLogTest {

    private HikariDataSource dataSource;

    private AuditLog audit;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        config.setMaximumPoolSize(2);
        config.setConnectionTimeout(1000);
        this.dataSource = new HikariDataSource(config);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(this.dataSource);
        liquibase.setChangeLog("classpath:db/sysarch/changelog.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        this.audit = new AuditLog(JdbcClient.create(this.dataSource));
        for (int i = 0; i < 5; i++) {
            this.audit.append("p" + (i % 2), "alice", i % 2 == 0 ? AuditLog.MERGE : AuditLog.IMPORT, "E-" + i, "entry " + i);
        }
    }

    @AfterEach
    void tearDown() {
        this.dataSource.close();
    }

    @Test
    void readingTheLogGivesItsConnectionBack() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            for (int i = 0; i < 20; i++) { // ten times the pool size
                this.audit.recent(null, null, 200);
                this.audit.recent("p0", null, 1);
            }
            assertEquals(0, this.dataSource.getHikariPoolMXBean().getActiveConnections(), "every connection is back in the pool");
            this.audit.append(null, "bob", AuditLog.IMPORT, null, "still writable");
        });
    }

    @Test
    void filtersAndLimitStillApply() {
        List<AuditLog.Entry> newest = this.audit.recent(null, null, 2);
        assertEquals(List.of("entry 4", "entry 3"), newest.stream().map(AuditLog.Entry::detail).toList(), "newest first, at most the limit");
        assertEquals(3, this.audit.recent("p0", null, 200).size());
        assertEquals(2, this.audit.recent(null, AuditLog.IMPORT, 200).size());
        assertEquals(1, this.audit.recent("p0", AuditLog.MERGE, 1).size());
        assertEquals(1, this.audit.recent(null, null, 0).size(), "a limit below 1 reads one");
    }
}

package com.example.sysarch.session;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.identity.Editors.Editor;
import com.example.sysarch.lock.EditAdmission;
import com.example.sysarch.lock.LockEnforcement;
import com.example.sysarch.lock.LockService;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.SysarchFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import liquibase.integration.spring.SpringLiquibase;

/**
 * P2 T2 (spec P2 §2.1, T2 steps 1–3): one session per user and project, opened by editing (user's decision), at most
 * 30 per project ("当前项目编辑人数已满"), closed after 30 minutes without an edit; anonymous edits take no place and are
 * refused when the project is full (user's decision); sessions and locks are separate.
 */
class SessionTest {

    private static final String PROJECT = "sirius-1";

    private final MovableClock clock = new MovableClock();

    private JdbcClient jdbc;

    private SessionService sessions;

    private LockService locks;

    private EditAdmission admission;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/sysarch/changelog.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        this.jdbc = JdbcClient.create(dataSource);
        this.sessions = new SessionService(this.jdbc, 30, Duration.ofMinutes(30), this.clock);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.locks = new LockService(this.jdbc, transaction, new AuditLog(this.jdbc), this.clock);
        com.example.sysarch.access.Access access = new com.example.sysarch.access.Access(new com.example.sysarch.access.RoleMappings(this.jdbc),
                new com.example.sysarch.access.ProjectBindings(new com.example.sysarch.sync.SyncProperties(), this.jdbc,
                        new com.example.sysarch.branch.BranchWorkspaces(this.jdbc)), new AuditLog(this.jdbc));
        this.admission = new EditAdmission(new LockEnforcement(this.locks, this.jdbc), this.sessions, access);
    }

    @Test
    void editingOpensOneSessionPerUserAndKeepsIt() {
        SessionService.Session first = this.sessions.touch(PROJECT, "alice", "Sirius Web");
        this.clock.advance(Duration.ofMinutes(10));
        SessionService.Session again = this.sessions.touch(PROJECT, "alice", "Sirius Web");

        assertEquals(first.id(), again.id());
        assertEquals(this.clock.instant(), again.lastActiveAt().toInstant(), "the edit kept it active");
        assertNotEquals(first.id(), this.sessions.touch("sirius-2", "alice", null).id(), "one per project");
        assertEquals(1, this.sessions.list(PROJECT).size());
    }

    /** T2 step 2: the 31st user is refused with "当前项目编辑人数已满"; the 30 keep editing. */
    @Test
    void theThirtyFirstUserIsRefused() {
        for (int i = 1; i <= 30; i++) {
            this.sessions.touch(PROJECT, "user-" + i, null);
        }
        SessionService.FullException full = assertThrows(SessionService.FullException.class, () -> this.sessions.touch(PROJECT, "user-31", null));
        assertEquals("当前项目编辑人数已满（30/30），请稍后再试", full.getMessage());
        assertEquals(30, full.limit());

        this.clock.advance(Duration.ofMinutes(1));
        assertDoesNotThrow(() -> this.sessions.touch(PROJECT, "user-7", null), "people who are in stay in");
        assertDoesNotThrow(() -> this.sessions.touch("sirius-2", "user-31", null), "the limit is per project");
    }

    /** T2 step 1: 30 minutes without an edit closes a session and frees its place. */
    @Test
    void anIdleSessionClosesAfterThirtyMinutes() {
        this.sessions.touch(PROJECT, "idle", null);
        this.clock.advance(Duration.ofMinutes(5));
        for (int i = 1; i <= 29; i++) {
            this.sessions.touch(PROJECT, "user-" + i, null);
        }
        assertThrows(SessionService.FullException.class, () -> this.sessions.touch(PROJECT, "late", null));

        this.clock.advance(Duration.ofMinutes(25).plusSeconds(1)); // "idle" last edited 30 min 1 s ago, the others 25 min 1 s
        assertDoesNotThrow(() -> this.sessions.touch(PROJECT, "late", null));
        assertTrue(this.sessions.list(PROJECT).stream().noneMatch(s -> s.userId().equals("idle")));

        SessionService.Session back = this.sessions.touch(PROJECT, "user-1", null);
        assertEquals("user-1", back.userId(), "within 30 min: the same session goes on");
    }

    @Test
    void theSweepClosesIdleSessions() {
        this.sessions.touch(PROJECT, "alice", null);
        this.clock.advance(Duration.ofMinutes(31));
        assertEquals(1, this.sessions.sweep());
        assertNotNull(this.jdbc.sql("SELECT closed_at FROM edit_session").query(java.time.OffsetDateTime.class).single());
    }

    @Test
    void closingFreesThePlaceAndOnlyItsUserMayClose() {
        SessionService.Session alice = this.sessions.touch(PROJECT, "alice", null);
        for (int i = 1; i <= 29; i++) {
            this.sessions.touch(PROJECT, "user-" + i, null);
        }
        assertThrows(SessionService.NoSuchSessionException.class, () -> this.sessions.close(alice.id(), "bob"));
        this.sessions.close(alice.id(), "alice");

        assertDoesNotThrow(() -> this.sessions.touch(PROJECT, "bob", null));
        SessionService.Session again = this.sessions.touch(PROJECT, "user-1", null);
        assertNotNull(again);
        assertThrows(SessionService.FullException.class, () -> this.sessions.touch(PROJECT, "alice", null), "alice left; now it is full");
    }

    /** User's decision: anonymous edits take no place, and are refused while the project is full. */
    @Test
    void anonymousEditsTakeNoPlaceAndAreRefusedWhenFull() {
        assertDoesNotThrow(() -> this.sessions.checkAnonymous(PROJECT));
        assertEquals(0, this.sessions.list(PROJECT).size());
        for (int i = 1; i <= 30; i++) {
            this.sessions.touch(PROJECT, "user-" + i, null);
        }
        assertThrows(SessionService.FullException.class, () -> this.sessions.checkAnonymous(PROJECT));
    }

    /** 40 users at the same instant: exactly 30 get in. */
    @Test
    void concurrentNewcomersNeverExceedTheLimit() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            String user = "user-" + i;
            results.add(pool.submit(() -> {
                start.await();
                try {
                    this.sessions.touch(PROJECT, user, null);
                    return true;
                } catch (SessionService.FullException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int in = 0;
        for (Future<Boolean> result : results) {
            in += result.get() ? 1 : 0;
        }
        pool.shutdown();
        assertEquals(30, in);
        assertEquals(30, this.sessions.list(PROJECT).size());
    }

    // =========================================================== admission: locks first, then the limit; separate

    @Test
    void admissionChecksLocksThenTheLimit() {
        PartDefinition block = SysarchFactory.eINSTANCE.createPartDefinition();
        block.setId("PD-001");
        block.setName("块");
        Resource resource = new ResourceImpl(URI.createURI("memory:/m.json"));
        new ResourceSetImpl().getResources().add(resource);
        resource.getContents().add(block);
        ChangeRecorder recorder = new ChangeRecorder(resource.getResourceSet());
        block.setName("块 v2");
        ChangeDescription rename = recorder.endRecording();
        recorder.dispose();

        this.locks.acquire(PROJECT, List.of("PD-001"), "alice", null);
        assertThrows(LockService.LockedException.class, () -> this.admission.check(PROJECT, new Editor("bob"), rename, null), "the lock decides first");
        assertEquals(0, this.sessions.list(PROJECT).size(), "a refused edit opens no session");

        this.admission.check(PROJECT, new Editor("alice"), rename, "Sirius Web");
        assertEquals("Sirius Web", this.sessions.list(PROJECT).get(0).clientInfo());
        assertEquals(1, this.locks.list(PROJECT).size(), "opening a session took no lock and released none");

        for (int i = 1; i <= 29; i++) {
            this.sessions.touch(PROJECT, "user-" + i, null);
        }
        this.locks.release(PROJECT, "PD-001", "alice", false);
        SessionService.FullException full = assertThrows(SessionService.FullException.class,
                () -> this.admission.check(PROJECT, new Editor("bob"), rename, null));
        assertEquals(EditAdmission.SESSIONS_FULL, EditAdmission.reasons(full).get(0).code());
        // P2 T8: an anonymous edit is refused by its role (observer) before any session question
        assertThrows(com.example.sysarch.access.ForbiddenException.class, () -> this.admission.check(PROJECT, Editor.ANONYMOUS, rename, null));
        assertDoesNotThrow(() -> this.admission.check(PROJECT, new Editor("alice"), rename, null), "alice has her session");
    }

    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-11-09T06:00:00Z");

        void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.now;
        }
    }
}

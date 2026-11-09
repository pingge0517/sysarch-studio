package com.example.sysarch.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.identity.Editors;
import com.example.sysarch.identity.GraphQLEditorFilter;
import com.example.sysarch.identity.Editors.Editor;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.SysarchFactory;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import liquibase.integration.spring.SpringLiquibase;

/**
 * P2 T1 acceptance (P2 §2.2–2.3, P2-AC-1, P2-AC-2): "两个客户端同时编辑同一元素，第二个返回 423；停心跳 90 秒后锁自动释放",
 * on H2 with the application's change sets and a clock the test moves; plus the server-side enforcement on real EMF
 * edits (user's decisions: veto with the holder named, lease on edit).
 */
class LockTest {

    private static final String PROJECT = "sirius-1";

    private final MovableClock clock = new MovableClock();

    private JdbcClient jdbc;

    private LockService locks;

    private LockEnforcement enforcement;

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
        this.locks = new LockService(this.jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), new AuditLog(this.jdbc), this.clock);
        this.enforcement = new LockEnforcement(this.locks, this.jdbc);
    }

    // =========================================================== acceptance

    /** P2-AC-1: the second client is refused with the holder, and gets nothing. */
    @Test
    void theSecondClientOnTheSameElementIsRefusedWithTheHolder() {
        List<LockService.Lock> alice = this.locks.acquire(PROJECT, List.of("PD-001"), "alice", null);
        assertEquals(1, alice.size());
        assertEquals(LockService.DEFAULT_LEASE, alice.get(0).leaseSeconds());

        LockService.LockedException refused = assertThrows(LockService.LockedException.class,
                () -> this.locks.acquire(PROJECT, List.of("PD-001"), "bob", null));
        assertEquals("alice", refused.conflicts().get(0).ownerUser());
        assertTrue(refused.getMessage().startsWith("PD-001 已被 alice 锁定（至 "), refused.getMessage());
    }

    /** P2-AC-2: without heartbeat the lock is free after its 90 s lease, before or without the sweep. */
    @Test
    void aLockWithoutHeartbeatIsFreeAfterItsLease() {
        this.locks.acquire(PROJECT, List.of("PD-001"), "alice", null);
        this.clock.advance(Duration.ofSeconds(89));
        assertThrows(LockService.LockedException.class, () -> this.locks.acquire(PROJECT, List.of("PD-001"), "bob", null));

        this.clock.advance(Duration.ofSeconds(2));
        assertEquals(List.of(), this.locks.list(PROJECT));
        assertEquals("bob", this.locks.acquire(PROJECT, List.of("PD-001"), "bob", null).get(0).ownerUser(), "no sweep needed");
    }

    @Test
    void theHeartbeatKeepsTheLock() {
        String id = this.locks.acquire(PROJECT, List.of("PD-001"), "alice", null).get(0).id();
        for (int i = 0; i < 4; i++) {
            this.clock.advance(Duration.ofSeconds(45));
            this.locks.renew(id, "alice");
        }
        assertThrows(LockService.LockedException.class, () -> this.locks.acquire(PROJECT, List.of("PD-001"), "bob", null), "held for 180 s");
        assertThrows(LockService.NotOwnerException.class, () -> this.locks.renew(id, "bob"));

        this.clock.advance(Duration.ofSeconds(91));
        assertThrows(LockService.NoSuchLockException.class, () -> this.locks.renew(id, "alice"), "a missed heartbeat cannot revive it");
    }

    @Test
    void theSweepRemovesExpiredRowsOnly() {
        this.locks.acquire(PROJECT, List.of("PD-001"), "alice", 30);
        this.locks.acquire(PROJECT, List.of("PD-002"), "alice", 300);
        this.clock.advance(Duration.ofSeconds(31));

        assertEquals(1, this.locks.sweep());
        assertEquals(1, this.jdbc.sql("SELECT COUNT(*) FROM edit_lock").query(Integer.class).single());
    }

    // =========================================================== batches

    /** All or nothing: one element held by someone else fails the batch, and nothing of it is taken. */
    @Test
    void aBatchIsAllOrNothing() {
        this.locks.acquire(PROJECT, List.of("PD-002"), "alice", null);

        LockService.LockedException refused = assertThrows(LockService.LockedException.class,
                () -> this.locks.acquire(PROJECT, List.of("PD-001", "PD-002", "PD-003"), "bob", null));
        assertEquals(List.of("PD-002"), refused.conflicts().stream().map(LockService.Conflict::elementId).toList());
        assertEquals(List.of("PD-002"), this.locks.list(PROJECT).stream().map(LockService.Lock::elementId).toList(), "PD-001 and PD-003 not taken");
    }

    @Test
    void takingALockYouHoldRenewsIt() {
        String id = this.locks.acquire(PROJECT, List.of("PD-001"), "alice", 60).get(0).id();
        this.clock.advance(Duration.ofSeconds(50));
        LockService.Lock again = this.locks.acquire(PROJECT, List.of("PD-001", "PD-002"), "alice", 120).get(0);

        assertEquals(id, again.id(), "the same lock, not a second row");
        assertEquals(this.clock.instant().plusSeconds(120), again.expiresAt().toInstant());
    }

    @Test
    void limitsAreChecked() {
        List<String> fiftyOne = IntStream.rangeClosed(1, 51).mapToObj(i -> "E-" + i).toList();
        assertThrows(LockService.InvalidLockRequestException.class, () -> this.locks.acquire(PROJECT, fiftyOne, "alice", null));
        assertEquals(50, this.locks.acquire(PROJECT, fiftyOne.subList(0, 50), "alice", null).size());
        assertThrows(LockService.InvalidLockRequestException.class, () -> this.locks.acquire(PROJECT, List.of("X"), "alice", 29));
        assertThrows(LockService.InvalidLockRequestException.class, () -> this.locks.acquire(PROJECT, List.of("X"), "alice", 301));
        assertThrows(LockService.InvalidLockRequestException.class, () -> this.locks.acquire(PROJECT, List.of(), "alice", null));
    }

    @Test
    void locksArePerProject() {
        this.locks.acquire(PROJECT, List.of("PD-001"), "alice", null);
        assertEquals("bob", this.locks.acquire("sirius-2", List.of("PD-001"), "bob", null).get(0).ownerUser());
    }

    /** Two clients at the same instant: exactly one gets the element. */
    @Test
    void concurrentRequestsForTheSameElementHaveOneWinner() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String user = "user-" + i;
            Callable<String> attempt = () -> {
                start.await();
                try {
                    return this.locks.acquire(PROJECT, List.of("PD-001", "PD-002"), user, null).get(0).ownerUser();
                } catch (LockService.LockedException e) {
                    return null;
                }
            };
            results.add(pool.submit(attempt));
        }
        start.countDown();
        List<String> winners = new ArrayList<>();
        for (Future<String> result : results) {
            String winner = result.get();
            if (winner != null) {
                winners.add(winner);
            }
        }
        pool.shutdown();
        assertEquals(1, winners.size(), String.valueOf(winners));
        assertEquals(2, this.locks.list(PROJECT).size());
        assertTrue(this.locks.list(PROJECT).stream().allMatch(l -> l.ownerUser().equals(winners.get(0))), "both elements, one owner");
    }

    // =========================================================== release

    @Test
    void onlyTheOwnerReleasesUnlessForcedAndAForcedReleaseIsAudited() {
        this.locks.acquire(PROJECT, List.of("PD-001", "PD-002"), "alice", null);

        assertThrows(LockService.NotOwnerException.class, () -> this.locks.release(PROJECT, "PD-001", "bob", false));
        assertTrue(this.locks.release(PROJECT, "PD-001", "alice", false));
        assertFalse(this.locks.release(PROJECT, "PD-001", "alice", false), "already free: not an error");
        assertEquals(0, this.audits(), "an owner's release is not audited");

        assertTrue(this.locks.release(PROJECT, "PD-002", "admin", true));
        Map<String, Object> audit = this.jdbc.sql("SELECT actor, action, element_id, detail FROM audit_log").query().singleRow();
        assertEquals("admin", audit.get("actor"));
        assertEquals(AuditLog.LOCK_FORCE_RELEASE, audit.get("action"));
        assertEquals("PD-002", audit.get("element_id"));
        assertTrue(audit.get("detail").toString().contains("of alice"));
        assertEquals("bob", this.locks.acquire(PROJECT, List.of("PD-002"), "bob", null).get(0).ownerUser());
    }

    // =========================================================== enforcement on model edits

    @Test
    void anEditOfAnElementSomeoneElseHoldsConflictsAndTheHolderMayEdit() {
        Fixture model = new Fixture();
        this.locks.acquire(PROJECT, List.of("PD-002"), "alice", null);

        ChangeDescription rename = model.record(() -> model.block("PD-002").setName("融合单元 v2"));

        List<LockService.Conflict> conflicts = this.enforcement.conflicts(PROJECT, new Editor("bob"), rename);
        assertEquals(List.of("PD-002"), conflicts.stream().map(LockService.Conflict::elementId).toList());
        assertEquals("alice", conflicts.get(0).ownerUser());
        assertEquals(List.of(), this.enforcement.conflicts(PROJECT, new Editor("alice"), rename));
        assertEquals(1, this.enforcement.conflicts(PROJECT, Editor.ANONYMOUS, rename).size(), "an anonymous edit respects every lock");
    }

    /** "编辑即自动加锁": an edit that went through leaves its user a 90 s lease on what it changed. */
    @Test
    void anEditThatWentThroughGivesItsUserALease() {
        Fixture model = new Fixture();
        ChangeDescription rename = model.record(() -> model.block("PD-002").setName("融合单元 v2"));

        this.enforcement.committed(PROJECT, new Editor("bob"), rename);

        LockService.Lock lease = this.locks.list(PROJECT).get(0);
        assertEquals("PD-002", lease.elementId());
        assertEquals("bob", lease.ownerUser());
        assertEquals(this.clock.instant().plusSeconds(90), lease.expiresAt().toInstant());
        assertThrows(LockService.LockedException.class, () -> this.locks.acquire(PROJECT, List.of("PD-002"), "alice", null));

        this.enforcement.committed(PROJECT, Editor.ANONYMOUS, model.record(() -> model.block("PD-003").setName("x")));
        assertEquals(1, this.locks.list(PROJECT).size(), "an anonymous edit takes no lock");
    }

    /** A longer lease taken explicitly is not shortened by editing. */
    @Test
    void editingDoesNotShortenAnExplicitLease() {
        Fixture model = new Fixture();
        this.locks.acquire(PROJECT, List.of("PD-002"), "bob", 300);
        this.enforcement.committed(PROJECT, new Editor("bob"), model.record(() -> model.block("PD-002").setName("v2")));
        assertEquals(this.clock.instant().plusSeconds(300), this.locks.list(PROJECT).get(0).expiresAt().toInstant());
    }

    /** Adding a part changes its block; the new part itself is new and protected by nobody. */
    @Test
    void creatingUnderABlockChangesTheBlockOnly() {
        Fixture model = new Fixture();
        ChangeDescription add = model.record(() -> {
            Part part = SysarchFactory.eINSTANCE.createPart();
            part.setId("PT-NEW");
            part.setName("新部件");
            model.block("PD-001").getOwnedParts().add(part);
        });
        assertEquals(java.util.Set.of("PD-001"), LockEnforcement.protectedElements(add));
    }

    /** A deleted element is protected by its lock, and its lock ends with it (P2 §2.3). */
    @Test
    void deletingAnElementNeedsItsLockAndEndsIt() {
        Fixture model = new Fixture();
        this.locks.acquire(PROJECT, List.of("PD-006"), "alice", null);
        ChangeDescription delete = model.record(() -> EcoreUtil.delete(model.block("PD-006"), true));

        assertTrue(LockEnforcement.protectedElements(delete).contains("PD-006"));
        assertEquals("alice", this.enforcement.conflicts(PROJECT, new Editor("bob"), delete).get(0).ownerUser());

        this.enforcement.committed(PROJECT, new Editor("alice"), delete);
        assertTrue(this.locks.list(PROJECT).stream().noneMatch(l -> l.elementId().equals("PD-006")), "the lock went with the element");
    }

    // =========================================================== identity of Sirius inputs

    @Test
    void theFilterReadsTheMutationsInputId() {
        String mutation = "{\"query\":\"mutation editTextfield($input: EditTextfieldInput!) { editTextfield(input: $input) { __typename } }\","
                + "\"variables\":{\"input\":{\"id\":\"8f1c0000-0000-4000-8000-000000000001\",\"editingContextId\":\"ec\",\"newValue\":\"x\"}}}";
        assertEquals("8f1c0000-0000-4000-8000-000000000001",
                GraphQLEditorFilter.inputId(mutation.getBytes(StandardCharsets.UTF_8)));
        assertNull(GraphQLEditorFilter.inputId(
                "{\"query\":\"query { viewer { id } }\",\"variables\":{\"input\":{\"id\":\"x\"}}}".getBytes(StandardCharsets.UTF_8)), "queries change nothing");
        assertNull(GraphQLEditorFilter.inputId("not json".getBytes(StandardCharsets.UTF_8)));

        Editors editors = new Editors();
        editors.record("in-1", new Editor("alice"));
        assertEquals("alice", editors.of("in-1").map(Editor::user).orElseThrow());
        assertTrue(editors.of("never-recorded").isEmpty(), "a system input");
    }

    // =========================================================== helpers

    private int audits() {
        return this.jdbc.sql("SELECT COUNT(*) FROM audit_log").query(Integer.class).single();
    }

    /** The domain-controller demo in a resource set, with recorded edits. */
    private static final class Fixture {

        private final ResourceSet resourceSet = new ResourceSetImpl();

        private final Resource resource = new org.eclipse.emf.ecore.resource.impl.ResourceImpl(URI.createURI("memory:/demo.json"));

        Fixture() {
            this.resourceSet.getResources().add(this.resource);
            this.resource.getContents().addAll(DomainControllerDemo.build());
        }

        PartDefinition block(String id) {
            return (PartDefinition) this.resource.getContents().stream().filter(e -> e instanceof PartDefinition p && id.equals(p.getId())).findFirst()
                    .orElseThrow();
        }

        ChangeDescription record(Runnable edit) {
            ChangeRecorder recorder = new ChangeRecorder(this.resourceSet);
            edit.run();
            ChangeDescription change = recorder.endRecording();
            recorder.dispose();
            return change;
        }
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

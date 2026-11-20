package com.example.sysarch.storage.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import com.example.sysarch.storage.command.DurableCommandGate.*;

class DurableCommandGateTest {
    private JdbcDataSource data;
    private JdbcClient jdbc;
    private DurableCommandGate gate;
    private final UUID epoch = UUID.randomUUID();
    private final Request request = new Request(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64), "verified-fixture");
    @BeforeEach void setup() {
        data = new JdbcDataSource(); data.setURL("jdbc:h2:mem:command-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("db/sysarch/020-model-command-gate.sql")).execute(data);
        jdbc = JdbcClient.create(data); gate = new DurableCommandGate(data);
    }
    private void ready() {
        // Test-only activation. Production activation requires an audited remote reconciliation procedure.
        jdbc.sql("UPDATE model_command_coordinator SET state='READY',epoch=:epoch WHERE singleton=1").param("epoch", epoch.toString()).update();
    }
    private String state() { return jdbc.sql("SELECT state FROM model_command_gate").query(String.class).single(); }

    @Test void freshMissingAndRecoveryRequiredCoordinatorsNeverGrantWrites() {
        assertThrows(Locked.class, () -> gate.acquire(request));
        jdbc.sql("UPDATE model_command_coordinator SET state='RECOVERY_REQUIRED'").update();
        assertThrows(Locked.class, () -> gate.acquire(request));
        jdbc.sql("DELETE FROM model_command_coordinator").update();
        assertThrows(Locked.class, () -> gate.acquire(request));
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM model_command_gate").query(Integer.class).single());
    }
    @Test void restartedInstancesKeepActiveAndUnknownCommandsBlocked() {
        ready(); Ticket ticket = gate.acquire(request);
        var restarted = new DurableCommandGate(data);
        assertThrows(Busy.class, () -> restarted.acquire(request));
        restarted.markUnknown(ticket); assertEquals("UNKNOWN", state());
        assertThrows(Busy.class, () -> new DurableCommandGate(data).acquire(request));
    }
    @Test void distinctInstancesRaceAndExactlyOneWins() throws Exception {
        ready(); var barrier = new CyclicBarrier(4);
        try (var workers = Executors.newFixedThreadPool(4)) {
            java.util.concurrent.Callable<Boolean> acquire = () -> {
                barrier.await();
                try { new DurableCommandGate(data).acquire(request); return true; }
                catch (Busy busy) { return false; }
            };
            int won = 0;
            for (var future : workers.invokeAll(List.of(acquire, acquire, acquire, acquire))) if (future.get()) won++;
            assertEquals(1, won);
        }
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM model_command_gate").query(Integer.class).single());
    }
    @Test void differentModelsCanHaveSeparatePendingCommands() {
        ready(); gate.acquire(request);
        gate.acquire(new Request(UUID.randomUUID(), request.commandId(), request.requestHash(), request.actor()));
        assertEquals(2, jdbc.sql("SELECT COUNT(*) FROM model_command_gate").query(Integer.class).single());
    }
    @Test void missingRemoteRecordAndNetworkFailureDoNotReleaseAnUncertainCommand() {
        ready(); Ticket ticket = gate.acquire(request);
        assertFalse(gate.reconcile(ticket, value -> Outcome.NOT_FOUND)); assertEquals("UNKNOWN", state());
        assertFalse(gate.reconcile(ticket, value -> { throw new IllegalStateException("fixture transport failure"); }));
        assertThrows(Busy.class, () -> gate.acquire(request));
    }
    @Test void interruptedVerificationKeepsUnknownAndPreservesCancellation() {
        ready(); Ticket ticket = gate.acquire(request);
        try {
            assertFalse(gate.reconcile(ticket, value -> { throw new InterruptedException("fixture cancellation"); }));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertEquals("UNKNOWN", state());
        assertThrows(Busy.class, () -> gate.acquire(request));
    }
    @Test void onlyConfirmedRemoteOutcomesReleaseAndStaleTicketsCannotReleaseTheNextAttempt() {
        ready(); Ticket ticket = gate.acquire(request); gate.markUnknown(ticket);
        assertTrue(gate.reconcile(ticket, value -> Outcome.COMMITTED));
        Ticket next = gate.acquire(request);
        assertNotEquals(ticket.attemptId(), next.attemptId());
        assertThrows(LostOwnership.class, () -> gate.reconcile(ticket, value -> fail("Stale ticket must not reach verifier")));
        assertThrows(LostOwnership.class, () -> gate.markUnknown(ticket));
        assertEquals("ACTIVE", state());
        assertTrue(gate.reconcile(next, value -> Outcome.ROLLED_BACK));
    }
    @Test void alteredActorHashAndEpochNeverReachRemoteVerificationOrReleaseAnotherOwner() {
        ready(); Ticket ticket = gate.acquire(request);
        for (Ticket forged : List.of(
                new Ticket(new Request(request.modelUuid(), request.commandId(), request.requestHash(), "other"), ticket.attemptId(), epoch),
                new Ticket(new Request(request.modelUuid(), request.commandId(), "b".repeat(64), request.actor()), ticket.attemptId(), epoch),
                new Ticket(request, ticket.attemptId(), UUID.randomUUID()))) {
            assertThrows(LostOwnership.class, () -> gate.reconcile(forged, value -> fail("Wrong owner")));
        }
        assertEquals("ACTIVE", state());
    }
    @Test void remoteVerificationRunsWithoutDatabaseLockAndRechecksEpochAfterReturning() {
        ready(); Ticket ticket = gate.acquire(request);
        assertThrows(LostOwnership.class, () -> gate.reconcile(ticket, value -> {
            // A separate connection can update this row while verification is in flight.
            jdbc.sql("UPDATE model_command_coordinator SET epoch=:epoch").param("epoch", UUID.randomUUID().toString()).update();
            return Outcome.COMMITTED;
        }));
        assertEquals("ACTIVE", state());
    }
    @Test void databaseFailureAndAmbientTransactionsCannotFallBackToAnInMemoryLock() throws Exception {
        DataSource failing = mock(DataSource.class); when(failing.getConnection()).thenThrow(new SQLException("fixture"));
        assertThrows(Unavailable.class, () -> new DurableCommandGate(failing).acquire(request));
        DataSource ambient = mock(DataSource.class); Connection connection = mock(Connection.class);
        when(ambient.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(false);
        assertThrows(Unavailable.class, () -> new DurableCommandGate(ambient).acquire(request));
        verify(connection, never()).commit();
    }
    @Test void migrationIsRepeatableAndDoesNotResetAPendingCommandOrActivateAFreshDatabase() throws Exception {
        var target = new JdbcDataSource(); target.setURL("jdbc:h2:mem:gate-migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        migrate(target); var sql = JdbcClient.create(target);
        assertEquals("LOCKED", sql.sql("SELECT state FROM model_command_coordinator").query(String.class).single());
        sql.sql("UPDATE model_command_coordinator SET state='READY',epoch=:id").param("id", epoch.toString()).update();
        new DurableCommandGate(target).acquire(request); migrate(target);
        assertThrows(Busy.class, () -> new DurableCommandGate(target).acquire(request));
    }
    private static void migrate(DataSource data) throws Exception {
        try (var accessor = new ClassLoaderResourceAccessor(); var migration = new Liquibase("db/sysarch/020-model-command-gate.xml", accessor, new JdbcConnection(data.getConnection()))) {
            migration.update(new Contexts(), new LabelExpression());
        }
    }
    @Test void requestRequiresVerifiedIdentityAndCanonicalDigest() {
        assertThrows(IllegalArgumentException.class, () -> new Request(request.modelUuid(), request.commandId(), "not-a-hash", request.actor()));
        assertThrows(IllegalArgumentException.class, () -> new Request(request.modelUuid(), request.commandId(), request.requestHash(), "bad\nidentity"));
    }
}

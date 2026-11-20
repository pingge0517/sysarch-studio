package com.example.sysarch.storage.command;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/** Internal M3 foundation, deliberately not a bean or HTTP endpoint. Never stores credentials or model content. */
public final class DurableCommandGate {
    public record Request(UUID modelUuid, UUID commandId, String requestHash, String actor) {
        public Request {
            Objects.requireNonNull(modelUuid); Objects.requireNonNull(commandId);
            if (requestHash == null || !requestHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid request hash");
            if (actor == null || actor.isBlank() || actor.length() > 128 || actor.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Verified actor required");
        }
    }
    public record Ticket(Request request, UUID attemptId, UUID epoch) {
        public Ticket { Objects.requireNonNull(request); Objects.requireNonNull(attemptId); Objects.requireNonNull(epoch); }
    }
    public enum Outcome { COMMITTED, ROLLED_BACK, UNKNOWN, NOT_FOUND }
    /** The production implementation must query durable Polarion evidence as this ticket's verified actor.
     * NOT_FOUND alone never proves rollback. No administrator or service-account fallback is permitted. */
    @FunctionalInterface public interface RemoteVerifier { Outcome verify(Ticket ticket) throws Exception; }
    public static final class Busy extends RuntimeException { public Busy() { super("Model command already pending"); } }
    public static final class Locked extends RuntimeException { public Locked() { super("Command coordinator requires reconciliation"); } }
    public static final class Unavailable extends RuntimeException { public Unavailable() { super("Command coordination unavailable"); } }
    public static final class LostOwnership extends RuntimeException { public LostOwnership() { super("Command ownership changed"); } }

    private final DataSource data;
    public DurableCommandGate(DataSource data) { this.data = Objects.requireNonNull(data); }

    public Ticket acquire(Request request) {
        Objects.requireNonNull(request);
        return transaction(connection -> {
            UUID epoch = readyEpoch(connection);
            var ticket = new Ticket(request, UUID.randomUUID(), epoch);
            try (var sql = connection.prepareStatement("INSERT INTO model_command_gate (model_uuid,attempt_id,command_id,request_hash,actor,coordinator_epoch,state) VALUES (?,?,?,?,?,?,'ACTIVE')")) {
                sql.setQueryTimeout(3); bind(sql, ticket); sql.executeUpdate();
            } catch (SQLException failure) {
                if ("23505".equals(failure.getSQLState())) throw new Busy();
                throw failure;
            }
            return ticket;
        });
    }

    /** Network failure or process loss does not grant another writer access. No lease expiry or automatic deletion. */
    public void markUnknown(Ticket ticket) {
        transaction(connection -> {
            requireEpoch(connection, ticket);
            try (var sql = connection.prepareStatement("UPDATE model_command_gate SET state='UNKNOWN' WHERE " + MATCH)) {
                sql.setQueryTimeout(3); bind(sql, ticket);
                if (sql.executeUpdate() != 1) throw new LostOwnership();
            }
            return null;
        });
    }

    /** Verify outside a database transaction; recheck the exact ticket and epoch before releasing it. */
    public boolean reconcile(Ticket ticket, RemoteVerifier verifier) {
        Objects.requireNonNull(verifier);
        transaction(connection -> { requireOwned(connection, ticket); return null; });
        Outcome result;
        try { result = verifier.verify(ticket); }
        catch (InterruptedException failure) {
            try { markUnknown(ticket); } finally { Thread.currentThread().interrupt(); }
            return false;
        }
        catch (Exception failure) { markUnknown(ticket); return false; }
        if (result != Outcome.COMMITTED && result != Outcome.ROLLED_BACK) { markUnknown(ticket); return false; }
        return transaction(connection -> {
            requireEpoch(connection, ticket);
            try (var sql = connection.prepareStatement("DELETE FROM model_command_gate WHERE " + MATCH)) {
                sql.setQueryTimeout(3); bind(sql, ticket);
                if (sql.executeUpdate() != 1) throw new LostOwnership();
            }
            return true;
        });
    }

    private static final String MATCH = "model_uuid=? AND attempt_id=? AND command_id=? AND request_hash=? AND actor=? AND coordinator_epoch=?";
    private static void bind(java.sql.PreparedStatement sql, Ticket ticket) throws SQLException {
        sql.setString(1, ticket.request.modelUuid.toString()); sql.setString(2, ticket.attemptId.toString());
        sql.setString(3, ticket.request.commandId.toString()); sql.setString(4, ticket.request.requestHash);
        sql.setString(5, ticket.request.actor); sql.setString(6, ticket.epoch.toString());
    }
    private static UUID readyEpoch(Connection connection) throws SQLException {
        try (var sql = connection.prepareStatement("SELECT state,epoch FROM model_command_coordinator WHERE singleton=1 FOR UPDATE")) {
            sql.setQueryTimeout(3);
            try (var rows = sql.executeQuery()) {
                if (!rows.next() || !"READY".equals(rows.getString(1))) throw new Locked();
                try { return UUID.fromString(rows.getString(2)); }
                catch (RuntimeException invalid) { throw new Locked(); }
            }
        }
    }
    private static void requireEpoch(Connection connection, Ticket ticket) throws SQLException {
        if (!readyEpoch(connection).equals(ticket.epoch)) throw new LostOwnership();
    }
    private static void requireOwned(Connection connection, Ticket ticket) throws SQLException {
        requireEpoch(connection, ticket);
        try (var sql = connection.prepareStatement("SELECT state FROM model_command_gate WHERE " + MATCH)) {
            sql.setQueryTimeout(3); bind(sql, ticket);
            try (var rows = sql.executeQuery()) { if (!rows.next()) throw new LostOwnership(); }
        }
    }
    @FunctionalInterface private interface Work<T> { T run(Connection connection) throws SQLException; }
    private <T> T transaction(Work<T> action) {
        // Obtain a new pool connection, never a Spring thread-bound transaction spanning a remote request.
        try (var connection = data.getConnection()) {
            if (!connection.getAutoCommit()) throw new Unavailable();
            connection.setAutoCommit(false);
            try {
                T result = action.run(connection); connection.commit(); return result;
            } catch (RuntimeException | SQLException failure) {
                try { connection.rollback(); } catch (SQLException ignored) { }
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new Unavailable();
            }
        } catch (SQLException failure) { throw new Unavailable(); }
    }
}

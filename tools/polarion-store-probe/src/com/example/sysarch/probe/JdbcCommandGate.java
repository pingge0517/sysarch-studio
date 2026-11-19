package com.example.sysarch.probe;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** M0 shared durable coordination, never the model store. Each statement commits before any Polarion call. */
final class JdbcCommandGate {
    record Ticket(String modelKey, String requestId) { }
    record ModelClaim(String modelUuid, String requestId) { }
    private final String url;
    private final Properties connectionProperties;

    JdbcCommandGate(String file) throws Exception {
        Properties config = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(file), StandardCharsets.UTF_8)) { config.load(reader); }
        this.url = config.getProperty("url");
        if (!"jdbc:postgresql://localhost:5432/sysarch".equals(this.url)) throw new IllegalArgumentException("M0 gate must use SysArch's database, never Polarion's");
        this.connectionProperties = new Properties();
        this.connectionProperties.setProperty("user", config.getProperty("user"));
        this.connectionProperties.setProperty("password", config.getProperty("password"));
        this.connectionProperties.setProperty("connectTimeout", "3");
        this.connectionProperties.setProperty("socketTimeout", "5");
        Class.forName("org.postgresql.Driver");
    }

    private Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection(this.url, this.connectionProperties);
        connection.setAutoCommit(true);
        return connection;
    }

    Ticket acquire(String key, String requestId, String commandId, String actor) throws SQLException {
        try (Connection connection = connection(); var statement = connection.prepareStatement(
                "INSERT INTO sysarch_probe_m0.command_gate (model_key,request_id,command_id,actor,state) "
                + "VALUES (?,?,?,?, 'ACTIVE') ON CONFLICT (model_key) DO NOTHING")) {
            statement.setQueryTimeout(2);
            statement.setString(1, key); statement.setString(2, requestId); statement.setString(3, commandId); statement.setString(4, actor);
            return statement.executeUpdate() == 1 ? new Ticket(key, requestId) : null;
        }
    }

    void release(Ticket ticket) throws SQLException {
        try (Connection connection = connection(); var statement = connection.prepareStatement(
                "DELETE FROM sysarch_probe_m0.command_gate WHERE model_key=? AND request_id=? AND state='ACTIVE'")) {
            statement.setQueryTimeout(2); statement.setString(1, ticket.modelKey()); statement.setString(2, ticket.requestId()); statement.executeUpdate();
        }
    }

    void unknown(Ticket ticket) throws SQLException {
        try (Connection connection = connection(); var statement = connection.prepareStatement(
                "UPDATE sysarch_probe_m0.command_gate SET state='UNKNOWN' WHERE model_key=? AND request_id=?")) {
            statement.setQueryTimeout(2); statement.setString(1, ticket.modelKey()); statement.setString(2, ticket.requestId()); statement.executeUpdate();
        }
    }

    boolean recoverCommitted(String key, String commandId, String actor) throws SQLException {
        try (Connection connection = connection(); var statement = connection.prepareStatement(
                "DELETE FROM sysarch_probe_m0.command_gate WHERE model_key=? AND command_id=? AND actor=? AND state='UNKNOWN'")) {
            statement.setQueryTimeout(2); statement.setString(1, key); statement.setString(2, commandId); statement.setString(3, actor);
            return statement.executeUpdate() == 1;
        }
    }

    ModelClaim claimModel(String modelUuid, String project, String actor, String requestId) throws SQLException {
        try (Connection connection = connection(); var statement = connection.prepareStatement(
                "INSERT INTO sysarch_probe_m0.model_registry(model_uuid,project_id,actor,request_id,state) VALUES (?,?,?,?,'RESERVED') "
                + "ON CONFLICT (model_uuid) DO NOTHING")) {
            statement.setQueryTimeout(2); statement.setString(1, modelUuid); statement.setString(2, project);
            statement.setString(3, actor); statement.setString(4, requestId);
            return statement.executeUpdate() == 1 ? new ModelClaim(modelUuid, requestId) : null;
        }
    }

    void finishModel(ModelClaim claim, String root, boolean rollback) throws SQLException {
        String sql = rollback
                ? "DELETE FROM sysarch_probe_m0.model_registry WHERE model_uuid=? AND request_id=? AND state='RESERVED'"
                : "UPDATE sysarch_probe_m0.model_registry SET root_id=?,state='PUBLISHED' WHERE model_uuid=? AND request_id=? AND state='RESERVED'";
        try (Connection connection = connection(); var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(2); int position = 1;
            if (!rollback) statement.setString(position++, root);
            statement.setString(position++, claim.modelUuid()); statement.setString(position, claim.requestId());
            if (statement.executeUpdate() != 1) throw new SQLException("Model claim is no longer owned");
        }
    }

    void unknownModel(ModelClaim claim) throws SQLException {
        try (Connection connection = connection(); var statement = connection.prepareStatement(
                "UPDATE sysarch_probe_m0.model_registry SET state='UNKNOWN' WHERE model_uuid=? AND request_id=?")) {
            statement.setQueryTimeout(2); statement.setString(1, claim.modelUuid()); statement.setString(2, claim.requestId()); statement.executeUpdate();
        }
    }

    void retireTestModel(String modelUuid, String project, String root, String actor) throws SQLException {
        try (Connection connection = connection(); var statement = connection.prepareStatement(
                "DELETE FROM sysarch_probe_m0.model_registry WHERE model_uuid=? AND project_id=? AND root_id=? AND actor=? AND state='PUBLISHED'")) {
            statement.setQueryTimeout(2); statement.setString(1, modelUuid); statement.setString(2, project);
            statement.setString(3, root); statement.setString(4, actor); statement.executeUpdate();
        }
    }
}

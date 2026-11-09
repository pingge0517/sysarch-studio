package com.example.sysarch.migration;

import com.example.sysarch.migration.InterfaceEntityConversion.Row;
import com.example.sysarch.migration.InterfaceEntityConversion.Status;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rollback of the interface migration (P1 T1 step 5, spec P1 §2.4/§7): restores every converted port's and connection's string from
 * {@code interface_migration_backup} and removes the entities the migration created, for going back to metamodel v1.0
 * (tag {@code v0.1.0}).
 *
 * <p><b>Offline tool: run it with the backend stopped.</b> It is the one place that writes Sirius' {@code document}
 * table directly, because the v1.1 application cannot load a document whose ports hold strings again. Without
 * {@code --apply} it only prints what it would change. Each reverted document gets a migration version below
 * {@link InterfaceEntityMigrationParticipant#VERSION}, so a later upgrade to P1 converts it again instead of reading a
 * name as an id. Uses {@code POSTGRES_URL}, {@code POSTGRES_USER} and {@code POSTGRES_PASSWORD}.
 *
 * <p>{@code ./gradlew :backend:rollbackInterfaceMigration [-Papply]}, then deploy {@code v0.1.0}.
 */
public final class InterfaceMigrationRollback {

    /** Below this migration's version, above every Sirius 2025.x participant. */
    static final String ROLLED_BACK_VERSION = "2025.99.99-interface-rollback";

    private InterfaceMigrationRollback() {
    }

    public static void main(String[] args) throws SQLException {
        boolean apply = List.of(args).contains("--apply");
        String url = System.getenv("POSTGRES_URL");
        if (url == null || url.isBlank()) {
            System.err.println("set POSTGRES_URL, POSTGRES_USER and POSTGRES_PASSWORD");
            System.exit(2);
        }
        try (Connection connection = DriverManager.getConnection(url, System.getenv("POSTGRES_USER"), System.getenv("POSTGRES_PASSWORD"))) {
            connection.setAutoCommit(false);
            int documents = run(connection, apply, System.out);
            if (apply) {
                connection.commit();
            } else {
                connection.rollback();
            }
            System.out.println((apply ? "Rolled back " : "Dry run: would roll back ") + documents + " document(s)"
                    + (apply ? "" : "; run with --apply (-Papply) to write"));
        }
    }

    /** Reverts every document in the backup; writes only if {@code apply}. Returns the number of documents. */
    static int run(Connection connection, boolean apply, PrintStream out) throws SQLException {
        Map<String, List<Row>> rowsByDocument = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT document_key, port_id, port_name, old_value, interface_def_id, status, element_class FROM interface_migration_backup ORDER BY document_key, port_id");
                ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                rowsByDocument.computeIfAbsent(rs.getString(1), key -> new ArrayList<>())
                        .add(new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), Status.valueOf(rs.getString(6)), rs.getString(7)));
            }
        }
        for (var entry : rowsByDocument.entrySet()) {
            String documentId = documentId(entry.getKey());
            String content;
            try (PreparedStatement read = connection.prepareStatement("SELECT content FROM document WHERE id = CAST(? AS uuid)")) {
                read.setString(1, documentId);
                try (ResultSet rs = read.executeQuery()) {
                    if (!rs.next()) {
                        out.println("skip " + entry.getKey() + ": the document no longer exists");
                        continue;
                    }
                    content = rs.getString(1);
                }
            }
            String reverted = revert(content, entry.getValue());
            long elements = entry.getValue().stream().filter(row -> row.status() != Status.EMPTY).count();
            out.println((apply ? "revert " : "would revert ") + entry.getKey() + ": " + elements + " port(s)/connection(s) back to their string");
            if (apply) {
                try (PreparedStatement write = connection.prepareStatement("UPDATE document SET content = ?, last_modified_on = now() WHERE id = CAST(? AS uuid)");
                        PreparedStatement forget = connection.prepareStatement("DELETE FROM interface_migration_backup WHERE document_key = ?")) {
                    write.setString(1, reverted);
                    write.setString(2, documentId);
                    write.executeUpdate();
                    forget.setString(1, entry.getKey());
                    forget.executeUpdate();
                }
            }
        }
        return rowsByDocument.size();
    }

    /** The reverted document JSON, with a migration version that makes a later upgrade convert it again. */
    static String revert(String content, List<Row> rows) {
        JsonObject document = JsonParser.parseString(content).getAsJsonObject();
        InterfaceEntityConversion.revert(document, rows);
        JsonObject migration = document.has("migration") && document.get("migration").isJsonObject() ? document.getAsJsonObject("migration")
                : new JsonObject();
        migration.addProperty("lastMigrationPerformed", "InterfaceEntityRollback");
        migration.addProperty("migrationVersion", ROLLED_BACK_VERSION);
        document.add("migration", migration);
        return document.toString();
    }

    /** The document id is the last segment of the resource URI ({@code sirius:///<id>}). */
    static String documentId(String documentKey) {
        return documentKey.substring(documentKey.lastIndexOf('/') + 1);
    }
}

package com.example.sysarch.migration;

import com.example.sysarch.migration.InterfaceEntityConversion.Row;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * The report of the interface migration (P1 T1 step 3/4): {@link #dryRun} converts copies of the stored documents that
 * still have to be migrated and writes nothing; {@link #applied} lists what loads have converted so far. Both run the
 * same {@link InterfaceEntityConversion} with the same document keys, so for a document they are equal row for row.
 */
@Service
public class InterfaceMigrationReport {

    /** A stored Sirius document, read-only. */
    public record StoredDocument(String project, String documentId, String name, String content) {
    }

    /** Where stored documents come from: Sirius' {@code document} table, read-only (tests supply their own). */
    public interface DocumentSource {

        List<StoredDocument> documents();
    }

    public static final String CSV_HEADER = "document_key,element_id,element_name,old_value,interface_def_id,status,element_class";

    private final DocumentSource documents;

    private final InterfaceMigrationBackup backup;

    public InterfaceMigrationReport(DocumentSource documents, InterfaceMigrationBackup backup) {
        this.documents = Objects.requireNonNull(documents);
        this.backup = Objects.requireNonNull(backup);
    }

    /** What the migration would do to every document that has not been converted yet; changes nothing. */
    public List<Row> dryRun() {
        List<Row> rows = new ArrayList<>();
        for (StoredDocument document : this.documents.documents()) {
            JsonObject json = JsonParser.parseString(document.content()).getAsJsonObject();
            if (!alreadyMigrated(json)) {
                rows.addAll(InterfaceEntityConversion.convert(documentKey(document.documentId()), json));
            }
        }
        return rows;
    }

    /** What loads have converted so far (the backup). */
    public List<Row> applied() {
        return this.backup.rows(null);
    }

    /** The key Sirius gives a document's resource: its URI, made from the document id (Sirius' {@code ResourceLoader}). */
    public static String documentKey(String documentId) {
        return new JSONResourceFactory().createResourceURI(documentId).toString();
    }

    /** True once a document has been saved by a version that ran this migration. */
    static boolean alreadyMigrated(JsonObject document) {
        return InterfaceEntityConversion.migrationVersion(document).compareTo(InterfaceEntityMigrationParticipant.VERSION) >= 0;
    }

    public static String toCsv(List<Row> rows) {
        StringBuilder csv = new StringBuilder(CSV_HEADER).append('\n');
        for (Row row : rows) {
            csv.append(String.join(",", cell(row.documentKey()), cell(row.elementId()), cell(row.elementName()), cell(row.oldValue()),
                    cell(row.interfaceDefId()), row.status().name(), row.elementClass())).append('\n');
        }
        return csv.toString();
    }

    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        return value.contains(",") || value.contains("\"") || value.contains("\n") ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }

    /** Production source: Sirius' tables, read with plain SQL; never written. */
    @Service
    public static class SiriusDocuments implements DocumentSource {

        private final JdbcClient jdbc;

        public SiriusDocuments(JdbcClient jdbc) {
            this.jdbc = Objects.requireNonNull(jdbc);
        }

        @Override
        public List<StoredDocument> documents() {
            return this.jdbc.sql("SELECT p.name, d.id, d.name, d.content FROM document d JOIN project_semantic_data psd ON psd.semantic_data_id = d.semantic_data_id "
                    + "JOIN project p ON p.id = psd.project_id ORDER BY p.name, d.name")
                    .query((rs, i) -> new StoredDocument(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4))).list();
        }
    }
}

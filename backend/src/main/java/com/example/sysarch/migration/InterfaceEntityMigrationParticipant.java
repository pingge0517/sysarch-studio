package com.example.sysarch.migration;

import com.example.sysarch.migration.InterfaceEntityConversion.Row;
import com.example.sysarch.migration.InterfaceEntityConversion.Status;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Objects;

import org.eclipse.sirius.components.emf.migration.api.IMigrationParticipant;
import org.eclipse.sirius.emfjson.resource.JsonResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs {@link InterfaceEntityConversion} when Sirius Web loads a document saved before metamodel v1.1 (P1 T1).
 *
 * <p>Sirius records in each saved document the version of the newest migration it went through and runs a participant
 * only on documents older than its {@link #VERSION} (string order). Documents without a record count as version "0",
 * so every P0 document is converted on its next load; documents created from now on are stamped with this version and
 * left alone. A converted document is written back by Sirius on its next save; until then each load converts it
 * again, with the same derived ids, so nothing is lost or duplicated.
 */
@Component
public class InterfaceEntityMigrationParticipant implements IMigrationParticipant {

    /** Sirius' format ({@code <release>-<yyyyMMddHHmm>}), after every Sirius 2025.x participant. */
    public static final String VERSION = "2026.11.0-202611080000";

    private final Logger logger = LoggerFactory.getLogger(InterfaceEntityMigrationParticipant.class);

    private final InterfaceMigrationBackup backup;

    public InterfaceEntityMigrationParticipant(InterfaceMigrationBackup backup) {
        this.backup = Objects.requireNonNull(backup);
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public void preDeserialization(JsonResource resource, JsonObject jsonObject) {
        String documentKey = resource.getURI() == null ? "?" : resource.getURI().toString();
        List<Row> rows = InterfaceEntityConversion.convert(documentKey, jsonObject);
        if (rows.isEmpty()) {
            return;
        }
        this.backup.record(rows);
        long converted = rows.stream().filter(row -> row.status() == Status.CONVERTED).count();
        this.logger.info("Interface migration of document {}: {} port(s) now refer to an InterfaceDef, {} without a value", documentKey, converted,
                rows.size() - converted);
    }
}

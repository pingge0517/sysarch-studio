package com.example.sysarch.migration;

import com.example.sysarch.migration.InterfaceEntityConversion.Row;
import com.example.sysarch.migration.InterfaceEntityConversion.Status;

import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code interface_migration_backup}: every port the interface migration converted, with its old string. The report of
 * the applied migration and the input of the rollback (spec P1 §2.4 "迁移前导出全部链接关系的 CSV").
 */
@Repository
public class InterfaceMigrationBackup {

    private final JdbcClient jdbc;

    /**
     * Its own write transaction: Sirius loads a project (and so runs the migration) inside a read-only transaction,
     * where the insert failed and aborted the load (found in the live check). If the backup cannot be written the load
     * fails, so a model is never converted without its way back.
     */
    private final TransactionTemplate ownTransaction;

    public InterfaceMigrationBackup(JdbcClient jdbc, PlatformTransactionManager transactions) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.ownTransaction = new TransactionTemplate(Objects.requireNonNull(transactions));
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.ownTransaction.setReadOnly(false);
    }

    /**
     * Keeps the first row per (document, port): a document that is loaded again before it was saved is converted again,
     * with the same result (ids are derived), and must not overwrite the record.
     */
    public void record(List<Row> rows) {
        this.ownTransaction.executeWithoutResult(status -> this.insert(rows));
    }

    /** Skips rows already there, without an error: on PostgreSQL a failed insert would abort the whole transaction. */
    private void insert(List<Row> rows) {
        for (Row row : rows) {
            this.jdbc.sql("INSERT INTO interface_migration_backup (document_key, port_id, port_name, old_value, interface_def_id, status) "
                    + "SELECT :document, :port, :name, :old, :entity, :status WHERE NOT EXISTS "
                    + "(SELECT 1 FROM interface_migration_backup WHERE document_key = :document AND port_id = :port)")
                    .param("document", row.documentKey()).param("port", row.portId()).param("name", row.portName()).param("old", row.oldValue())
                    .param("entity", row.interfaceDefId()).param("status", row.status().name())
                    .update();
        }
    }

    /** All rows, by document then port, or those of one document. */
    public List<Row> rows(String documentKey) {
        String sql = "SELECT document_key, port_id, port_name, old_value, interface_def_id, status FROM interface_migration_backup"
                + (documentKey == null ? "" : " WHERE document_key = :document") + " ORDER BY document_key, port_id";
        var statement = this.jdbc.sql(sql);
        if (documentKey != null) {
            statement = statement.param("document", documentKey);
        }
        return statement.query((rs, i) -> new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                Status.valueOf(rs.getString(6)))).list();
    }
}

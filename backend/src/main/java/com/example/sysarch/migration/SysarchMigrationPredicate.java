package com.example.sysarch.migration;

import com.example.sysarch.model.SysarchPackage;

import java.util.Objects;
import java.util.UUID;

import org.eclipse.sirius.web.application.editingcontext.services.api.IEditingContextMigrationParticipantPredicate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Lets Sirius run migration participants (such as {@link InterfaceEntityMigrationParticipant}) when it loads a project
 * that holds sysarch models.
 *
 * <p>Sirius Web's {@code EditingContextLoader} applies migration participants to a project's documents only if one of
 * these predicates accepts the editing context, and registers none for our domain. Without this bean the interface
 * migration never ran on load, and a P0 model would have lost its port interfaces on the next save (found in the live
 * check). The editing context id is the project's semantic data id; the check reads Sirius' {@code document} table.
 */
@Component
public class SysarchMigrationPredicate implements IEditingContextMigrationParticipantPredicate {

    private final JdbcClient jdbc;

    public SysarchMigrationPredicate(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public boolean test(String editingContextId) {
        UUID semanticDataId;
        try {
            semanticDataId = UUID.fromString(editingContextId);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return this.jdbc.sql("SELECT COUNT(*) FROM document WHERE semantic_data_id = :id AND content LIKE :ns")
                .param("id", semanticDataId).param("ns", "%\"" + SysarchPackage.eNS_URI + "\"%")
                .query(Long.class).single() > 0;
    }
}

package com.example.sysarch.storage;

import com.example.sysarch.access.Access;
import com.example.sysarch.access.Permission;
import com.example.sysarch.access.ProjectReadAccess;
import com.example.sysarch.identity.Principal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Durable model identity and storage selection. M1 exposes LOCAL registration only, never a mode switch. */
@Service
public final class ModelStorageRegistry {
    public record Registration(String siriusProject, UUID modelUuid, ModelStorageMode mode) { }

    public static final class RegistrationConflict extends IllegalStateException {
        public RegistrationConflict() { super("Model identity is already registered; explicit migration is required"); }
    }

    private final JdbcClient jdbc;
    private final ProjectReadAccess reads;
    private final Access access;

    public ModelStorageRegistry(JdbcClient jdbc, ProjectReadAccess reads, Access access) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.reads = Objects.requireNonNull(reads);
        this.access = Objects.requireNonNull(access);
    }

    public Optional<Registration> find(String siriusProject, Principal principal) {
        requireIdentity(principal);
        this.reads.require(principal, siriusProject);
        return stored(siriusProject);
    }

    public ModelStorageMode mode(String siriusProject, Principal principal) {
        return find(siriusProject, principal).map(Registration::mode).orElse(ModelStorageMode.LOCAL);
    }

    public Registration registerLocal(String siriusProject, UUID modelUuid, Principal principal) {
        Objects.requireNonNull(modelUuid);
        requireIdentity(principal);
        this.reads.require(principal, siriusProject);
        this.access.require(principal, siriusProject, Permission.MODEL_EDIT, null);
        var existing = stored(siriusProject);
        if (existing.isPresent()) return sameLocal(existing.get(), modelUuid);
        try {
            this.jdbc.sql("INSERT INTO model_storage_registration (sirius_project, model_uuid, storage_mode, created_by) "
                    + "VALUES (:project, :uuid, 'LOCAL', :actor)")
                    .param("project", siriusProject).param("uuid", modelUuid.toString()).param("actor", principal.user()).update();
        } catch (DuplicateKeyException e) {
            // Database uniqueness, rather than a JVM lock, arbitrates concurrent registrations.
            return stored(siriusProject).map(r -> sameLocal(r, modelUuid)).orElseThrow(RegistrationConflict::new);
        }
        return new Registration(siriusProject, modelUuid, ModelStorageMode.LOCAL);
    }

    private Optional<Registration> stored(String project) {
        return this.jdbc.sql("SELECT sirius_project, model_uuid, storage_mode FROM model_storage_registration WHERE sirius_project=:project")
                .param("project", project).query((rs, row) -> new Registration(rs.getString(1), UUID.fromString(rs.getString(2)),
                        ModelStorageMode.valueOf(rs.getString(3)))).optional();
    }

    private static Registration sameLocal(Registration registration, UUID expected) {
        if (!registration.modelUuid().equals(expected) || registration.mode() != ModelStorageMode.LOCAL) throw new RegistrationConflict();
        return registration;
    }

    private static void requireIdentity(Principal principal) {
        if (principal == null || principal.user() == null || principal.user().isBlank()) throw new ProjectReadAccess.Denied();
    }
}

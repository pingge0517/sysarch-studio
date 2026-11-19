package com.example.sysarch.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.sysarch.access.Access;
import com.example.sysarch.access.Permission;
import com.example.sysarch.access.ProjectReadAccess;
import com.example.sysarch.identity.Principal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class ModelStorageRegistryTest {
    private final Principal principal = Principal.dev("fixture", List.of());
    private final ProjectReadAccess reads = mock(ProjectReadAccess.class);
    private final Access access = mock(Access.class);
    private JdbcClient jdbc;
    private ModelStorageRegistry registry;

    @BeforeEach
    void setup() {
        var data = new JdbcDataSource(); data.setURL("jdbc:h2:mem:storage-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("db/sysarch/019-model-storage.sql")).execute(data);
        this.jdbc = JdbcClient.create(data);
        this.registry = new ModelStorageRegistry(this.jdbc, this.reads, this.access);
    }

    @Test
    void absentRegistrationIsLocalAndRegistrationSurvivesNewRegistryInstance() {
        assertEquals(ModelStorageMode.LOCAL, this.registry.mode("project", this.principal));
        assertTrue(this.registry.find("project", this.principal).isEmpty());
        UUID id = UUID.randomUUID();
        var registered = this.registry.registerLocal("project", id, this.principal);
        var reloaded = new ModelStorageRegistry(this.jdbc, this.reads, this.access);
        assertEquals(registered, reloaded.find("project", this.principal).orElseThrow());
        assertEquals(registered, reloaded.registerLocal("project", id, this.principal));
    }

    @Test
    void identityCannotBeChangedOrSharedAcrossProjects() {
        UUID id = UUID.randomUUID(); this.registry.registerLocal("a", id, this.principal);
        assertThrows(ModelStorageRegistry.RegistrationConflict.class,
                () -> this.registry.registerLocal("a", UUID.randomUUID(), this.principal));
        assertThrows(ModelStorageRegistry.RegistrationConflict.class,
                () -> this.registry.registerLocal("b", id, this.principal));
        assertEquals(1, this.jdbc.sql("SELECT COUNT(*) FROM model_storage_registration").query(Integer.class).single());
    }

    @Test
    void permissionsAreCheckedBeforeRegistrationAndAnonymousReadsAreDenied() {
        assertThrows(ProjectReadAccess.Denied.class, () -> this.registry.mode("a", null));
        doThrow(new SecurityException("fixture denial")).when(this.access)
                .require(this.principal, "a", Permission.MODEL_EDIT, null);
        assertThrows(SecurityException.class, () -> this.registry.registerLocal("a", UUID.randomUUID(), this.principal));
        assertEquals(0, this.jdbc.sql("SELECT COUNT(*) FROM model_storage_registration").query(Integer.class).single());
    }

    @Test
    void deniedProjectCannotReadModeOrIdentity() {
        doThrow(new ProjectReadAccess.Denied()).when(this.reads).require(this.principal, "secret");
        assertThrows(ProjectReadAccess.Denied.class, () -> this.registry.find("secret", this.principal));
        assertThrows(ProjectReadAccess.Denied.class, () -> this.registry.registerLocal("secret", UUID.randomUUID(), this.principal));
        verifyNoInteractions(this.access);
    }

    @Test
    void databaseUniquenessArbitratesConcurrentRegistrations() throws Exception {
        UUID id = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var barrier = new java.util.concurrent.CyclicBarrier(4);
            Callable<ModelStorageRegistry.Registration> register = () -> {
                barrier.await();
                return new ModelStorageRegistry(this.jdbc, this.reads, this.access).registerLocal("same", id, this.principal);
            };
            for (var future : executor.invokeAll(List.of(register, register, register, register))) assertEquals(id, future.get().modelUuid());
        }
        assertEquals(1, this.jdbc.sql("SELECT COUNT(*) FROM model_storage_registration").query(Integer.class).single());
    }

    @Test
    void polarionRegistrationNeverFallsBackToLocalOrChangesModeDuringRegistration() {
        this.jdbc.sql("INSERT INTO model_storage_registration (sirius_project,model_uuid,storage_mode,created_by) VALUES ('future',:id,'POLARION','fixture')")
                .param("id", UUID.randomUUID().toString()).update();
        ModelRepository local = mock(ModelRepository.class); when(local.mode()).thenReturn(ModelStorageMode.LOCAL);
        var router = new ModelRepositoryRouter(this.registry, local);
        assertSame(local, router.repository("old", this.principal));
        assertThrows(IllegalStateException.class, () -> router.repository("future", this.principal));
        assertThrows(ModelStorageRegistry.RegistrationConflict.class, () -> this.registry.registerLocal("future", UUID.randomUUID(), this.principal));
        verify(local, never()).capture(anyString(), any(), any());
    }
}

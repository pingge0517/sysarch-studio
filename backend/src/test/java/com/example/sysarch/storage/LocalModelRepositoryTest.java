package com.example.sysarch.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.sysarch.access.ProjectReadAccess;
import com.example.sysarch.identity.Principal;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.storage.local.LocalModelRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.Test;

class LocalModelRepositoryTest {
    private final ProjectModels projects = mock(ProjectModels.class);
    private final ProjectReadAccess access = mock(ProjectReadAccess.class);
    private final LocalModelRepository repository = new LocalModelRepository(this.projects, this.access, new ModelCodec());
    private final Principal principal = new Principal("fixture-user", "Fixture", Set.of(), Map.of(), "test");

    @Test
    void permissionIsCheckedBeforeAccessingAnyModelObjects() {
        String project = UUID.randomUUID().toString();
        when(this.projects.siriusProjectId("alias")).thenReturn(project);
        doThrow(new ProjectReadAccess.Denied()).when(this.access).require(this.principal, project);
        assertThrows(ProjectReadAccess.Denied.class, () -> this.repository.capture("alias", this.principal, new ModelIdentityRegistry()));
        verify(this.projects, never()).read(anyString(), any());
    }

    @Test
    void anonymousCannotCaptureEvenWhenLegacyAnonymousReadIsConfigured() {
        assertThrows(ProjectReadAccess.Denied.class, () -> this.repository.capture("alias", null, new ModelIdentityRegistry()));
        verifyNoInteractions(this.projects, this.access);
    }

    @Test
    @SuppressWarnings("unchecked")
    void capturesInsideTheExistingEditingContextWithoutWritingOrRenamingObjects() {
        String project = UUID.randomUUID().toString();
        Resource resource = new ResourceImpl(URI.createURI("memory:/fixed-document"));
        var block = SysarchFactory.eINSTANCE.createPartDefinition(); block.setId("PD-001");
        resource.getContents().add(block);
        when(this.projects.siriusProjectId("alias")).thenReturn(project);
        when(this.projects.read(eq(project), any())).thenAnswer(invocation -> {
            Function<List<Resource>, StoredModel> read = invocation.getArgument(1);
            return read.apply(List.of(resource));
        });
        var identities = new ModelIdentityRegistry();
        StoredModel first = this.repository.capture("alias", this.principal, identities);
        StoredModel second = this.repository.capture("alias", this.principal, identities);
        assertEquals(first, second);
        assertEquals("PD-001", block.getId());
        assertEquals(ModelStorageMode.LOCAL, this.repository.mode());
        verify(this.access, times(2)).require(this.principal, project);
        verify(this.projects, never()).write(anyString(), any(Function.class));
    }

    @Test
    void enablingPreparatoryFlagCannotEnableRemoteWritesOrChangeExistingMode() {
        var properties = new PolarionStoreProperties();
        assertFalse(properties.isEnabled());
        properties.setEnabled(true);
        assertFalse(properties.writesAllowed());
        assertEquals(ModelStorageMode.LOCAL, properties.defaultMode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void registeredIdentityIsUsedAndPolarionModeCannotReadLocalObjects() {
        String project = UUID.randomUUID().toString();
        UUID modelUuid = UUID.randomUUID();
        var registry = mock(ModelStorageRegistry.class);
        var repository = new LocalModelRepository(this.projects, this.access, new ModelCodec(), registry);
        when(this.projects.siriusProjectId("alias")).thenReturn(project);
        when(registry.find(project, this.principal)).thenReturn(java.util.Optional.of(
                new ModelStorageRegistry.Registration(project, modelUuid, ModelStorageMode.LOCAL)));
        when(this.projects.read(eq(project), any())).thenAnswer(invocation -> {
            Function<List<Resource>, StoredModel> read = invocation.getArgument(1);
            return read.apply(List.of());
        });
        assertEquals(modelUuid, repository.capture("alias", this.principal, new ModelIdentityRegistry()).modelUuid());
        clearInvocations(this.projects);
        when(registry.find(project, this.principal)).thenReturn(java.util.Optional.of(
                new ModelStorageRegistry.Registration(project, modelUuid, ModelStorageMode.POLARION)));
        assertThrows(IllegalStateException.class, () -> repository.capture("alias", this.principal, new ModelIdentityRegistry()));
        verify(this.projects, never()).read(anyString(), any());
    }
}

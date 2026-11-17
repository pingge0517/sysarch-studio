package com.example.sysarch.access;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import com.example.sysarch.identity.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProjectReadAccessTest {
    private ProjectReadFixture f;
    @BeforeEach void setup() { f = new ProjectReadFixture(); }

    @Test void observerFallbackDoesNotGrantMembershipInEveryProject() {
        assertTrue(f.reads.canRead(f.alice,"p-a"));
        assertFalse(f.reads.canRead(f.alice,"p-b"));
        assertTrue(f.reads.canRead(f.bob,"p-b"));
        assertFalse(f.reads.canRead(f.bob,"p-a"));
        assertFalse(f.reads.canRead(Principal.dev("stranger",List.of()),"p-a"));
        assertTrue(f.reads.canRead(f.admin,"p-a"));
    }

    @Test void projectAliasesAndEditingContextsCannotBypassThePolicy() {
        assertEquals("p-a",f.reads.resolve("alpha"));
        assertEquals("p-a",f.reads.resolve("Alpha model"));
        assertEquals("p-b",f.reads.editingContext("ec-b"));
        assertThrows(ProjectReadAccess.Denied.class,()->f.reads.reference(f.alice,"beta"));
        assertThrows(ProjectReadAccess.Denied.class,()->f.reads.reference(f.alice,"Beta secret"));
        assertThrows(ProjectReadAccess.Denied.class,()->f.reads.resolve("missing"));
    }

    @Test void branchWorkspacesInheritTheTrunksVisibility() {
        assertFalse(f.reads.canRead(f.alice,"workspace-b"));
        assertTrue(f.reads.canRead(f.bob,"workspace-b"));
        assertThrows(ProjectReadAccess.Denied.class,()->f.reads.resource(f.alice,"branch","branch-b"));
    }

    @Test void reportAndBaselineIdentifiersDoNotActAsBearerCapabilities() {
        f.reads.resource(f.alice,"report","report-a");
        f.reads.resource(f.alice,"baseline","base-a");
        assertThrows(ProjectReadAccess.Denied.class,()->f.reads.resource(f.alice,"report","report-b"));
        assertThrows(ProjectReadAccess.Denied.class,()->f.reads.resource(f.alice,"baseline","base-b"));
    }

    @Test void unboundProjectsAreSharedOnlyByAnExplicitUnboundMapping() {
        assertTrue(f.reads.canRead(f.alice,"unbound"));
        when(f.roles.list()).thenReturn(List.of(new RoleMappings.Row(1,"*","*",ToolRole.OBSERVER,"system",null)));
        assertFalse(f.reads.canRead(f.alice,"unbound"));
    }

    @Test void namedGlobalRoleGrantsAreExplicitAndScoped() {
        when(f.roles.list()).thenReturn(List.of(new RoleMappings.Row(1,"alpha","global:auditor",ToolRole.OBSERVER,"root",null)));
        var auditor=Principal.dev("auditor",List.of("global:auditor"));
        assertTrue(f.reads.canRead(auditor,"p-a"));
        assertFalse(f.reads.canRead(auditor,"p-b"));
    }

    @Test void anonymousProductionReadsAreDeniedUnlessTheDeploymentExplicitlyOptsOut() {
        assertFalse(f.reads.canRead(null,"p-a"));
        f.auth.setAnonymousRead(true);
        assertTrue(f.reads.canRead(null,"p-a"));
    }

    @Test void failedImportDiagnosticsArePrivateToTheirAuthorAndAdministrators() {
        assertTrue(f.reads.canReadImport(f.alice,"import-a"));
        assertFalse(f.reads.canReadImport(f.alice,"import-b"));
        assertTrue(f.reads.canReadImport(f.admin,"import-b"));
    }
}

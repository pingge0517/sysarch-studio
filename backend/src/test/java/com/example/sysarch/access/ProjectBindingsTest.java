package com.example.sysarch.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.branch.BranchWorkspaces;
import com.example.sysarch.identity.Principal;
import com.example.sysarch.sync.SyncProperties;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import liquibase.integration.spring.SpringLiquibase;

/** P4 T1 (P4-AC-1): Polarion project → model bindings as data, without a restart, administrators only, audited. */
class ProjectBindingsTest {

    private JdbcClient jdbc;

    private SyncProperties sync;

    private ProjectBindings bindings;

    private ProjectBindingController controller;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/sysarch/changelog.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        this.jdbc = JdbcClient.create(dataSource);
        this.jdbc.sql("CREATE TABLE IF NOT EXISTS project (id VARCHAR(64) PRIMARY KEY, name VARCHAR(200))").update();
        this.jdbc.sql("INSERT INTO project (id, name) VALUES ('p-env', 'model from env'), ('p-dc', '域控演示'), ('p-other', 'other model'), "
                + "('p-twin-1', 'twin'), ('p-twin-2', 'twin')").update();
        this.sync = new SyncProperties();
        this.sync.getProjects().put("envproj", "model from env");
        this.bindings = new ProjectBindings(this.sync, this.jdbc, new BranchWorkspaces(this.jdbc));
        this.bindings.load();
        Access access = new Access(new RoleMappings(this.jdbc), this.bindings, new AuditLog(this.jdbc));
        this.controller = new ProjectBindingController(this.bindings, access, new AuditLog(this.jdbc));
    }

    private static MockHttpServletRequest as(String user, String... roles) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Principal principal = Principal.dev(user, List.of(roles));
        request.setAttribute(Principal.class.getName(), (Supplier<Optional<Principal>>) () -> Optional.of(principal));
        return request;
    }

    private int audits() {
        return this.jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE action = 'PROJECT_BINDING_CHANGE'").query(Integer.class).single();
    }

    @Test
    void anAdministratorBindsWithoutARestart() {
        assertEquals(Optional.empty(), this.bindings.polarionProjectOf("p-dc"));

        ProjectBindings.Binding binding = this.controller.bind("sysarch", new ProjectBindingController.BindRequest("域控演示"), as("root", "global:admin"));

        assertEquals("p-dc", binding.siriusProject(), "stored as the model's id, given by name");
        assertEquals("p-dc", this.sync.getProjects().get("sysarch"), "every reader of sysarch.polarion.projects sees it at once");
        assertEquals(Optional.of("sysarch"), this.bindings.polarionProjectOf("p-dc"));
        assertEquals(List.of("envproj", "sysarch"), this.bindings.list().stream().map(ProjectBindings.Binding::polarionProject).toList());
        assertEquals(1, this.audits());

        this.controller.bind("sysarch", new ProjectBindingController.BindRequest("p-other"), as("root", "global:admin"));
        assertEquals(Optional.empty(), this.bindings.polarionProjectOf("p-dc"), "rebinding moves the Polarion project");
        assertEquals(Optional.of("sysarch"), this.bindings.polarionProjectOf("p-other"));

        SyncProperties fresh = new SyncProperties();
        fresh.getProjects().put("envproj", "model from env");
        ProjectBindings reloaded = new ProjectBindings(fresh, this.jdbc, new BranchWorkspaces(this.jdbc));
        reloaded.load();
        assertEquals("p-other", fresh.getProjects().get("sysarch"), "the table survives a restart");
    }

    @Test
    void onlyAdministratorsChangeBindingsAndRefusalsAreAudited() {
        assertThrows(ForbiddenException.class, () -> this.controller.bind("sysarch", new ProjectBindingController.BindRequest("域控演示"),
                as("arch", "sysarch/project_user")), "403 through the error handler");
        assertFalse(this.sync.getProjects().containsKey("sysarch"));
        assertTrue(this.jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE action = 'ACCESS_DENIED'").query(Integer.class).single() >= 1);
        assertThrows(ForbiddenException.class, () -> this.controller.unbind("envproj", as("arch")));
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> this.controller.list(new MockHttpServletRequest())).getStatusCode().value());
        assertEquals(1, this.controller.list(as("arch")).size(), "signed-in users read them (the refused binding was not made)");
    }

    @Test
    void aModelBelongsToOnePolarionProject() {
        this.controller.bind("sysarch", new ProjectBindingController.BindRequest("p-dc"), as("root", "global:admin"));

        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> this.controller.bind("other", new ProjectBindingController.BindRequest("p-dc"), as("root", "global:admin"))).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> this.controller.bind("other", new ProjectBindingController.BindRequest("p-env"), as("root", "global:admin"))).getStatusCode().value(),
                "also against the environment's bindings");
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> this.controller.bind("other", new ProjectBindingController.BindRequest("nope"), as("root", "global:admin"))).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> this.controller.bind("other", new ProjectBindingController.BindRequest("twin"), as("root", "global:admin"))).getStatusCode().value(),
                "two models of that name: use the id");
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> this.controller.bind("other", new ProjectBindingController.BindRequest(" "), as("root", "global:admin"))).getStatusCode().value());
    }

    @Test
    void unbindingRestoresTheEnvironmentsDefault() {
        this.controller.bind("envproj", new ProjectBindingController.BindRequest("p-other"), as("root", "global:admin"));
        assertEquals("p-other", this.sync.getProjects().get("envproj"), "the table overrides the environment");

        this.controller.unbind("envproj", as("root", "global:admin"));

        assertEquals("model from env", this.sync.getProjects().get("envproj"));
        assertEquals("environment", this.bindings.list().get(0).source());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> this.controller.unbind("envproj", as("root", "global:admin"))).getStatusCode().value(),
                "an environment binding is changed in the configuration");
        assertEquals(2, this.audits());
    }

    @Test
    void deletingAModelDropsItsBindings() {
        this.controller.bind("sysarch", new ProjectBindingController.BindRequest("p-dc"), as("root", "global:admin"));

        assertEquals(1, this.bindings.dropSiriusProject("p-dc"));

        assertFalse(this.sync.getProjects().containsKey("sysarch"));
        assertEquals(0, this.jdbc.sql("SELECT COUNT(*) FROM project_binding").query(Integer.class).single());
        assertEquals(0, this.bindings.dropSiriusProject("p-dc"));
    }
}

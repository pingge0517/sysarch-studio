package com.example.sysarch.access;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.branch.BranchWorkspaces;
import com.example.sysarch.identity.AuthProperties;
import com.example.sysarch.identity.Editors.Editor;
import com.example.sysarch.identity.Identities;
import com.example.sysarch.identity.Principal;
import com.example.sysarch.identity.SessionCookies;
import com.example.sysarch.lock.EditAdmission;
import com.example.sysarch.lock.LockEnforcement;
import com.example.sysarch.lock.LockService;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.session.SessionService;
import com.example.sysarch.sync.SyncProperties;
import com.example.sysarch.sync.polarion.PolarionClient;
import com.example.sysarch.sync.polarion.PolarionToken;
import com.example.sysarch.sync.polarion.TokenBucket;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.servlet.http.Cookie;
import liquibase.integration.spring.SpringLiquibase;

/**
 * P2 T8 step 1 (spec §7.1): roles come from {@code role_mapping} (its seed rows: the user's decision "走通配行"), the
 * identity from Polarion (who owns a token, with which roles), kept in a signed cookie; a reviewer's modelling is
 * refused and audited (T8 acceptance, first half).
 */
class AccessTest {

    private JdbcClient jdbc;

    private JdbcDataSource dataSource;

    private Access access;

    private RoleMappings mappings;

    private WireMockServer polarion;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        this.dataSource = dataSource;
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/sysarch/changelog.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        this.jdbc = JdbcClient.create(dataSource);
        this.jdbc.sql("CREATE TABLE IF NOT EXISTS project (id VARCHAR(64) PRIMARY KEY, name VARCHAR(200))").update();
        this.jdbc.sql("INSERT INTO project (id, name) VALUES ('sirius-bound', 'model of sysarch'), ('sirius-free', 'imported')").update();
        SyncProperties sync = new SyncProperties();
        sync.getProjects().put("sysarch", "model of sysarch");
        this.mappings = new RoleMappings(this.jdbc);
        this.access = new Access(this.mappings, new ProjectBindings(sync, this.jdbc, new BranchWorkspaces(this.jdbc)), new AuditLog(this.jdbc));
        this.polarion = new WireMockServer(wireMockConfig().dynamicPort());
        this.polarion.start();
    }

    @AfterEach
    void tearDown() {
        this.polarion.stop();
    }

    private static Principal user(String id, List<String> roles) {
        return Principal.dev(id, roles);
    }

    @Test
    void theSeedRowsGiveTheFourRoles() {
        assertEquals(ToolRole.ADMIN, this.access.grant(user("root", List.of("global:admin")), "sirius-free").role(), "Polarion administrator");
        assertEquals(ToolRole.ADMIN, this.access.grant(user("pa", List.of("sysarch/project_admin")), "sirius-bound").role());
        assertEquals(ToolRole.ARCHITECT, this.access.grant(user("arch", List.of("sysarch/project_user")), "sirius-bound").role());
        assertEquals(ToolRole.REVIEWER, this.access.grant(user("rev", List.of("sysarch/project_reviewer")), "sirius-bound").role());
        assertEquals(ToolRole.OBSERVER, this.access.grant(user("obs", List.of("sysarch/project_observer")), "sirius-bound").role());
        assertEquals(ToolRole.OBSERVER, this.access.grant(user("other", List.of("elsewhere/project_admin")), "sirius-bound").role(),
                "no mapped role in the bound project: observer");
        assertEquals(ToolRole.ARCHITECT, this.access.grant(user("other", List.of()), "sirius-free").role(), "every signed-in user models unbound projects");
        assertEquals(ToolRole.ARCHITECT, this.access.grant(user("other", List.of()), null).role(), "a project not created yet is unbound");
        assertEquals(ToolRole.OBSERVER, this.access.grant(null, "sirius-free").role(), "anonymous matches no row");
        assertEquals(ToolRole.OBSERVER, this.access.globalGrant(user("pa", List.of("sysarch/project_admin"))).role(), "a project role is not global");
        assertEquals(ToolRole.ADMIN, this.access.globalGrant(user("root", List.of("global:admin"))).role());
    }

    @Test
    void theMappingIsDataNotCode() {
        this.mappings.put("sysarch", "custom_reviewer", ToolRole.REVIEWER, "root");
        assertEquals(ToolRole.REVIEWER, this.access.grant(user("x", List.of("sysarch/custom_reviewer")), "sirius-bound").role());
        long unbound = this.mappings.list().stream().filter(r -> r.polarionProject().equals(RoleMappings.UNBOUND)).findFirst().orElseThrow().id();
        this.mappings.delete(unbound);
        assertEquals(ToolRole.OBSERVER, this.access.grant(user("other", List.of()), "sirius-free").role(), "without the (unbound) row only the * row applies");
        this.mappings.put("*", "project_user", ToolRole.REVIEWER, "root");
        assertEquals(ToolRole.REVIEWER, this.access.grant(user("arch", List.of("sysarch/project_user")), "sirius-bound").role(), "replaced, not added");
    }

    @Test
    void aReviewerIsRefusedModellingAndTheRefusalIsAudited() {
        Principal reviewer = user("rev", List.of("sysarch/project_reviewer"));
        ForbiddenException refused = assertThrows(ForbiddenException.class,
                () -> this.access.require(reviewer, "sirius-bound", Permission.MODEL_EDIT, "PD-001"));
        assertEquals("权限不足：rev 在本项目的角色是评审人，不能建模", refused.getMessage());
        assertDoesNotThrow(() -> this.access.require(reviewer, "sirius-bound", Permission.COMMENT, null), "a reviewer comments");
        List<AuditLog.Entry> audit = new AuditLog(this.jdbc).recent("sirius-bound", AuditLog.ACCESS_DENIED, 10);
        assertEquals(1, audit.size());
        assertEquals("rev", audit.get(0).actor());
        assertEquals("PD-001", audit.get(0).elementId());
        assertTrue(audit.get(0).detail().startsWith("MODEL_EDIT refused, role REVIEWER"), audit.get(0).detail());
    }

    @Test
    void editAdmissionAsksTheRoleFirst() {
        SessionService sessions = new SessionService(this.jdbc, 30, Duration.ofMinutes(30), List.of());
        LockService locks = new LockService(this.jdbc, new TransactionTemplate(new DataSourceTransactionManager(this.dataSource)), new AuditLog(this.jdbc));
        EditAdmission admission = new EditAdmission(new LockEnforcement(locks, this.jdbc), sessions, this.access);
        ChangeDescription rename = rename();
        Principal reviewer = user("rev", List.of("sysarch/project_reviewer"));
        ForbiddenException refused = assertThrows(ForbiddenException.class, () -> admission.check("sirius-bound", Editor.of(reviewer), rename, null));
        assertEquals(EditAdmission.FORBIDDEN, EditAdmission.reasons(refused).get(0).code());
        assertTrue(sessions.list("sirius-bound").isEmpty(), "a refused edit opens no session");
        assertThrows(ForbiddenException.class, () -> admission.check("sirius-bound", Editor.ANONYMOUS, rename, null), "anonymous: observer");
        assertDoesNotThrow(() -> admission.check("sirius-bound", Editor.of(user("arch", List.of("sysarch/project_user"))), rename, null));
        assertDoesNotThrow(() -> admission.check("sirius-bound", new Editor("merger"), rename, null), "a name without a principal was authorized by its caller");
    }

    @Test
    void theSessionCookieIsSignedAndExpires() {
        MutableClock clock = new MutableClock();
        SessionCookies cookies = new SessionCookies("secret-1", clock);
        Principal alice = new Principal("alice", "Alice", Set.of("user"), Map.of("sysarch", Set.of("project_user")), "polarion");
        String value = cookies.issue(alice, clock.instant().plusSeconds(3600));
        assertEquals(alice, cookies.read(value).orElseThrow());
        String payload = value.substring(0, value.indexOf('.'));
        String forged = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(java.util.Base64.getUrlDecoder().decode(payload), java.nio.charset.StandardCharsets.UTF_8).replace("project_user", "project_admin")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(cookies.read(forged + value.substring(value.indexOf('.'))).isEmpty(), "a changed role breaks the signature");
        assertTrue(new SessionCookies("secret-2", clock).read(value).isEmpty(), "another key");
        clock.now = clock.now.plusSeconds(3601);
        assertTrue(cookies.read(value).isEmpty(), "expired");
        assertFalse(value.contains("tok"), "no token in the cookie");
    }

    @Test
    void polarionSaysWhoOwnsTheToken() {
        this.polarion.stubFor(get(urlPathEqualTo("/polarion/rest/v1/users")).withQueryParam("query", equalTo("id:$[user.id]"))
                .withHeader("Authorization", equalTo("Bearer good")).willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[{\"type\":\"users\",\"id\":\"rev\"}]}")));
        this.polarion.stubFor(get(urlPathEqualTo("/polarion/rest/v1/users")).withHeader("Authorization", equalTo("Bearer bad"))
                .willReturn(aResponse().withStatus(401)));
        this.polarion.stubFor(get(urlPathEqualTo("/polarion/rest/v1/users/rev")).willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody("{\"data\":{\"type\":\"users\",\"id\":\"rev\",\"attributes\":{\"id\":\"rev\",\"name\":\"Rita Reviewer\"},\"relationships\":{"
                        + "\"globalRoles\":{\"data\":[{\"type\":\"globalroles\",\"id\":\"user\"}]},"
                        + "\"projectRoles\":{\"data\":[{\"type\":\"projectroles\",\"id\":\"sysarch/project_reviewer\"},"
                        + "{\"type\":\"projectroles\",\"id\":\"other/project_admin\"}]}}}}")));
        PolarionClient client = new PolarionClient(this.polarion.baseUrl() + "/polarion", new TokenBucket(1000), 0, new ObjectMapper(), d -> {
        });
        SyncProperties sync = new SyncProperties();
        sync.getProjects().put("sysarch", "model of sysarch");
        AuthProperties properties = new AuthProperties();
        SessionCookies cookies = new SessionCookies("k", Clock.systemUTC());
        Identities identities = new Identities(properties, client, sync, cookies);

        Principal rev = identities.login(new PolarionToken("good"));
        assertEquals("rev", rev.user());
        assertEquals("Rita Reviewer", rev.name());
        assertEquals(Set.of("user"), rev.globalRoles());
        assertEquals(Map.of("sysarch", Set.of("project_reviewer")), rev.projectRoles(), "only roles in bound projects are kept");
        assertEquals(ToolRole.REVIEWER, this.access.grant(rev, "sirius-bound").role());
        assertThrows(Identities.InvalidTokenException.class, () -> identities.login(new PolarionToken("bad")));

        MockHttpServletRequest api = new MockHttpServletRequest();
        api.addHeader("Authorization", "Bearer good");
        assertEquals("rev", identities.resolve(api).orElseThrow().user());
        assertEquals(2, this.polarion.countRequestsMatching(getRequestedFor(urlPathEqualTo("/polarion/rest/v1/users")).build()).getCount(),
                "the login's answer is reused for the token (cached by its hash)");

        MockHttpServletRequest claimed = new MockHttpServletRequest();
        claimed.addHeader("X-Sysarch-User", "admin");
        claimed.setCookies(new Cookie("sysarch_user", "admin"));
        assertTrue(identities.resolve(claimed).isEmpty(), "polarion mode: a typed name is never believed");
        properties.setMode(AuthProperties.Mode.DEV);
        claimed.addHeader(Identities.ROLES_HEADER, "global:admin");
        assertEquals(Set.of("admin"), identities.resolve(claimed).orElseThrow().globalRoles(), "dev mode only");

        MockHttpServletRequest browser = new MockHttpServletRequest();
        browser.setCookies(new Cookie(SessionCookies.COOKIE, cookies.issue(rev, Instant.now().plusSeconds(60))));
        assertEquals(rev, identities.resolve(browser).orElseThrow(), "the session cookie");
    }

    private static MockHttpServletRequest as(Principal principal) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (principal != null) {
            request.setAttribute(Principal.class.getName(), (java.util.function.Supplier<java.util.Optional<Principal>>) () -> java.util.Optional.of(principal));
        }
        return request;
    }

    /** Binding and unbinding need SYNC in the Polarion project; claiming a pooled Work Item takes it out of the pool. */
    @Test
    void bindingNeedsTheSyncPermissionAndClaimingEmptiesThePool() {
        com.example.sysarch.sync.SyncStore store = new com.example.sysarch.sync.SyncStore(this.jdbc);
        com.example.sysarch.link.LinkRepository links = new com.example.sysarch.link.LinkRepository(this.jdbc);
        store.addToUnboundPool("sysarch", "SYS-42", "requirement", "新需求");
        com.example.sysarch.sync.api.model.LinkRequest claim = new com.example.sysarch.sync.api.model.LinkRequest("RQ-009", "Requirement", "SYS-42", "requirement", "sysarch");

        for (MockHttpServletRequest refused : List.of(as(null), as(user("rev", List.of("sysarch/project_reviewer"))))) {
            com.example.sysarch.link.LinksController controller = new com.example.sysarch.link.LinksController(links, store, refused, this.access);
            assertThrows(ForbiddenException.class, () -> controller.linksPost(claim));
            assertThrows(ForbiddenException.class, () -> controller.unbound("sysarch"));
        }
        assertTrue(links.find("RQ-009").isEmpty(), "nothing bound");

        com.example.sysarch.link.LinksController architect = new com.example.sysarch.link.LinksController(links, store,
                as(user("arch", List.of("sysarch/project_user"))), this.access);
        assertEquals(List.of("SYS-42"), architect.unbound("sysarch").stream().map(com.example.sysarch.sync.SyncStore.PooledWorkItem::workItemId).toList());
        assertEquals(201, architect.linksPost(claim).getStatusCode().value());
        assertEquals("SYS-42", links.find("RQ-009").orElseThrow().workItemId());
        assertEquals(List.of(), architect.unbound("sysarch"), "claimed: out of the pool");

        com.example.sysarch.link.LinksController reviewer = new com.example.sysarch.link.LinksController(links, store,
                as(user("rev", List.of("sysarch/project_reviewer"))), this.access);
        assertThrows(ForbiddenException.class, () -> reviewer.linksElementIdDelete("RQ-009"));
        assertEquals(204, architect.linksElementIdDelete("RQ-009").getStatusCode().value());
        assertTrue(links.find("RQ-009").isEmpty());
        assertEquals(List.of("BIND", "UNBIND"), this.jdbc.sql("SELECT direction FROM sync_audit WHERE element_id = 'RQ-009' ORDER BY id").query(String.class).list());
        assertTrue(this.jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE action = 'ACCESS_DENIED'").query(Integer.class).single() >= 5, "refusals audited");
    }

    @Test
    void anUnrecordedSiriusInputIsAnonymousNotSystem() {
        String renamed = "{\"query\":\"mutation($i:EditLabelInput!){editLabel(input:$i){__typename}}\","
                + "\"variables\":{\"i\":{\"id\":\"in-7\",\"newText\":\"x\"}}}";
        assertEquals(List.of("in-7"), com.example.sysarch.identity.GraphQLEditorFilter.inputIds(renamed.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "a variable not named input is still recorded");
        com.example.sysarch.identity.Editors editors = new com.example.sysarch.identity.Editors();
        UUID id = UUID.randomUUID();
        org.eclipse.sirius.components.core.api.IInput sirius = (org.eclipse.sirius.components.core.api.IInput) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { org.eclipse.sirius.components.core.api.IInput.class }, (proxy, method, args) -> id);
        assertEquals(Editor.ANONYMOUS, com.example.sysarch.config.ConstraintEnforcement.Guard.editorOf(editors, sirius).orElseThrow(),
                "a Sirius input nobody recorded (inline literal, unknown shape) is anonymous: refused, not exempt");
        org.eclipse.sirius.components.core.api.IInput ours = new OurInput(id);
        assertTrue(com.example.sysarch.config.ConstraintEnforcement.Guard.editorOf(editors, ours).isEmpty(), "our own unrecorded input is the system");
        editors.record(id.toString(), Editor.of(user("arch", List.of())));
        assertEquals("arch", com.example.sysarch.config.ConstraintEnforcement.Guard.editorOf(editors, sirius).orElseThrow().user());
    }

    private record OurInput(UUID id) implements org.eclipse.sirius.components.core.api.IInput {
    }

    private static ChangeDescription rename() {
        PartDefinition block = SysarchFactory.eINSTANCE.createPartDefinition();
        block.setId("PD-001");
        block.setName("块");
        Resource resource = new ResourceImpl(URI.createURI("memory:/m.json"));
        new ResourceSetImpl().getResources().add(resource);
        resource.getContents().add(block);
        ChangeRecorder recorder = new ChangeRecorder(resource.getResourceSet());
        block.setName("块 v2");
        ChangeDescription change = recorder.endRecording();
        recorder.dispose();
        return change;
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-10T06:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.now;
        }
    }
}

package com.example.sysarch.sync;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.ElementIds;
import com.example.sysarch.link.LinkRepository;
import com.example.sysarch.link.LinkRepository.StoredLink;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.PortDirection;
import com.example.sysarch.sync.api.model.ConsistencyRepairPostRequest;
import com.example.sysarch.sync.api.model.ConsistencyReport;
import com.example.sysarch.sync.api.model.ConsistencyReportIssuesInner;
import com.example.sysarch.sync.consistency.ConsistencyController;
import com.example.sysarch.sync.consistency.ConsistencyProperties;
import com.example.sysarch.sync.consistency.ConsistencyRules;
import com.example.sysarch.sync.consistency.ConsistencyService;
import com.example.sysarch.sync.consistency.ConsistencyService.RepairAction;
import com.example.sysarch.sync.consistency.ConsistencyStore;
import com.example.sysarch.sync.consistency.PolarionEventConsumer;
import com.example.sysarch.sync.consistency.PolarionWebhookController;
import com.example.sysarch.sync.polarion.PolarionClient;
import com.example.sysarch.sync.polarion.TokenBucket;
import com.example.sysarch.validation.ConstraintValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import liquibase.integration.spring.SpringLiquibase;

/**
 * T8 acceptance (spec §8): "造 5 个异常 fixture（删 WI、删关系、重复绑定、跨层分配、未绑定元素），报告命中 5/5", plus the
 * repair actions per rule and strategy, the fixed per-project strategy, and the signed webhook queue.
 */
class ConsistencyIT {

    private static final String PROJECT = "sysarch";

    private static final String WI = "/polarion/rest/v1/projects/sysarch/workitems";

    private static final String SECRET = "webhook-secret-for-tests";

    private static ConstraintValidator constraints;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private WireMockServer polarion;

    private JdbcClient jdbc;

    private LinkRepository links;

    private ConsistencyStore store;

    private InMemoryModel model;

    private ConsistencyProperties strategy;

    private SyncProperties syncProperties;

    private ConsistencyController controller;

    private MockHttpServletRequest request;

    @BeforeAll
    static void loadOcl() {
        constraints = new ConstraintValidator();
    }

    @AfterAll
    static void disposeOcl() {
        constraints.close();
    }

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
        this.links = new LinkRepository(this.jdbc);
        this.store = new ConsistencyStore(this.jdbc);
        SyncStore syncStore = new SyncStore(this.jdbc);
        this.polarion = new WireMockServer(wireMockConfig().dynamicPort());
        this.polarion.start();
        var client = new PolarionClient(this.polarion.baseUrl() + "/polarion", new TokenBucket(1000), 3, this.json, duration -> { });
        this.model = new InMemoryModel(PROJECT, constraints);
        this.syncProperties = new SyncProperties();
        this.syncProperties.setWebhookSecret(SECRET);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var sync = new SyncService(this.model, this.links, syncStore, client, this.syncProperties, this.json, transaction);
        this.strategy = new ConsistencyProperties();
        this.strategy.setStrategy(ConsistencyProperties.MODEL_WINS);
        var service = new ConsistencyService(this.model, this.links, this.store, syncStore, sync, client, this.syncProperties, this.strategy, this.json,
                transaction);
        this.request = new MockHttpServletRequest();
        this.request.addHeader("Authorization", "Bearer user-pat-123");
        this.request.addHeader("X-Sysarch-User", "alice");
        this.controller = new ConsistencyController(service, this.request);
    }

    @AfterEach
    void tearDown() {
        this.polarion.stop();
    }

    // =========================================================== acceptance: 5 fixtures, 5 hits

    /** The five anomalies of T8's acceptance in one project; every other element is bound and in step. */
    @Test
    void fiveAnomaliesFiveHits() {
        // 删 WI: bound, the Work Item was deleted in Polarion
        this.boundBlock("PD-1", "ECU", "SYS-1");
        this.polarion.stubFor(get(urlPathEqualTo(WI + "/SYS-1")).willReturn(aResponse().withStatus(404)));
        // 删关系: the connection's relation was removed in Polarion (endpoint fields emptied)
        this.boundConnection("CO-1", "SYS-2", null, null);
        // 重复绑定: one Work Item, two elements
        this.boundBlock("PD-2", "Gateway", "SYS-3");
        this.model.block("PD-3", "Gateway copy");
        this.bind("PD-3", "PartDefinition", "SYS-3");
        // 跨层分配: LOGICAL -> EPBS skips two layers
        PartDefinition logical = this.boundBlock("PD-4", "Controller", "SYS-4");
        PartDefinition epbs = this.boundBlock("PD-5", "Board", "SYS-5");
        epbs.setLayer(Layer.EPBS);
        this.model.allocate("AL-1", logical, epbs);
        // 未绑定元素
        this.model.block("PD-6", "Sensor");

        ConsistencyReport report = this.controller.report(PROJECT);

        assertEquals(List.of("R1 BLOCKER PD-1 SYS-1", "R2 MAJOR CO-1 SYS-2", "R3 MAJOR PD-2 SYS-3", "R4 BLOCKER AL-1 null", "R5 MINOR PD-6 null"),
                report.getIssues().stream().map(i -> i.getRule().getValue() + " " + i.getSeverity().getValue() + " " + i.getElementId() + " " + i.getWorkItemId())
                        .toList());
        assertEquals(List.of("R1:PD-1", "R2:CO-1", "R3:SYS-3", "R4:AL-1", "R5:PD-6"), report.getIssues().stream().map(ConsistencyReportIssuesInner::getId).toList());
        assertEquals(List.of(true, true, true, false, true), report.getIssues().stream().map(ConsistencyReportIssuesInner::getRepairable).toList());
        assertTrue(issue(report, "R2").getMessage().contains("SYS-2 无对应关系"), issue(report, "R2").getMessage());
        assertTrue(issue(report, "R4").getMessage().contains("LOGICAL") || issue(report, "R4").getMessage().contains("LA"), issue(report, "R4").getMessage());
        this.polarion.verify(0, patchRequestedFor(anyUrl())); // the report only detects
    }

    /** 删关系 on the other side: the connection was deleted on the canvas while its Work Item still relates the ports. */
    @Test
    void aConnectionDeletedOnTheCanvasIsADanglingEdge() {
        Connection connection = this.boundConnection("CO-1", "SYS-2", "PO-1", "PO-2");
        EcoreUtil.delete(connection, true);

        assertEquals(List.of("R2:CO-1"), ids(this.controller.report(PROJECT)));
    }

    /** Without a token: R1 from what pull or a signed webhook recorded; R2 only on the canvas side. */
    @Test
    void withoutATokenTheReportJudgesFromRecordedState() {
        this.request.removeHeader("Authorization");
        this.boundBlock("PD-1", "ECU", "SYS-1");
        this.links.update(this.links.find("PD-1").orElseThrow().withState("ORPHAN")); // a pull saw the 404
        this.boundBlock("PD-2", "Gateway", "SYS-2");
        this.store.consume(new ConsistencyStore.QueuedEvent(0, PROJECT, ConsistencyStore.DELETED, "SYS-2", "r9")); // a webhook said so
        this.boundConnection("CO-1", "SYS-3", null, null); // edited in Polarion: not visible without asking Polarion

        assertEquals(List.of("R1:PD-1", "R1:PD-2"), ids(this.controller.report(PROJECT)));
        this.polarion.verify(0, anyRequestedFor(anyUrl()));
    }

    // =========================================================== repair, rule by rule

    /** R1: unbind, the element is "待认领"; the next report lists it as such, and binding it again ends that. */
    @Test
    void repairingAnOrphanUnbindsAndMarksPendingClaim() {
        this.boundBlock("PD-1", "ECU", "SYS-1");
        this.polarion.stubFor(get(urlPathEqualTo(WI + "/SYS-1")).willReturn(aResponse().withStatus(404)));

        RepairAction action = this.repair("R1:PD-1").get(0);

        assertEquals("DONE UNBIND_AND_MARK_PENDING_CLAIM", action.status() + " " + action.action());
        assertTrue(this.links.find("PD-1").isEmpty());
        ConsistencyReportIssuesInner pending = issue(this.controller.report(PROJECT), "R5");
        assertTrue(pending.getMessage().contains("待认领（原绑定 SYS-1 已删除）"), pending.getMessage());
        assertEquals("SYS-1", pending.getWorkItemId());
        assertEquals(List.of("REPAIR binding SYS-1 -> null by alice"), this.audit("PD-1"));

        this.bind("PD-1", "PartDefinition", "SYS-7");
        assertEquals(Map.of(), this.store.pendingClaims(PROJECT));
    }

    /** R2 with MODEL_WINS: the model's endpoints are written to the Work Item. */
    @Test
    void modelWinsWritesTheCanvasEndpointsToPolarion() {
        this.boundConnection("CO-1", "SYS-2", "PO-9", "PO-2");
        this.polarion.stubFor(patch(urlPathEqualTo(WI + "/SYS-2")).willReturn(aResponse().withStatus(204)));

        RepairAction action = this.repair("R2:CO-1").get(0);

        assertEquals("DONE WRITE_ENDPOINTS_TO_POLARION", action.status() + " " + action.action(), action.message());
        this.polarion.verify(patchRequestedFor(urlPathEqualTo(WI + "/SYS-2")).withRequestBody(equalToJson(
                "{\"data\": {\"type\": \"workitems\", \"id\": \"sysarch/SYS-2\", \"attributes\": {\"sysarchSource\": \"PO-1\", \"sysarchTarget\": \"PO-2\"}}}")));
        assertTrue(this.links.find("CO-1").orElseThrow().pushedFields().contains("\"sysarchSource\":\"PO-1\""));
    }

    /** R2 with MODEL_WINS when the canvas has no such connection: the Work Item goes. */
    @Test
    void modelWinsDeletesTheWorkItemOfAConnectionGoneFromTheCanvas() {
        EcoreUtil.delete(this.boundConnection("CO-1", "SYS-2", "PO-1", "PO-2"), true);
        this.polarion.stubFor(delete(urlPathEqualTo(WI)).willReturn(aResponse().withStatus(204)));

        assertEquals("DONE DELETE_WORK_ITEM", summary(this.repair("R2:CO-1").get(0)));
        this.polarion.verify(deleteRequestedFor(urlPathEqualTo(WI)).withRequestBody(equalToJson("{\"data\": [{\"type\": \"workitems\", \"id\": \"sysarch/SYS-2\"}]}")));
        assertTrue(this.links.find("CO-1").isEmpty());
    }

    /** R2 with POLARION_WINS: the model takes the Work Item's endpoints, through the on-drop constraints. */
    @Test
    void polarionWinsRewiresTheConnectionInTheModel() {
        this.strategy.setStrategy(ConsistencyProperties.POLARION_WINS);
        Connection connection = this.boundConnection("CO-1", "SYS-2", "PO-1", "PO-3");
        PartDefinition owner = (PartDefinition) connection.getSource().eContainer();
        this.model.port(owner, "PO-3", PortDirection.IN);
        this.bind("PO-3", "Port", "SYS-PO-3");

        assertEquals("DONE SET_CONNECTION_ENDPOINTS", summary(this.repair("R2:CO-1").get(0)));
        assertEquals("PO-3", ElementIds.of(connection.getTarget()));
        this.polarion.verify(0, patchRequestedFor(anyUrl()));
        assertEquals(List.of(), ids(this.controller.report(PROJECT)), "model and Polarion agree again");
    }

    /** POLARION_WINS cannot create what the editor would refuse: here a connection from a port to itself (NoSelfLoop). */
    @Test
    void polarionWinsStillRespectsTheConnectionConstraints() {
        this.strategy.setStrategy(ConsistencyProperties.POLARION_WINS);
        Connection connection = this.boundConnection("CO-1", "SYS-2", "PO-2", "PO-2");

        RepairAction action = this.repair("R2:CO-1").get(0);

        assertEquals("FAILED", action.status());
        assertTrue(action.message().contains("NoSelfLoop"), action.message());
        assertEquals("PO-1", ElementIds.of(connection.getSource()), "the model is unchanged");
    }

    /** POLARION_WINS, Polarion has no relation: the connection leaves the model and the binding goes. */
    @Test
    void polarionWinsDeletesAConnectionPolarionDoesNotHave() {
        this.strategy.setStrategy(ConsistencyProperties.POLARION_WINS);
        this.boundConnection("CO-1", "SYS-2", null, null);

        assertEquals("DONE DELETE_CONNECTION", summary(this.repair("R2:CO-1").get(0)));
        assertTrue(this.model.readAll(PROJECT).get("CO-1") == null && this.links.find("CO-1").isEmpty());
    }

    /** R3: keep the element the Work Item names, mark the others CONFLICT; R3 is then resolved. */
    @Test
    void repairingADuplicateKeepsTheElementTheWorkItemNames() {
        this.model.block("PD-2", "Gateway");
        this.model.block("PD-3", "Gateway copy");
        this.bind("PD-2", "PartDefinition", "SYS-3");
        this.bind("PD-3", "PartDefinition", "SYS-3");
        this.stubWorkItem("SYS-3", Map.of("sysarchElementId", "PD-3"));

        RepairAction action = this.repair("R3:SYS-3").get(0);

        assertEquals("DONE KEEP_ONE_MARK_OTHERS_CONFLICT", summary(action));
        assertEquals("CONFLICT", this.links.find("PD-2").orElseThrow().state());
        assertEquals("ACTIVE", this.links.find("PD-3").orElseThrow().state());
        assertEquals(List.of(), ids(this.controller.report(PROJECT)));
    }

    @Test
    void aLayerViolationIsNeverRepaired() {
        PartDefinition logical = this.boundBlock("PD-4", "Controller", "SYS-4");
        PartDefinition epbs = this.boundBlock("PD-5", "Board", "SYS-5");
        epbs.setLayer(Layer.EPBS);
        this.model.allocate("AL-1", logical, epbs);

        RepairAction action = this.repair("R4:AL-1").get(0);

        assertEquals("REFUSED", action.status());
        assertEquals(Layer.EPBS, epbs.getLayer());
    }

    /** R5: the Work Items are created by one push (all or nothing). */
    @Test
    void repairingUnboundElementsCreatesTheirWorkItems() {
        this.model.block("PD-6", "Sensor");
        this.polarion.stubFor(post(urlPathEqualTo(WI)).willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                .withBody("{\"data\": [{\"type\": \"workitems\", \"id\": \"sysarch/SYS-60\"}]}")));

        RepairAction action = this.repair("R5:PD-6").get(0);

        assertEquals("DONE CREATE_WORK_ITEM", summary(action));
        assertEquals("SYS-60", this.links.find("PD-6").orElseThrow().workItemId());
    }

    // =========================================================== repair rules

    @Test
    void theStrategyIsFixedPerProject() {
        var switched = assertThrows(ResponseStatusException.class, () -> this.controller.repair(new ConsistencyRepairPostRequest().projectId(PROJECT)
                .issueIds(List.of("R5:PD-6")).strategy(ConsistencyRepairPostRequest.StrategyEnum.POLARION_WINS)));
        assertEquals(409, switched.getStatusCode().value());

        this.strategy.setStrategy("");
        var unset = assertThrows(ResponseStatusException.class, () -> this.repair("R5:PD-6"));
        assertEquals(409, unset.getStatusCode().value());

        this.strategy.getStrategies().put(PROJECT, ConsistencyProperties.POLARION_WINS);
        assertEquals(ConsistencyProperties.POLARION_WINS, this.strategy.strategyOf(PROJECT), "a per-project setting overrides the default");
    }

    @Test
    void thereIsNoRepairAll() {
        var error = assertThrows(ResponseStatusException.class, () -> this.controller.repair(new ConsistencyRepairPostRequest().projectId(PROJECT)));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void anIssueNoLongerReportedIsSkipped() {
        RepairAction action = this.repair("R1:PD-404").get(0);

        assertEquals("SKIPPED", action.status());
        assertNull(action.rule());
    }

    // =========================================================== webhooks

    /** A signed event is queued (202); the consumer turns it into a mark that the next report judges as R1. */
    @Test
    void aSignedWebhookIsQueuedAndJudgedByTheNextReport() {
        this.boundBlock("PD-1", "ECU", "SYS-1");
        byte[] body = "{\"event\":\"workitem.deleted\",\"projectId\":\"sysarch\",\"workItemId\":\"sysarch/SYS-1\",\"revision\":\"42\"}".getBytes(StandardCharsets.UTF_8);
        var webhooks = new PolarionWebhookController(this.store, this.syncProperties, this.json);

        assertEquals(202, webhooks.receive("sha256=" + hmac(body), body).getStatusCode().value());
        assertEquals(Map.of(), this.store.pendingChecks(PROJECT), "receiving only queues");

        new PolarionEventConsumer(this.store).consumeQueued();
        assertEquals(Map.of("SYS-1", ConsistencyStore.DELETED), this.store.pendingChecks(PROJECT));
        assertTrue(this.links.find("PD-1").isPresent(), "the consumer changes no data");

        this.request.removeHeader("Authorization");
        assertEquals(List.of("R1:PD-1"), ids(this.controller.report(PROJECT)));
    }

    @Test
    void aWebhookWithABadSignatureIsRejected() {
        byte[] body = "{\"event\":\"workitem.deleted\",\"projectId\":\"sysarch\",\"workItemId\":\"SYS-1\"}".getBytes(StandardCharsets.UTF_8);
        var webhooks = new PolarionWebhookController(this.store, this.syncProperties, this.json);

        assertEquals(401, assertThrows(ResponseStatusException.class, () -> webhooks.receive("sha256=" + hmac("other".getBytes(StandardCharsets.UTF_8)), body))
                .getStatusCode().value());
        this.syncProperties.setWebhookSecret("");
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> webhooks.receive(hmac(body), body)).getStatusCode().value());
        assertEquals(0, this.store.unprocessed(10).size());
    }

    /** A live report judges the marks, so they do not linger. */
    @Test
    void aLiveReportClearsTheMarksItJudged() {
        this.boundBlock("PD-1", "ECU", "SYS-1");
        this.store.consume(new ConsistencyStore.QueuedEvent(0, PROJECT, ConsistencyStore.UPDATED, "SYS-1", "r2"));

        assertEquals(List.of(), ids(this.controller.report(PROJECT)));
        assertEquals(Map.of(), this.store.pendingChecks(PROJECT));
    }

    /** Sirius' Validation view colours errors red and warnings yellow. */
    @Test
    void blockersAreErrorsTheRestWarnings() {
        var blocker = new ConsistencyRules.Issue("R1:X", "R1", ConsistencyRules.BLOCKER, "X", "W", "m", true, null, List.of());
        var minor = new ConsistencyRules.Issue("R5:X", "R5", ConsistencyRules.MINOR, "X", null, "m", true, null, List.of());

        Diagnostic error = com.example.sysarch.sync.consistency.ConsistencyDiagnostics.toDiagnostic(blocker, null);
        assertEquals(Diagnostic.ERROR, error.getSeverity());
        assertEquals("[R1 BLOCKER] m", error.getMessage());
        assertEquals(Diagnostic.WARNING, com.example.sysarch.sync.consistency.ConsistencyDiagnostics.toDiagnostic(minor, null).getSeverity());
    }

    // =========================================================== helpers

    private List<RepairAction> repair(String... issueIds) {
        return this.controller.repair(new ConsistencyRepairPostRequest().projectId(PROJECT).issueIds(List.of(issueIds))).actions();
    }

    /** A block bound to a Work Item that is in step with it. */
    private PartDefinition boundBlock(String id, String name, String workItemId) {
        PartDefinition block = this.model.block(id, name);
        this.bind(id, "PartDefinition", workItemId);
        this.stubWorkItem(workItemId, Map.of("sysarchElementId", id));
        return block;
    }

    /**
     * A connection PO-1 -> PO-2 bound to a Work Item whose endpoint fields are {@code source}/{@code target} (null =
     * no such field). Its owner block and ports are bound too, so they raise no R5.
     */
    private Connection boundConnection(String id, String workItemId, String source, String target) {
        Connection connection = this.model.connection(id, "PO-1", "PO-2");
        this.bind(id, "Connection", workItemId);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sysarchElementId", id);
        if (source != null) {
            fields.put("sysarchSource", source);
        }
        if (target != null) {
            fields.put("sysarchTarget", target);
        }
        this.stubWorkItem(workItemId, fields);
        PartDefinition owner = (PartDefinition) connection.getSource().eContainer();
        this.bind(ElementIds.of(owner), "PartDefinition", "SYS-" + ElementIds.of(owner));
        for (Port port : owner.getOwnedPorts()) {
            this.bind(ElementIds.of(port), "Port", "SYS-" + ElementIds.of(port));
        }
        return connection;
    }

    private void bind(String elementId, String elementType, String workItemId) {
        this.links.insert(new StoredLink(elementId, elementType, workItemId, "Connection".equals(elementType) ? "architecture_connection" : "architecture_element",
                PROJECT, "ACTIVE", null, null, null, null, null, null, null));
        if (!this.polarion.getStubMappings().stream().anyMatch(s -> s.getRequest().getUrlPath() != null && s.getRequest().getUrlPath().endsWith("/" + workItemId))) {
            this.stubWorkItem(workItemId, Map.of("sysarchElementId", elementId));
        }
    }

    private void stubWorkItem(String workItemId, Map<String, Object> customFields) {
        Map<String, Object> attributes = new LinkedHashMap<>(customFields);
        attributes.put("title", workItemId);
        attributes.put("updated", OffsetDateTime.parse("2026-11-05T10:00:00Z").toString());
        try {
            this.polarion.stubFor(get(urlPathEqualTo(WI + "/" + workItemId)).willReturn(okJson(this.json.writeValueAsString(
                    Map.of("data", Map.of("type", "workitems", "id", PROJECT + "/" + workItemId, "attributes", attributes))))));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> audit(String elementId) {
        return this.jdbc.sql("SELECT direction || ' ' || field || ' ' || COALESCE(old_value, 'null') || ' -> ' || COALESCE(new_value, 'null') || ' by ' || actor "
                + "FROM sync_audit WHERE element_id = :id ORDER BY id").param("id", elementId).query(String.class).list();
    }

    private static String hmac(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(body));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ConsistencyReportIssuesInner issue(ConsistencyReport report, String rule) {
        return report.getIssues().stream().filter(i -> i.getRule().getValue().equals(rule)).findFirst().orElseThrow(() -> new AssertionError(rule + " not in " + ids(report)));
    }

    private static List<String> ids(ConsistencyReport report) {
        return report.getIssues().stream().map(ConsistencyReportIssuesInner::getId).toList();
    }

    private static String summary(RepairAction action) {
        return action.status() + " " + action.action() + (action.status().equals("DONE") ? "" : " " + action.message());
    }
}

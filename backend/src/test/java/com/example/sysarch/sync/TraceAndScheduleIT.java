package com.example.sysarch.sync;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.link.LinkRepository;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.sync.api.model.SyncPullPostRequest;
import com.example.sysarch.sync.api.model.SyncPushPostRequest;
import com.example.sysarch.sync.api.model.SyncResult;
import com.example.sysarch.sync.polarion.PolarionClient;
import com.example.sysarch.sync.polarion.TokenBucket;
import com.example.sysarch.validation.ConstraintValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;

import liquibase.integration.spring.SpringLiquibase;

/**
 * P1 T6 against a WireMock Polarion: satisfy/verify as native Polarion links, reqId from Polarion, and the scheduled
 * incremental pull. Acceptance (spec P1 T6): "Mock Server 模拟改标题、删 WI、改端点三个场景，各自产生正确结果".
 */
class TraceAndScheduleIT {

    private static final String PROJECT = "sysarch";

    private static final String WI = "/polarion/rest/v1/projects/sysarch/workitems";

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-09T08:00:00Z");

    private static ConstraintValidator constraints;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private WireMockServer polarion;

    private JdbcClient jdbc;

    private LinkRepository links;

    private InMemoryModel model;

    private SyncController controller;

    private SyncService service;

    private SyncStatusStore status;

    private SyncProperties properties;

    private MutableClock clock;

    private ScheduledPull scheduler;

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
        SyncStore store = new SyncStore(this.jdbc);
        this.polarion = new WireMockServer(wireMockConfig().dynamicPort());
        this.polarion.start();
        var client = new PolarionClient(this.polarion.baseUrl() + "/polarion", new TokenBucket(1000), 3, this.json, d -> { });
        this.model = new InMemoryModel(PROJECT, constraints);
        this.properties = new SyncProperties();
        this.properties.getProjects().put(PROJECT, "sirius-project");
        this.properties.setSyncToken("service-account-pat");
        this.service = new SyncService(this.model, this.links, store, client, this.properties, this.json,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer user-pat-123");
        request.addHeader(SyncController.ACTOR_HEADER, "alice");
        this.status = new SyncStatusStore(this.jdbc);
        this.controller = new SyncController(this.service, store, this.json, request, this.status);
        this.clock = new MutableClock(T0.toInstant());
        this.scheduler = new ScheduledPull(this.service, this.status, this.properties, this.clock);
    }

    @AfterEach
    void tearDown() {
        this.polarion.stop();
    }

    // =========================================================== satisfy / verify as Polarion links (P1-AC-3)

    /** One push binds the requirement and the block and links them: block Work Item "implements" requirement Work Item. */
    @Test
    void pushingASatisfyRelationCreatesANativePolarionLink() {
        Requirement requirement = this.model.requirement("RQ-1", "Speed", "text");
        PartDefinition block = this.model.block("PD-1", "Controller");
        requirement.getSatisfiedBy().add(block);
        this.stubCreates();
        this.stubLinks("SYS-1");

        ResponseEntity<SyncResult> pushed = this.push("RQ-1");

        assertEquals(200, pushed.getStatusCode().value(), String.valueOf(pushed.getBody()));
        assertEquals(2, pushed.getBody().getPushed(), "pushing the requirement brings the element that satisfies it");
        this.polarion.verify(postRequestedFor(urlPathEqualTo(WI + "/SYS-1/linkedworkitems")).withRequestBody(equalToJson("""
                {"data": [{"type": "linkedworkitems", "attributes": {"role": "implements"},
                  "relationships": {"workItem": {"data": {"type": "workitems", "id": "sysarch/REQ-1"}}}}]}""")));
        assertTrue(this.links.find("PD-1").orElseThrow().pushedFields().contains("\"traceLinks\":\"implements:REQ-1\""));
        assertTrue(this.audit("PD-1").contains("PUSH traceLinks  -> implements:REQ-1 by alice"), this.audit("PD-1").toString());
    }

    @Test
    void verifyUsesTheVerifiesRole() {
        Requirement requirement = this.model.requirement("RQ-1", "Speed", "text");
        PartDefinition block = this.model.block("PD-1", "Controller");
        requirement.getVerifiedBy().add(block);
        this.stubCreates();
        this.stubLinks("SYS-1");

        this.push("RQ-1");

        this.polarion.verify(postRequestedFor(urlPathEqualTo(WI + "/SYS-1/linkedworkitems")).withRequestBody(containing("\"role\":\"verifies\"")));
    }

    /** Unchanged links are not written again; a removed relation is removed in Polarion when the requirement is pushed. */
    @Test
    void aRemovedRelationIsRemovedInPolarion() {
        Requirement requirement = this.model.requirement("RQ-1", "Speed", "text");
        PartDefinition block = this.model.block("PD-1", "Controller");
        requirement.getSatisfiedBy().add(block);
        this.stubCreates();
        this.stubLinks("SYS-1");
        this.push("RQ-1");
        this.stubWorkItem("SYS-1", "Controller", this.fields("PD-1", "Controller"), List.of("implements/sysarch/REQ-1"));
        this.stubWorkItem("REQ-1", "Speed", this.fields("RQ-1", "Speed"), List.of());

        SyncResult again = this.push("RQ-1").getBody();

        assertEquals(2, again.getSkipped(), "nothing changed");
        this.polarion.verify(1, postRequestedFor(urlPathEqualTo(WI + "/SYS-1/linkedworkitems")));

        requirement.getSatisfiedBy().clear();
        SyncResult removed = this.push("RQ-1").getBody();

        assertEquals(List.of(), removed.getErrors());
        this.polarion.verify(deleteRequestedFor(urlPathEqualTo(WI + "/SYS-1/linkedworkitems")).withRequestBody(equalToJson("""
                {"data": [{"type": "linkedworkitems", "id": "sysarch/SYS-1/implements/sysarch/REQ-1"}]}""")));
        assertTrue(this.links.find("PD-1").orElseThrow().pushedFields().contains("\"traceLinks\":\"\""));
    }

    /** "两端都已绑定才写": an element satisfying an unbound requirement cannot be pushed alone, and nothing is written. */
    @Test
    void aRelationToAnUnboundRequirementStopsThePush() {
        Requirement requirement = this.model.requirement("RQ-1", "Speed", "text");
        PartDefinition block = this.model.block("PD-1", "Controller");
        requirement.getSatisfiedBy().add(block);
        this.stubCreates();

        SyncResult result = this.push("PD-1").getBody();

        assertEquals("TRACE_TARGET_NOT_BOUND", result.getErrors().get(0).getCode());
        assertEquals(0, result.getPushed());
        this.polarion.verify(0, postRequestedFor(anyUrl()));
    }

    /** A managed link removed (or added) in Polarion is a CONFLICT on pull, never merged. */
    @Test
    void aLinkEditedInPolarionIsAConflict() {
        Requirement requirement = this.model.requirement("RQ-1", "Speed", "text");
        PartDefinition block = this.model.block("PD-1", "Controller");
        requirement.getSatisfiedBy().add(block);
        this.stubCreates();
        this.stubLinks("SYS-1");
        this.push("RQ-1");
        this.stubWorkItem("SYS-1", "Controller", this.fields("PD-1", "Controller"), List.of()); // the link was deleted in Polarion
        this.stubWorkItem("REQ-1", "Speed", this.fields("RQ-1", "Speed"), List.of());

        SyncResult pulled = this.pull().getBody();

        assertEquals(List.of("traceLinks"), pulled.getConflicts().get(0).getFields());
        assertEquals("CONFLICT", this.links.find("PD-1").orElseThrow().state());
        assertEquals(List.of(block), requirement.getSatisfiedBy(), "the model keeps its relation");
    }

    /** reqId is Polarion-owned (user's decision: the Work Item id). */
    @Test
    void pullWritesTheWorkItemIdIntoReqId() {
        Requirement requirement = this.model.requirement("RQ-1", "Speed", "text");
        requirement.setReqId("DC-REQ-001");
        this.stubCreates();
        this.push("RQ-1");
        this.stubWorkItem("REQ-1", "Speed", this.fields("RQ-1", "Speed"), List.of());

        this.pull();

        assertEquals("REQ-1", requirement.getReqId());
        assertTrue(this.audit("RQ-1").contains("PULL reqId DC-REQ-001 -> REQ-1 by alice"));
    }

    // =========================================================== the scheduled incremental pull: the three scenarios

    /** "改标题": the Work Item renamed in Polarion → the title on the binding, audited as the scheduler. */
    @Test
    void scheduledPullTakesARenamedWorkItemsTitle() {
        this.bindBlock();
        this.stubUpdated("SYS-1", "Controller (renamed in Polarion)", this.fields("PD-1", "Controller"));
        this.stubExisting("SYS-1");

        assertTrue(this.scheduler.runIfDue(PROJECT));

        assertEquals("Controller (renamed in Polarion)", this.links.find("PD-1").orElseThrow().workItemTitle());
        assertTrue(this.audit("PD-1").contains("PULL title Controller -> Controller (renamed in Polarion) by scheduler"), this.audit("PD-1").toString());
        assertEquals("Controller", this.model.resource.getContents().stream().map(e -> ((com.example.sysarch.model.ArchElement) e).getName()).findFirst().orElseThrow());
        assertEquals(0, this.status.status(PROJECT).scheduleFailures());
        assertEquals(T0, this.status.status(PROJECT).scheduleLastSuccessAt());
    }

    /** "删 WI": the Work Item is gone → the binding is an ORPHAN (R1 for the consistency report). */
    @Test
    void scheduledPullOrphansADeletedWorkItem() {
        this.bindBlock();
        this.stubUpdated(null, null, null);
        this.stubExisting();

        this.scheduler.runIfDue(PROJECT);

        assertEquals("ORPHAN", this.links.find("PD-1").orElseThrow().state());
    }

    /** "改端点": a Connection's source edited in Polarion → CONFLICT, the model unchanged. */
    @Test
    void scheduledPullMarksAnEditedEndpointAsAConflict() {
        this.model.connection("CO-1", "PO-1", "PO-2");
        this.stubCreates();
        this.push("CO-1");
        Map<String, Object> edited = new LinkedHashMap<>(Map.of("sysarchElementId", "CO-1", "sysarchName", "bus", "sysarchLayer", "LOGICAL",
                "sysarchSource", "PO-99", "sysarchTarget", "PO-2"));
        this.stubUpdated("SYS-1", "bus", edited);
        this.stubExisting("SYS-1");

        this.scheduler.runIfDue(PROJECT);

        assertEquals("CONFLICT", this.links.find("CO-1").orElseThrow().state());
    }

    /** Incremental: an unchanged Work Item is not read, and the query asks only for what changed since the last success. */
    @Test
    void theScheduledPullOnlyReadsWhatChanged() {
        this.bindBlock();
        this.stubUpdated(null, null, null);
        this.stubExisting("SYS-1");

        this.scheduler.runIfDue(PROJECT);

        this.polarion.verify(0, com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathEqualTo(WI + "/SYS-1")));
        this.polarion.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathEqualTo(WI))
                .withQueryParam("query", containing("updated:[20261109 TO *]")));
        assertEquals("ACTIVE", this.links.find("PD-1").orElseThrow().state());
    }

    // =========================================================== schedule: interval, retry, pause (P1-AC-6)

    @Test
    void itRunsEveryIntervalAndPullsSinceTheLastSuccess() {
        this.bindBlock();
        this.stubUpdated(null, null, null);
        this.stubExisting("SYS-1");
        assertTrue(this.scheduler.runIfDue(PROJECT));

        this.clock.advance(Duration.ofMinutes(10));
        assertFalse(this.scheduler.runIfDue(PROJECT), "not due before 15 minutes");
        this.clock.advance(Duration.ofMinutes(6));
        assertTrue(this.scheduler.runIfDue(PROJECT));
        assertEquals(T0.plusMinutes(16), this.status.status(PROJECT).scheduleLastSuccessAt());
    }

    /** "失败退避 2 分钟后重试一次，连续 3 次失败暂停并告警"; editing never depends on it, and Polarion back resumes it. */
    @Test
    void failuresRetryOnceAfterTwoMinutesAndPauseAfterThree() {
        this.bindBlock();
        this.polarion.stubFor(get(urlPathEqualTo(WI)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        this.scheduler.runIfDue(PROJECT);
        SyncStatusStore.Status first = this.status.status(PROJECT);
        assertEquals(1, first.scheduleFailures());
        assertEquals(T0.plusMinutes(2), first.scheduleNextAttemptAt(), "one retry after 2 minutes");
        assertTrue(first.scheduleLastError().startsWith("POLARION_ERROR"), first.scheduleLastError());

        this.clock.advance(Duration.ofMinutes(2));
        this.scheduler.runIfDue(PROJECT);
        assertEquals(T0.plusMinutes(17), this.status.status(PROJECT).scheduleNextAttemptAt(), "then back to the interval");

        this.clock.advance(Duration.ofMinutes(15));
        this.scheduler.runIfDue(PROJECT);
        SyncStatusStore.Status paused = this.status.status(PROJECT);
        assertTrue(paused.schedulePaused(), "paused after 3 failures in a row");
        assertNull(paused.scheduleNextAttemptAt());
        this.clock.advance(Duration.ofHours(1));
        assertFalse(this.scheduler.runIfDue(PROJECT), "a paused schedule does not run");

        this.polarion.resetAll();
        this.stubWorkItem("SYS-1", "Controller", this.fields("PD-1", "Controller"), List.of());
        assertEquals(200, this.pull().getStatusCode().value());
        assertFalse(this.status.status(PROJECT).schedulePaused(), "a successful manual pull resumes the schedule");
    }

    @Test
    void withoutAServiceAccountNothingRuns() {
        this.properties.setSyncToken("");

        assertFalse(this.scheduler.enabled());
        this.scheduler.tick();

        this.polarion.verify(0, com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor(anyUrl()));
    }

    @Test
    void pushAndPullTimesAreRecordedForThePanel() {
        this.bindBlock();
        this.stubWorkItem("SYS-1", "Controller", this.fields("PD-1", "Controller"), List.of());

        this.pull();

        SyncStatusStore.Status state = this.status.status(PROJECT);
        assertTrue(state.lastPushAt() != null && state.lastPullAt() != null);
    }

    // =========================================================== helpers

    private void bindBlock() {
        this.model.block("PD-1", "Controller");
        this.stubCreates();
        assertEquals(200, this.push("PD-1").getStatusCode().value());
    }

    private ResponseEntity<SyncResult> push(String... ids) {
        return this.controller.syncPushPost(UUID.randomUUID().toString(), new SyncPushPostRequest().projectId(PROJECT).elementIds(List.of(ids)));
    }

    private ResponseEntity<SyncResult> pull() {
        return this.controller.syncPullPost(new SyncPullPostRequest().projectId(PROJECT));
    }

    /** Requirements become REQ-1, everything else SYS-1. */
    private void stubCreates() {
        this.polarion.stubFor(post(urlPathEqualTo(WI)).withRequestBody(containing("\"type\":\"requirement\""))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("{\"data\": [{\"type\": \"workitems\", \"id\": \"sysarch/REQ-1\"}]}")));
        this.polarion.stubFor(post(urlPathEqualTo(WI)).withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.notContaining("\"type\":\"requirement\""))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("{\"data\": [{\"type\": \"workitems\", \"id\": \"sysarch/SYS-1\"}]}")));
    }

    private void stubLinks(String workItemId) {
        this.polarion.stubFor(post(urlPathEqualTo(WI + "/" + workItemId + "/linkedworkitems")).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json").withBody("{\"data\": []}")));
        this.polarion.stubFor(delete(urlPathEqualTo(WI + "/" + workItemId + "/linkedworkitems")).willReturn(aResponse().withStatus(204)));
    }

    private Map<String, Object> fields(String elementId, String name) {
        return new LinkedHashMap<>(Map.of("sysarchElementId", elementId, "sysarchName", name, "sysarchLayer", "LOGICAL"));
    }

    /** {@code links}: "role/project/target" of the Work Item's outgoing links. */
    private void stubWorkItem(String workItemId, String title, Map<String, Object> fields, List<String> links) {
        this.polarion.stubFor(get(urlPathEqualTo(WI + "/" + workItemId)).willReturn(okJson(this.resource(workItemId, title, fields, links, true))));
    }

    /** The "updated since" query answers with this Work Item (or none). */
    private void stubUpdated(String workItemId, String title, Map<String, Object> fields) {
        String data = workItemId == null ? "" : this.resource(workItemId, title, fields, List.of(), false);
        this.polarion.stubFor(get(urlPathEqualTo(WI)).withQueryParam("query", containing("updated:")).willReturn(okJson("{\"data\": [" + data + "]}")));
        this.polarion.stubFor(get(urlPathEqualTo(WI)).withQueryParam("query", containing("created:")).willReturn(okJson("{\"data\": []}")));
    }

    /** The existence check answers with these ids. */
    private void stubExisting(String... workItemIds) {
        StringBuilder data = new StringBuilder();
        for (String id : workItemIds) {
            data.append(data.length() == 0 ? "" : ",").append("{\"type\": \"workitems\", \"id\": \"sysarch/").append(id).append("\", \"attributes\": {}}");
        }
        this.polarion.stubFor(get(urlPathEqualTo(WI)).withQueryParam("query", containing("id:(")).willReturn(okJson("{\"data\": [" + data + "]}")));
    }

    private String resource(String workItemId, String title, Map<String, Object> fields, List<String> links, boolean wrap) {
        Map<String, Object> attributes = new LinkedHashMap<>(fields);
        attributes.put("title", title);
        attributes.put("updated", "2026-11-09T09:00:00Z");
        List<Map<String, String>> linkData = links.stream()
                .map(l -> Map.of("type", "linkedworkitems", "id", PROJECT + "/" + workItemId + "/" + l)).toList();
        Map<String, Object> resource = Map.of("type", "workitems", "id", PROJECT + "/" + workItemId, "attributes", attributes, "relationships",
                Map.of("linkedWorkItems", Map.of("data", linkData)));
        try {
            String text = this.json.writeValueAsString(resource);
            return wrap ? "{\"data\": " + text + "}" : text;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> audit(String elementId) {
        return this.jdbc.sql("SELECT direction || ' ' || field || ' ' || COALESCE(old_value, 'null') || ' -> ' || COALESCE(new_value, 'null') || ' by ' || actor "
                + "FROM sync_audit WHERE element_id = :id ORDER BY id").param("id", elementId).query(String.class).list();
    }

    /** A clock the tests move forward. */
    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.now;
        }
    }
}

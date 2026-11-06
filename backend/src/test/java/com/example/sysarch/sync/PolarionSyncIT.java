package com.example.sysarch.sync;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.ElementIds;
import com.example.sysarch.link.LinkRepository;
import com.example.sysarch.link.LinkRepository.StoredLink;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.PortDirection;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.sync.api.model.SyncPullPostRequest;
import com.example.sysarch.sync.api.model.SyncPushPostRequest;
import com.example.sysarch.sync.api.model.SyncResult;
import com.example.sysarch.sync.model.ElementSnapshots;
import com.example.sysarch.sync.model.ModelGateway;
import com.example.sysarch.sync.polarion.PolarionClient;
import com.example.sysarch.sync.polarion.TokenBucket;
import com.example.sysarch.validation.ConstraintValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
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
import org.springframework.web.server.ResponseStatusException;

import liquibase.integration.spring.SpringLiquibase;

/**
 * T7 acceptance (spec §8): push and pull against a WireMock Polarion, in the five scenarios the spec names (create,
 * rename, endpoint change, delete, rate limit) plus the rules of §6.3 (all or nothing, idempotency, constraint precheck,
 * conflicts, unbound pool).
 *
 * <p>The database is H2 in PostgreSQL mode, set up by the same Liquibase change sets as the application; the model is a
 * plain EMF resource behind {@link ModelGateway}, so no Sirius Web context is needed.
 */
class PolarionSyncIT {

    private static final String PROJECT = "sysarch";

    private static final String TOKEN = "Bearer user-pat-123";

    private static final String WI = "/polarion/rest/v1/projects/sysarch/workitems";

    private static final SysarchFactory FACTORY = SysarchFactory.eINSTANCE;

    private static ConstraintValidator constraints;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private final List<Duration> sleeps = new ArrayList<>();

    private WireMockServer polarion;

    private JdbcClient jdbc;

    private LinkRepository links;

    private InMemoryModel model;

    private SyncController controller;

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
        liquibase.setChangeLog("classpath:db/sysarch/001-polarion-sync.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();

        this.jdbc = JdbcClient.create(dataSource);
        this.links = new LinkRepository(this.jdbc);
        SyncStore store = new SyncStore(this.jdbc);
        this.polarion = new WireMockServer(wireMockConfig().dynamicPort());
        this.polarion.start();
        var client = new PolarionClient(this.polarion.baseUrl() + "/polarion", new TokenBucket(1000), 3, this.json, this.sleeps::add);
        this.model = new InMemoryModel();
        var service = new SyncService(this.model, this.links, store, client, new SyncProperties(), this.json,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        this.request = new MockHttpServletRequest();
        this.request.addHeader("Authorization", TOKEN);
        this.request.addHeader(SyncController.ACTOR_HEADER, "alice");
        this.controller = new SyncController(service, store, this.json, this.request);
    }

    @AfterEach
    void tearDown() {
        this.polarion.stop();
    }

    // =========================================================== the five scenarios (spec T7 step 6)

    /** Scenario 1, create: pushing an unbound element creates its Work Item and binds it (the one automatic merge). */
    @Test
    void scenario1PushCreatesTheWorkItemAndTheBinding() {
        PartDefinition controller = this.model.block("PD-1", "DomainController");
        this.stubCreate("SYS-1");

        ResponseEntity<SyncResult> response = this.push("key-1", "PD-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getPushed(), String.valueOf(response.getBody()));
        this.polarion.verify(postRequestedFor(urlPathEqualTo(WI)).withHeader("Authorization", equalTo(TOKEN)).withRequestBody(equalToJson("""
                {"data": [{"type": "workitems", "attributes": {
                  "type": "architecture_element", "title": "DomainController",
                  "sysarchElementId": "PD-1", "sysarchName": "DomainController", "sysarchLayer": "LOGICAL", "sysarchStereotypes": ""}}]}""")));
        StoredLink link = this.links.find("PD-1").orElseThrow();
        assertEquals("SYS-1", link.workItemId());
        assertEquals("ACTIVE", link.state());
        assertEquals("architecture_element", link.workItemType());
        assertTrue(this.auditRows("PD-1").contains("PUSH sysarchName null -> DomainController by alice"));
        assertEquals("PD-1", ElementIds.of(controller));
    }

    /**
     * Scenario 2, rename: renaming in the model updates the model-owned name field only, never the Polarion-owned
     * title; renaming the Work Item in Polarion updates the title shown on the binding, never the element name.
     */
    @Test
    void scenario2RenamesFlowOnlyInTheOwnersDirection() {
        PartDefinition controller = this.model.block("PD-1", "DomainController");
        this.stubCreate("SYS-1");
        this.push("key-1", "PD-1");
        controller.setName("MainController");
        this.stubWorkItem("SYS-1", "Domain controller (Polarion title)", Map.of("sysarchElementId", "PD-1", "sysarchName", "DomainController",
                "sysarchLayer", "LOGICAL", "sysarchStereotypes", ""));
        this.stubPatch("SYS-1");

        ResponseEntity<SyncResult> pushed = this.push("key-2", "PD-1");

        assertEquals(1, pushed.getBody().getPushed());
        this.polarion.verify(patchRequestedFor(urlPathEqualTo(WI + "/SYS-1"))
                .withRequestBody(equalToJson("{\"data\": {\"type\": \"workitems\", \"id\": \"sysarch/SYS-1\", \"attributes\": {\"sysarchName\": \"MainController\"}}}"))
                .withRequestBody(matchingJsonPath("$.data.attributes.title", absent())));
        assertTrue(this.auditRows("PD-1").contains("PUSH sysarchName DomainController -> MainController by alice"));

        this.stubWorkItem("SYS-1", "Main controller (renamed in Polarion)", Map.of("sysarchElementId", "PD-1", "sysarchName", "MainController",
                "sysarchLayer", "LOGICAL", "sysarchStereotypes", ""));
        ResponseEntity<SyncResult> pulled = this.pull(null);

        assertEquals(1, pulled.getBody().getPulled());
        assertEquals("Main controller (renamed in Polarion)", this.links.find("PD-1").orElseThrow().workItemTitle());
        assertEquals("MainController", controller.getName(), "the Polarion title never renames the element");
    }

    /**
     * Scenario 3, endpoint change: a Polarion user edits a Connection's model-owned source field. Pull records a
     * CONFLICT instead of merging, and push then refuses with 409 until a human resolves it (spec §6.2).
     */
    @Test
    void scenario3EndpointEditedInPolarionIsAConflict() {
        Connection bus = this.model.connection("CO-1", "PO-1", "PO-2");
        this.stubCreate("SYS-9");
        assertEquals(200, this.push("key-1", "CO-1").getStatusCode().value());
        Map<String, Object> edited = new LinkedHashMap<>(Map.of("sysarchElementId", "CO-1", "sysarchName", "bus", "sysarchLayer", "LOGICAL",
                "sysarchStereotypes", "", "sysarchSource", "PO-99", "sysarchTarget", "PO-2"));
        this.stubWorkItem("SYS-9", "bus", edited);

        SyncResult pulled = this.pull(null).getBody();

        assertEquals(List.of("sysarchSource"), pulled.getConflicts().get(0).getFields());
        assertEquals("CONFLICT", this.links.find("CO-1").orElseThrow().state());
        assertEquals("PO-1", ElementIds.of(bus.getSource()), "nothing is merged into the model");

        ResponseEntity<SyncResult> pushed = this.push("key-2", "CO-1");

        assertEquals(409, pushed.getStatusCode().value());
        assertEquals("CO-1", pushed.getBody().getConflicts().get(0).getElementId());
        this.polarion.verify(0, patchRequestedFor(anyUrl()));
    }

    /** Scenario 4, delete: a deleted Work Item makes the binding an ORPHAN on pull, and push refuses that element. */
    @Test
    void scenario4DeletedWorkItemOrphansTheBinding() {
        this.model.block("PD-1", "DomainController");
        this.stubCreate("SYS-1");
        this.push("key-1", "PD-1");
        this.polarion.stubFor(get(urlPathEqualTo(WI + "/SYS-1")).willReturn(aResponse().withStatus(404)));

        SyncResult pulled = this.pull(null).getBody();

        assertEquals("WORK_ITEM_DELETED", pulled.getErrors().get(0).getCode());
        assertEquals("ORPHAN", this.links.find("PD-1").orElseThrow().state());

        this.links.update(this.links.find("PD-1").orElseThrow().withState("ACTIVE")); // even if someone resets the state
        SyncResult pushed = this.push("key-2", "PD-1").getBody();

        assertEquals("WORK_ITEM_NOT_FOUND", pushed.getErrors().get(0).getCode());
        assertEquals(0, pushed.getPushed());
        this.polarion.verify(1, postRequestedFor(anyUrl()));
        this.polarion.verify(0, patchRequestedFor(anyUrl()));
    }

    /** Scenario 5, rate limit: a 429 is waited out per Retry-After; if it persists, the caller gets 429 and nothing is written. */
    @Test
    void scenario5RateLimitIsWaitedOutOrPassedOnWithoutWriting() {
        this.model.block("PD-1", "DomainController");
        this.polarion.stubFor(post(urlPathEqualTo(WI)).inScenario("limit").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "2")).willSetStateTo("free"));
        this.polarion.stubFor(post(urlPathEqualTo(WI)).inScenario("limit").whenScenarioStateIs("free")
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody(created("SYS-1"))));

        assertEquals(1, this.push("key-1", "PD-1").getBody().getPushed());
        assertEquals(List.of(Duration.ofSeconds(2)), this.sleeps);

        this.model.block("PD-2", "ECU");
        this.polarion.resetAll();
        this.polarion.stubFor(post(urlPathEqualTo(WI)).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "7")));
        ResponseEntity<SyncResult> limited = this.push("key-2", "PD-2");

        assertEquals(429, limited.getStatusCode().value());
        assertEquals("7", limited.getHeaders().getFirst("Retry-After"));
        assertTrue(this.links.find("PD-2").isEmpty(), "nothing is bound when the push did not get through");
    }

    // =========================================================== §6.3 rules

    /** All or nothing: when the second write fails, the first is undone in Polarion and nothing is recorded. */
    @Test
    void aFailedWriteUndoesTheEarlierWritesOfThePush() {
        this.model.block("PD-1", "DomainController");
        this.model.block("PD-2", "ECU");
        this.polarion.stubFor(post(urlPathEqualTo(WI)).inScenario("second fails").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody(created("SYS-1"))).willSetStateTo("failing"));
        this.polarion.stubFor(post(urlPathEqualTo(WI)).inScenario("second fails").whenScenarioStateIs("failing")
                .willReturn(aResponse().withStatus(500).withBody("boom")));
        this.polarion.stubFor(com.github.tomakehurst.wiremock.client.WireMock.delete(urlPathEqualTo(WI)).willReturn(aResponse().withStatus(204)));

        SyncResult result = this.push("key-1", "PD-1", "PD-2").getBody();

        assertEquals(0, result.getPushed());
        assertEquals(List.of("POLARION_ERROR", "ROLLED_BACK"), result.getErrors().stream().map(e -> e.getCode()).toList());
        this.polarion.verify(deleteRequestedFor(urlPathEqualTo(WI))
                .withRequestBody(equalToJson("{\"data\": [{\"type\": \"workitems\", \"id\": \"sysarch/SYS-1\"}]}")));
        assertTrue(this.links.find("PD-1").isEmpty() && this.links.find("PD-2").isEmpty(), "no binding recorded");
        assertEquals(List.of(), this.auditRows("PD-1"));
    }

    /** The precheck covers constraints: one invalid element and nothing at all is sent to Polarion. */
    @Test
    void anInvalidElementStopsTheWholePushBeforeAnyWrite() {
        this.model.block("PD-1", "DomainController");
        this.model.block("PD-2", ""); // NameNotEmpty

        SyncResult result = this.push("key-1", "PD-1", "PD-2", "PD-404").getBody();

        assertEquals(0, result.getPushed());
        assertEquals(List.of("CONSTRAINT_VIOLATION", "ELEMENT_NOT_FOUND"), result.getErrors().stream().map(e -> e.getCode()).toList());
        assertTrue(result.getErrors().get(0).getMessage().startsWith("NameNotEmpty"));
        this.polarion.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void dryRunWritesNothing() {
        this.model.block("PD-1", "DomainController");

        SyncResult result = this.controller.syncPushPost("key-1", new SyncPushPostRequest().projectId(PROJECT).elementIds(List.of("PD-1")).dryRun(true))
                .getBody();

        assertEquals(1, result.getPushed());
        this.polarion.verify(0, anyRequestedFor(anyUrl()));
        assertTrue(this.links.find("PD-1").isEmpty());
    }

    /** Replaying an Idempotency-Key returns the first result and does not write again. */
    @Test
    void replayingAnIdempotencyKeyReturnsTheFirstResult() {
        this.model.block("PD-1", "DomainController");
        this.stubCreate("SYS-1");

        ResponseEntity<SyncResult> first = this.push("same-key", "PD-1");
        ResponseEntity<SyncResult> replay = this.push("same-key", "PD-1");

        assertEquals(first.getBody(), replay.getBody());
        assertEquals("true", replay.getHeaders().getFirst("Idempotent-Replayed"));
        this.polarion.verify(1, postRequestedFor(urlPathEqualTo(WI)));
    }

    @Test
    void anUnchangedElementIsSkipped() {
        this.model.block("PD-1", "DomainController");
        this.stubCreate("SYS-1");
        this.push("key-1", "PD-1");
        this.stubWorkItem("SYS-1", "t", Map.of("sysarchElementId", "PD-1", "sysarchName", "DomainController", "sysarchLayer", "LOGICAL", "sysarchStereotypes", ""));

        SyncResult result = this.push("key-2", "PD-1").getBody();

        assertEquals(0, result.getPushed());
        assertEquals(1, result.getSkipped());
        this.polarion.verify(0, patchRequestedFor(anyUrl()));
    }

    /** A Requirement's text is Polarion-owned: pull writes it into the model, through the model gateway. */
    @Test
    void pullWritesRequirementTextIntoTheModel() {
        Requirement requirement = this.model.requirement("RQ-1", "Speed broadcast", "old text");
        this.stubCreate("REQ-1");
        this.push("key-1", "RQ-1");
        Map<String, Object> attributes = new LinkedHashMap<>(Map.of("sysarchElementId", "RQ-1", "sysarchName", "Speed broadcast", "sysarchLayer", "LOGICAL",
                "sysarchStereotypes", ""));
        attributes.put("description", Map.of("type", "text/plain", "value", "The controller shall broadcast vehicle speed every 10 ms."));
        this.stubWorkItem("REQ-1", "Speed broadcast", attributes);

        SyncResult result = this.pull(null).getBody();

        assertEquals(1, result.getPulled());
        assertEquals("The controller shall broadcast vehicle speed every 10 ms.", requirement.getText());
        assertTrue(this.auditRows("RQ-1").contains("PULL text old text -> The controller shall broadcast vehicle speed every 10 ms. by alice"));
    }

    /** New Work Items in Polarion are not merged into the model: they wait in the unbound pool to be claimed. */
    @Test
    void newWorkItemsInPolarionGoToTheUnboundPool() {
        this.polarion.stubFor(get(urlPathEqualTo(WI)).willReturn(okJson("""
                {"data": [{"type": "workitems", "id": "sysarch/SYS-50", "attributes": {"type": "architecture_element", "title": "Gateway"}}]}""")));

        this.pull(OffsetDateTime.parse("2026-11-01T00:00:00Z"));

        assertEquals(List.of("SYS-50 architecture_element Gateway"), this.jdbc.sql("SELECT work_item_id || ' ' || work_item_type || ' ' || title FROM polarion_unbound_pool")
                .query(String.class).list());
        this.polarion.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathEqualTo(WI))
                .withQueryParam("query", equalTo("type:(architecture_element architecture_connection requirement) AND created:[20261101 TO *]")));
        assertEquals(0, this.model.applied.size(), "nothing was written into the model");
    }

    @Test
    void withoutAPolarionTokenTheCallIsRefused() {
        this.request.removeHeader("Authorization");

        var error = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class, () -> this.push("key-1", "PD-1"));
        assertEquals(401, error.getStatusCode().value());
    }

    @Test
    void anUnmappedProjectIsReportedNotCrashed() {
        SyncResult result = this.controller.syncPushPost("key-1", new SyncPushPostRequest().projectId("other").elementIds(List.of("PD-1"))).getBody();

        assertEquals("PROJECT_NOT_MAPPED", result.getErrors().get(0).getCode());
        assertNull(result.getErrors().get(0).getElementId());
    }

    // =========================================================== helpers

    private ResponseEntity<SyncResult> push(String key, String... elementIds) {
        return this.controller.syncPushPost(key, new SyncPushPostRequest().projectId(PROJECT).elementIds(List.of(elementIds)));
    }

    private ResponseEntity<SyncResult> pull(OffsetDateTime since) {
        return this.controller.syncPullPost(new SyncPullPostRequest().projectId(PROJECT).since(since));
    }

    private void stubCreate(String workItemId) {
        this.polarion.stubFor(post(urlPathEqualTo(WI))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody(created(workItemId))));
    }

    private void stubPatch(String workItemId) {
        this.polarion.stubFor(patch(urlPathEqualTo(WI + "/" + workItemId)).willReturn(aResponse().withStatus(204)));
    }

    private void stubWorkItem(String workItemId, String title, Map<String, Object> customFields) {
        Map<String, Object> attributes = new LinkedHashMap<>(customFields);
        attributes.put("title", title);
        attributes.put("updated", "2026-11-05T10:00:00Z");
        try {
            String body = this.json.writeValueAsString(Map.of("data", Map.of("type", "workitems", "id", PROJECT + "/" + workItemId, "attributes", attributes)));
            this.polarion.stubFor(get(urlPathEqualTo(WI + "/" + workItemId)).willReturn(okJson(body)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String created(String workItemId) {
        return "{\"data\": [{\"type\": \"workitems\", \"id\": \"sysarch/" + workItemId + "\"}]}";
    }

    private List<String> auditRows(String elementId) {
        return this.jdbc.sql("SELECT direction || ' ' || field || ' ' || COALESCE(old_value, 'null') || ' -> ' || COALESCE(new_value, 'null') || ' by ' || actor "
                + "FROM sync_audit WHERE element_id = :id ORDER BY id").param("id", elementId).query(String.class).list();
    }

    /** The model of project {@value #PROJECT}, as a plain EMF resource. */
    private static final class InMemoryModel implements ModelGateway {

        final Resource resource = new ResourceImpl(URI.createURI("memory:/model.xmi"));

        final List<Map<String, Map<String, String>>> applied = new ArrayList<>();

        @Override
        public Map<String, ElementSnapshot> read(String polarionProjectId, Collection<String> elementIds) {
            if (!PROJECT.equals(polarionProjectId)) {
                throw new UnmappedProjectException(polarionProjectId);
            }
            Map<String, ElementSnapshot> result = new LinkedHashMap<>();
            this.resource.getAllContents().forEachRemaining(object -> {
                if (object instanceof ArchElement element && elementIds.contains(ElementIds.of(element))) {
                    result.put(ElementIds.of(element), ElementSnapshots.of(element, constraints));
                }
            });
            return result;
        }

        @Override
        public void applyPolarionOwned(String polarionProjectId, Map<String, Map<String, String>> changes) {
            if (changes.isEmpty()) {
                return;
            }
            this.applied.add(changes);
            this.resource.getAllContents().forEachRemaining(object -> {
                if (object instanceof Requirement requirement && changes.containsKey(ElementIds.of(requirement))) {
                    requirement.setText(changes.get(ElementIds.of(requirement)).get(ElementSnapshot.TEXT));
                }
            });
        }

        PartDefinition block(String id, String name) {
            PartDefinition block = FACTORY.createPartDefinition();
            block.setId(id);
            block.setName(name);
            this.resource.getContents().add(block);
            return block;
        }

        Requirement requirement(String id, String name, String text) {
            Requirement requirement = FACTORY.createRequirement();
            requirement.setId(id);
            requirement.setName(name);
            requirement.setText(text);
            requirement.setLayer(Layer.LOGICAL);
            this.resource.getContents().add(requirement);
            return requirement;
        }

        Connection connection(String id, String sourceId, String targetId) {
            PartDefinition owner = this.block("PD-" + id, "owner of " + id);
            Port source = port(sourceId, PortDirection.OUT);
            Port target = port(targetId, PortDirection.IN);
            owner.getOwnedPorts().addAll(List.of(source, target));
            Connection connection = FACTORY.createConnection();
            connection.setId(id);
            connection.setName("bus");
            connection.setSource(source);
            connection.setTarget(target);
            this.resource.getContents().add(connection);
            return connection;
        }

        private static Port port(String id, PortDirection direction) {
            Port port = FACTORY.createPort();
            port.setId(id);
            port.setName(id);
            port.setInterfaceType("CAN");
            port.setDirection(direction);
            return port;
        }
    }
}

package com.example.sysarch.storage.polarion;

import static org.junit.jupiter.api.Assertions.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import com.example.sysarch.storage.ModelCodec;
import com.example.sysarch.storage.polarion.PolarionSnapshotManifest.*;
import com.example.sysarch.storage.polarion.PolarionSnapshotLoader.*;
import com.example.sysarch.sync.polarion.PolarionToken;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PolarionRestReadSessionTest {
    private static final PolarionToken TOKEN = new PolarionToken("m2-fixture-not-a-real-token");
    private static final String PATH = "/polarion/rest/v1/projects/alpha/workitems/MODEL-1";
    private final Item root = new Item("alpha", "MODEL-1", null);
    private final UUID model = UUID.randomUUID(), commit = UUID.randomUUID(), element = UUID.randomUUID();
    private WireMockServer server;
    private PolarionRestReadSession session;

    @BeforeEach
    void start() {
        this.server = new WireMockServer(wireMockConfig().dynamicPort()); this.server.start();
        this.session = PolarionRestReadSession.open(URI.create(this.server.baseUrl() + "/polarion"), TOKEN, (uri, token) -> {
            assertSame(TOKEN, token); return "alice";
        });
    }

    @AfterEach
    void stop() { this.session.close(); this.server.stop(); }

    @Test
    void verifiesTheCredentialBeforeOpeningAndRefusesAnonymousOrUnsafeTargets() {
        var calls = new AtomicInteger();
        try (var reader = PolarionRestReadSession.open(URI.create(this.server.baseUrl() + "/polarion"), TOKEN, (uri, token) -> {
            calls.incrementAndGet(); return "alice";
        })) { assertEquals("alice", reader.verifiedSubject()); }
        assertEquals(1, calls.get());
        assertThrows(ReadFailure.class, () -> PolarionRestReadSession.open(URI.create(this.server.baseUrl() + "/polarion"), TOKEN, (uri, token) -> ""));
        assertThrows(ReadFailure.class, () -> PolarionRestReadSession.open(URI.create("http://remote.example/polarion"), TOKEN, (uri, token) -> fail("must not verify insecure remote target")));
        this.server.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void productionIdentityUsesOnlyTheDedicatedSdkBridgeAndRejectsM0Shape() {
        String path="/polarion/sysarch-model/identity";
        this.server.stubFor(get(urlPathEqualTo(path)).willReturn(okJson("{\"protocol\":\"sysarch-model-read/1\",\"authentication\":\"sdk-access-token\",\"actor\":\"alice\"}")));
        try(var verified=PolarionRestReadSession.open(URI.create(this.server.baseUrl()+"/polarion"),TOKEN)) {
            assertEquals("alice",verified.verifiedSubject());
        }
        this.server.verify(getRequestedFor(urlPathEqualTo(path)).withHeader("Authorization",equalTo("Bearer "+TOKEN.value())));
        this.server.stubFor(get(urlPathEqualTo(path)).willReturn(okJson("{\"authentication\":\"sdk-access-token\",\"actor\":\"alice\"}")));
        assertEquals(Failure.SCHEMA,assertThrows(ReadFailure.class,()->PolarionRestReadSession.open(URI.create(this.server.baseUrl()+"/polarion"),TOKEN)).failure());
        this.server.verify(0,getRequestedFor(urlPathMatching("/polarion/sysarch-m0/.*")));
    }

    @Test
    void productionIdentityHasNoCookieOrAnonymousFallback() {
        this.server.stubFor(get(urlPathEqualTo("/polarion/sysarch-model/identity")).willReturn(aResponse().withStatus(401)));
        assertEquals(Failure.DENIED,assertThrows(ReadFailure.class,()->PolarionRestReadSession.open(URI.create(this.server.baseUrl()+"/polarion"),TOKEN)).failure());
        this.server.verify(getRequestedFor(urlPathEqualTo("/polarion/sysarch-model/identity")).withoutHeader("Cookie"));
    }
    @Test
    void pinsTheActualSdkRevisionAfterCurrentRestAuthorization() {
        this.server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(resource("{}"))));
        this.server.stubFor(get(urlPathEqualTo("/polarion/sysarch-model/revision")).willReturn(okJson("{\"protocol\":\"sysarch-model-read/1\",\"actor\":\"alice\",\"project\":\"alpha\",\"workItem\":\"MODEL-1\",\"revision\":\"12345\"}")));
        assertEquals("12345",this.session.pinCurrentRevision(this.root).revision());
        this.server.verify(getRequestedFor(urlPathEqualTo("/polarion/sysarch-model/revision")).withQueryParam("project",equalTo("alpha"))
            .withQueryParam("workItem",equalTo("MODEL-1")).withHeader("Authorization",equalTo("Bearer "+TOKEN.value())));
    }
    @Test
    void revisionPinningRejectsWrongSubjectAndInventedTimestampRevision() {
        this.server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(resource("{}"))));
        for(String body:new String[]{"{\"protocol\":\"sysarch-model-read/1\",\"actor\":\"bob\",\"project\":\"alpha\",\"workItem\":\"MODEL-1\",\"revision\":\"42\"}",
            "{\"protocol\":\"sysarch-model-read/1\",\"actor\":\"alice\",\"project\":\"alpha\",\"workItem\":\"MODEL-1\",\"revision\":\"2026-11-18T00:00:00Z\"}"}) {
            this.server.stubFor(get(urlPathEqualTo("/polarion/sysarch-model/revision")).willReturn(okJson(body)));
            assertThrows(ReadFailure.class,()->this.session.pinCurrentRevision(this.root));
        }
    }

    @Test
    void historicalPinUsesTheObjectRevisionAndRejectsAnUnboundOrFutureReply() {
        String bridge="/polarion/sysarch-model/revision";
        String prefix="{\"protocol\":\"sysarch-model-read/1\",\"actor\":\"alice\",\"project\":\"alpha\",\"workItem\":\"MODEL-1\",";
        this.server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(resource("{}"))));
        this.server.stubFor(get(urlPathEqualTo(bridge)).willReturn(okJson(prefix+"\"revision\":\"100\",\"atRevision\":\"110\"}")));
        assertEquals("100",this.session.pinRevisionAt(this.root,"110").revision());
        this.server.verify(getRequestedFor(urlPathEqualTo(bridge)).withQueryParam("atRevision",equalTo("110")));
        for (String response : new String[]{"\"revision\":\"100\"}", "\"revision\":\"100\",\"atRevision\":\"101\"}",
                "\"revision\":\"111\",\"atRevision\":\"110\"}"}) {
            this.server.stubFor(get(urlPathEqualTo(bridge)).willReturn(okJson(prefix+response)));
            assertThrows(ReadFailure.class,()->this.session.pinRevisionAt(this.root,"110"));
        }
    }

    @Test
    void historicalPinChecksCurrentAccessBeforeCallingTheBridge() {
        this.server.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(403)));
        assertEquals(Failure.DENIED,assertThrows(ReadFailure.class,()->this.session.pinRevisionAt(this.root,"110")).failure());
        this.server.verify(0,getRequestedFor(urlPathEqualTo("/polarion/sysarch-model/revision")));
    }
    @Test
    void inaccessibleCurrentItemCannotBePinnedThroughTheSdkEndpoint() {
        this.server.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(403)));
        assertEquals(Failure.DENIED,assertThrows(ReadFailure.class,()->this.session.pinCurrentRevision(this.root)).failure());
        this.server.verify(0,getRequestedFor(urlPathEqualTo("/polarion/sysarch-model/revision")));
    }

    @Test
    void currentAccessUsesGetAndOnlyMinimalFieldsWithTheUsersToken() {
        this.server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(resource("{}"))));
        this.session.requireCurrentRead(this.root);
        this.server.verify(getRequestedFor(urlPathEqualTo(PATH)).withHeader("Authorization", equalTo("Bearer " + TOKEN.value()))
                .withQueryParam("fields[workitems]", equalTo("id")));
        assertFalse(this.session.toString().contains(TOKEN.value()));
    }

    @Test
    void readsTypedHeadWithoutInventingARepositoryRevisionFromUpdatedTime() {
        this.server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(resource("""
                {"type":"sysarchModel","sysarchManaged":true,"sysarchSchemaVersion":"1.0",
                 "sysarchModelUuid":"%s","sysarchHeadVersion":7,"sysarchHeadCommitId":"%s",
                 "sysarchHeadManifestAttachment":"1-manifest.json","sysarchHeadManifestHash":"%s","sysarchHeadManifestBytes":12}
                """.formatted(this.model, this.commit, "a".repeat(64))))));
        Head head = this.session.head(this.root);
        assertEquals(this.model, head.modelUuid()); assertEquals(7, head.version()); assertNull(head.manifest().owner().revision());
        this.server.verify(getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("fields[workitems]", notContaining("@all")));
    }

    @Test
    void missingManagedFieldsDoNotBecomeAnEmptyModel() {
        this.server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(resource("{\"type\":\"task\"}"))));
        assertEquals(Failure.SCHEMA, assertThrows(ReadFailure.class, () -> this.session.head(this.root)).failure());
    }

    @Test
    void pinnedElementIdentityUsesExactRequestedRevision() {
        this.server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(resource("""
                {"type":"sysarchElement","sysarchManaged":true,"sysarchModelUuid":"%s",
                 "sysarchElementUuid":"%s","sysarchElementKind":"PartDefinition"}
                """.formatted(this.model, this.element)))));
        var identity = this.session.element(new Item("alpha", "MODEL-1", "12345"));
        assertEquals(this.element, identity.elementUuid());
        this.server.verify(getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("revision", equalTo("12345")));
    }

    @Test
    void historicalRequirementUsesPinnedRevisionAndExistingRichTextConversion() {
        this.server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(resource("""
                {"type":"requirement","title":"Requirement title","description":{"type":"text/html","value":"A&lt;B<br/>Line 2"}}
                """))));
        var requirement = this.session.requirement(new Item("alpha", "MODEL-1", "456"));
        assertEquals("A<B\nLine 2", requirement.text()); assertEquals("MODEL-1", requirement.reqId());
        this.server.verify(getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("revision", equalTo("456")));
        assertThrows(ReadFailure.class, () -> this.session.requirement(this.root));
    }

    @Test
    void binaryAttachmentsAreNotReencodedAndKeepRevision() {
        byte[] bytes = new byte[]{0, -1, 5, 127};
        String path = PATH + "/attachments/1-snapshot.bin/content";
        this.server.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withBody(bytes)));
        var ref = new Attachment(new Item("alpha", "MODEL-1", "77"), "1-snapshot.bin", ModelCodec.sha256(bytes), bytes.length);
        assertArrayEquals(bytes, this.session.attachment(ref, 10));
        this.server.verify(getRequestedFor(urlPathEqualTo(path)).withQueryParam("revision", equalTo("77")));
    }

    @Test
    void sizeLimitIsEnforcedBeforeReturningAttachmentData() {
        byte[] bytes = "more-than-allowed".getBytes(StandardCharsets.UTF_8);
        this.server.stubFor(get(urlPathEqualTo(PATH + "/attachments/payload/content")).willReturn(aResponse().withBody(bytes)));
        var ref = new Attachment(this.root, "payload", "a".repeat(64), 1);
        assertEquals(Failure.INTEGRITY, assertThrows(ReadFailure.class, () -> this.session.attachment(ref, 2)).failure());
    }

    @Test
    void redirectNeverForwardsCredentialsToAnotherEndpoint() {
        this.server.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(302).withHeader("Location", this.server.baseUrl() + "/trap")));
        assertEquals(Failure.WRONG_SERVER, assertThrows(ReadFailure.class, () -> this.session.requireCurrentRead(this.root)).failure());
        this.server.verify(0, getRequestedFor(urlPathEqualTo("/trap")));
    }

    @Test
    void deniedOrMissingObjectsDoNotLeakResponseContentAndUnavailableIsDistinct() {
        for (int status : new int[]{401, 403, 404}) {
            this.server.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(status).withBody("private response body")));
            var failure = assertThrows(ReadFailure.class, () -> this.session.requireCurrentRead(this.root));
            assertEquals(Failure.DENIED, failure.failure()); assertFalse(failure.getMessage().contains("private"));
        }
        this.server.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(503)));
        assertEquals(Failure.UNAVAILABLE, assertThrows(ReadFailure.class, () -> this.session.requireCurrentRead(this.root)).failure());
    }

    @Test
    void htmlMalformedAndWrongResourceIdsAreRejected() {
        this.server.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withHeader("Content-Type", "text/html").withBody("<html>login</html>")));
        assertEquals(Failure.SCHEMA, assertThrows(ReadFailure.class, () -> this.session.requireCurrentRead(this.root)).failure());
        this.server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("{\"data\":{\"type\":\"workitems\",\"id\":\"beta/SECRET-1\"}}")));
        assertEquals(Failure.IDENTITY, assertThrows(ReadFailure.class, () -> this.session.requireCurrentRead(this.root)).failure());
        this.server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(resource("{}") + " {}")));
        assertEquals(Failure.SCHEMA, assertThrows(ReadFailure.class, () -> this.session.requireCurrentRead(this.root)).failure());
    }

    @Test
    void closedSessionsCannotBeReused() {
        this.session.close(); assertNull(this.session.verifiedSubject());
        assertEquals(Failure.DENIED, assertThrows(ReadFailure.class, () -> this.session.requireCurrentRead(this.root)).failure());
        this.server.verify(0, anyRequestedFor(anyUrl()));
    }

    private static String resource(String attributes) {
        // JSON:API identity is independent of sparse fields.
        return "{\"data\":{\"type\":\"workitems\",\"id\":\"alpha/MODEL-1\",\"attributes\":" + attributes + "}}";
    }

    private String modelEntry(String id, UUID uuid) {
        return "{\"type\":\"workitems\",\"id\":\""+id+"\",\"attributes\":{\"type\":\"sysarchModel\",\"title\":\"Model\",\"sysarchManaged\":true,\"sysarchSchemaVersion\":\"1.0\",\"sysarchModelUuid\":\""+uuid+"\"}}";
    }

    @Test
    void discoveryUsesBoundedFixedQueriesAndDoesNotFollowRemoteNextLinks() {
        String path="/polarion/rest/v1/projects/alpha/workitems";
        this.server.stubFor(get(urlPathEqualTo(path)).willReturn(okJson("{\"data\":["+modelEntry("alpha/MODEL-1",this.model)+"],\"links\":{\"next\":\"https://elsewhere.invalid/private\"}}")));
        var page=this.session.models("alpha",1,2);
        assertEquals(1,page.models().size()); assertTrue(page.mayHaveMore());
        assertEquals(this.model,page.models().getFirst().location().modelUuid());
        this.server.verify(1,getRequestedFor(urlPathEqualTo(path)).withQueryParam("query",equalTo("type:sysarchModel AND sysarchManaged:true"))
            .withQueryParam("page[number]",equalTo("1")).withQueryParam("page[size]",equalTo("2"))
            .withHeader("Authorization",equalTo("Bearer "+TOKEN.value())));
        assertEquals(1,this.server.getAllServeEvents().size());
    }

    @Test
    void discoveryEmptyFinalPageIsExplicitAndBoundsRejectBeforeNetwork() {
        this.server.stubFor(get(urlPathEqualTo("/polarion/rest/v1/projects/alpha/workitems")).willReturn(okJson("{\"data\":[]}")));
        assertFalse(this.session.models("alpha",2,1).mayHaveMore());
        assertThrows(IllegalArgumentException.class,()->this.session.models("../alpha",1,1));
        assertThrows(IllegalArgumentException.class,()->this.session.models("alpha",0,1));
        assertThrows(IllegalArgumentException.class,()->this.session.models("alpha",1,101));
        assertThrows(IllegalArgumentException.class,()->this.session.models("alpha",10001,1));
        assertEquals(1,this.server.getAllServeEvents().size());
    }

    @Test
    void discoveryRejectsForeignAndDuplicateModelIdentities() {
        String path="/polarion/rest/v1/projects/alpha/workitems";
        for(String data:new String[]{modelEntry("beta/SECRET",this.model),modelEntry("alpha/MODEL-1",this.model)+","+modelEntry("alpha/MODEL-2",this.model)}) {
            this.server.stubFor(get(urlPathEqualTo(path)).willReturn(okJson("{\"data\":["+data+"]}")));
            assertEquals(Failure.IDENTITY,assertThrows(ReadFailure.class,()->this.session.models("alpha",1,2)).failure());
        }
    }

    @Test
    void discoveryRejectsInvalidSchemaPartialErrorsAndOversizedPages() {
        String path="/polarion/rest/v1/projects/alpha/workitems";
        String valid=modelEntry("alpha/MODEL-1",this.model);
        for(String response:new String[]{"{\"data\":{}}","{\"data\":["+valid.replace("sysarchManaged\":true","sysarchManaged\":false")+"]}",
            "{\"data\":["+valid+"],\"errors\":[]}","{\"data\":["+valid+","+modelEntry("alpha/MODEL-2",UUID.randomUUID())+"]}",
            "{\"data\":[],\"links\":{\"next\":\"untrusted\"}}"}) {
            this.server.stubFor(get(urlPathEqualTo(path)).willReturn(okJson(response)));
            assertEquals(Failure.SCHEMA,assertThrows(ReadFailure.class,()->this.session.models("alpha",1,1)).failure());
        }
    }

    @Test
    void responseBodyDeadlineStillAppliesAfterHeadersArrive() {
        this.server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(resource("{}")).withChunkedDribbleDelay(16, 16000)));
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(22), () ->
                assertEquals(Failure.UNAVAILABLE, assertThrows(ReadFailure.class, () -> this.session.requireCurrentRead(this.root)).failure()));
    }
}

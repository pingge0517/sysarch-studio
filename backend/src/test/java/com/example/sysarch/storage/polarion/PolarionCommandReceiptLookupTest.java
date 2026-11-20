package com.example.sysarch.storage.polarion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import com.example.sysarch.storage.command.*;
import com.example.sysarch.storage.polarion.PolarionSnapshotManifest.*;
import com.example.sysarch.storage.polarion.PolarionSnapshotLoader.*;
import com.example.sysarch.sync.polarion.PolarionToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PolarionCommandReceiptLookupTest {
    private final UUID model=UUID.randomUUID(),commandId=UUID.randomUUID(),commit=UUID.randomUUID(),element=UUID.randomUUID();
    private final ObjectMapper json=new ObjectMapper();
    private final Item root=new Item("alpha","MODEL-1",null),record=new Item("alpha","COMMIT-1",null);
    private final String items="/polarion/rest/v1/projects/alpha/workitems";
    private final String attachments=items+"/COMMIT-1/attachments";
    private final String receiptContent=attachments+"/1-"+PolarionRestReadSession.COMMAND_RECEIPT_ATTACHMENT+"/content";
    private final Head head=new Head(model,4,commit,new Attachment(root,"manifest.json","b".repeat(64),100));
    private final PolarionSnapshotLoader loader=mock(PolarionSnapshotLoader.class);
    private WireMockServer server;private PolarionRestReadSession session;private Location location;
    private ModelCommandProtocol.BoundCommand command;private CommittedCommandVerifier.Receipt receipt;
    private String body;
    @BeforeEach void setup() throws Exception {
        server=new WireMockServer(wireMockConfig().dynamicPort());server.start();URI uri=URI.create(server.baseUrl()+"/polarion");
        session=PolarionRestReadSession.open(uri,new PolarionToken("receipt-fixture-token"),(u,t)->"alice");location=new Location(uri,root,model);
        body=json.writeValueAsString(Map.of("commandId",commandId,"expectedVersion",3,"schemaVersion","1.0","operations",List.of(Map.of("kind","RenameElement","elementUuid",element,"name","Next"))));
        command=new ModelCommandProtocol().bind(model,"alice",body.getBytes(StandardCharsets.UTF_8));
        receipt=new CommittedCommandVerifier.Receipt("1.0",model,commandId,"alice",command.requestHash(),3,4,commit,"b".repeat(64));
        when(loader.load(location,session)).thenReturn(new Loaded(head,null,null,"alice"));
        stubList(List.of(resource(record,attrs())),null);
        server.stubFor(get(urlPathEqualTo(items+"/COMMIT-1")).willReturn(okJson(json.writeValueAsString(Map.of("data",resource(record,attrs()))))));
        stubAttachments(List.of(attachment("alpha/COMMIT-1/1-"+PolarionRestReadSession.COMMAND_RECEIPT_ATTACHMENT)));
        server.stubFor(get(urlPathEqualTo(receiptContent)).willReturn(aResponse().withStatus(200).withBody(json.writeValueAsBytes(receipt))));
        server.stubFor(get(urlPathEqualTo("/polarion/sysarch-model/revision")).willReturn(okJson(revision("321"))));
        server.stubFor(get(urlPathEqualTo(items+"/MODEL-1")).willReturn(okJson(json.writeValueAsString(Map.of("data",resource(root,headAttrs(4)))))));
    }
    @AfterEach void close() {session.close();server.stop();}
    private Map<String,Object> resource(Item item,Map<String,Object> attrs) {return Map.of("type","workitems","id",item.project()+"/"+item.workItem(),"attributes",attrs);}
    private Map<String,Object> attrs() throws Exception {
        return Map.of("type","sysarchCommit","sysarchManaged",true,"sysarchSchemaVersion","1.0","sysarchModelUuid",model,"sysarchCommandId",commandId,
            "sysarchCommitId",commit,"sysarchRequestHash",receipt.requestHash(),"sysarchManifestHash",receipt.manifestHash(),"description",Map.of("type","text/plain","value",json.writeValueAsString(receipt)));
    }
    private Map<String,Object> headAttrs(long version) {
        return Map.of("type","sysarchModel","sysarchManaged",true,"sysarchSchemaVersion","1.0","sysarchModelUuid",model,"sysarchHeadVersion",version,"sysarchHeadCommitId",commit,
            "sysarchHeadManifestAttachment","manifest.json","sysarchHeadManifestHash","b".repeat(64),"sysarchHeadManifestBytes",100);
    }
    private String revision(String value) throws Exception {return json.writeValueAsString(Map.of("protocol","sysarch-model-read/1","actor","alice","project","alpha","workItem","COMMIT-1","revision",value));}
    private void stubList(List<?> rows,String next) throws Exception {
        server.stubFor(get(urlPathEqualTo(items)).willReturn(okJson(json.writeValueAsString(Map.of("data",rows,"links",next==null?Map.of():Map.of("next",next))))));
    }
    private java.util.Optional<PolarionCommandReceiptLookup.Observation> observe() {return new PolarionCommandReceiptLookup(loader).observe(location,command,session);}
    private Map<String,Object> attachment(String identity) {
        return Map.of("type","workitem_attachments","id",identity,"attributes",Map.of("fileName",PolarionRestReadSession.COMMAND_RECEIPT_ATTACHMENT));
    }
    private void stubAttachments(List<?> rows) throws Exception {
        server.stubFor(get(urlPathEqualTo(attachments)).willReturn(okJson(json.writeValueAsString(Map.of("data",rows)))));
    }
    @Test void readsExactScopePinnedRevisionButNeverCertifiesACommit() {
        var observation=observe().orElseThrow();assertEquals(receipt,observation.receipt());assertEquals("321",observation.commitRecord().revision());
        assertEquals(PolarionCommandReceiptLookup.Assurance.UNATTESTED,observation.assurance());assertEquals(DurableCommandGate.Outcome.UNKNOWN,observation.gateOutcome());
        server.verify(getRequestedFor(urlPathEqualTo(items)).withQueryParam("query",equalTo("type:sysarchCommit AND sysarchManaged:true AND sysarchModelUuid:\""+model+"\" AND sysarchCommandId:\""+commandId+"\""))
            .withQueryParam("page[size]",equalTo("2")).withoutHeader("Cookie"));
        server.verify(getRequestedFor(urlPathEqualTo(items+"/COMMIT-1")).withQueryParam("revision",equalTo("321")));
        server.verify(getRequestedFor(urlPathEqualTo(attachments)).withQueryParam("revision",equalTo("321")));
        server.verify(getRequestedFor(urlPathEqualTo(receiptContent)).withQueryParam("revision",equalTo("321")).withHeader("Accept",equalTo("application/octet-stream")));
        assertTrue(server.getAllServeEvents().stream().allMatch(event->event.getRequest().getMethod().getName().equals("GET")));
    }
    @Test void wholeModelAuthorizationRunsBeforeReceiptDiscovery() {
        when(loader.load(location,session)).thenThrow(new ReadFailure(Failure.DENIED));
        assertEquals(Failure.DENIED,assertThrows(ReadFailure.class,this::observe).failure());server.verify(0,anyRequestedFor(anyUrl()));
    }
    @Test void wrongCallerCannotReadAnyReceipt() {
        command=new ModelCommandProtocol().bind(model,"bob",body.getBytes(StandardCharsets.UTF_8));
        assertEquals(Failure.DENIED,assertThrows(ReadFailure.class,this::observe).failure());verifyNoInteractions(loader);server.verify(0,anyRequestedFor(anyUrl()));
    }
    @Test void missingSearchResultDoesNotProduceAReceipt() throws Exception {
        stubList(List.of(),null);assertTrue(observe().isEmpty());server.verify(0,getRequestedFor(urlPathEqualTo("/polarion/sysarch-model/revision")));
    }
    @Test void duplicateAndPaginatedResultsAreRejectedWithoutFollowingNextUrl() throws Exception {
        stubList(List.of(resource(record,attrs()),resource(new Item("alpha","COMMIT-2",null),attrs())),null);
        assertEquals(Failure.INCOMPLETE,assertThrows(ReadFailure.class,this::observe).failure());
        stubList(List.of(resource(record,attrs())),"https://untrusted.invalid/private");
        assertEquals(Failure.INCOMPLETE,assertThrows(ReadFailure.class,this::observe).failure());
        server.verify(0,getRequestedFor(urlPathEqualTo("/polarion/sysarch-model/revision")));
    }
    @Test void fieldPayloadMismatchAndHtmlAreRejected() throws Exception {
        var fields=new java.util.HashMap<>(attrs());fields.put("sysarchRequestHash","c".repeat(64));
        server.stubFor(get(urlPathEqualTo(items+"/COMMIT-1")).willReturn(okJson(json.writeValueAsString(Map.of("data",resource(record,fields))))));
        assertEquals(Failure.IDENTITY,assertThrows(ReadFailure.class,this::observe).failure());
        server.stubFor(get(urlPathEqualTo(receiptContent)).willReturn(aResponse().withStatus(200).withBody("<p>not JSON metadata</p>")));
        assertEquals(Failure.SCHEMA,assertThrows(ReadFailure.class,this::observe).failure());
    }
    @Test void rejectsAmbiguousOrForeignAttachmentIdentityWithoutDownloading() throws Exception {
        stubAttachments(List.of(attachment("alpha/COMMIT-1/1-first"),attachment("alpha/COMMIT-1/2-second")));
        assertEquals(Failure.INCOMPLETE,assertThrows(ReadFailure.class,this::observe).failure());
        stubAttachments(List.of(attachment("alpha/COMMIT-2/1-first")));
        assertEquals(Failure.IDENTITY,assertThrows(ReadFailure.class,this::observe).failure());
        server.verify(0,getRequestedFor(urlPathMatching(".*/content")));
    }
    @Test void anotherRequestUnderTheSameCommandIdIsAConflict() {
        command=new ModelCommandProtocol().bind(model,"alice",body.replace("Next","Different").getBytes(StandardCharsets.UTF_8));
        assertThrows(CommittedCommandVerifier.Conflict.class,this::observe);
    }
    @Test void revisionDriftDuringReadIsRejected() throws Exception {
        server.stubFor(get(urlPathEqualTo("/polarion/sysarch-model/revision")).inScenario("drift").whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED).willSetStateTo("changed").willReturn(okJson(revision("321"))));
        server.stubFor(get(urlPathEqualTo("/polarion/sysarch-model/revision")).inScenario("drift").whenScenarioStateIs("changed").willReturn(okJson(revision("322"))));
        assertEquals(Failure.INTEGRITY,assertThrows(ReadFailure.class,this::observe).failure());
    }
    @Test void changedModelHeadIsRejected() throws Exception {
        server.stubFor(get(urlPathEqualTo(items+"/MODEL-1")).willReturn(okJson(json.writeValueAsString(Map.of("data",resource(root,headAttrs(5)))))));
        assertEquals(Failure.INTEGRITY,assertThrows(ReadFailure.class,this::observe).failure());
    }
    @Test void recordPermissionDenialCannotBeBypassedThroughHistoricalRead() {
        server.stubFor(get(urlPathEqualTo(items+"/COMMIT-1")).willReturn(aResponse().withStatus(403)));
        assertEquals(Failure.DENIED,assertThrows(ReadFailure.class,this::observe).failure());server.verify(0,getRequestedFor(urlPathEqualTo("/polarion/sysarch-model/revision")));
    }
}

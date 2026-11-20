package com.example.sysarch.storage.polarion;

import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.storage.*;
import com.example.sysarch.storage.command.CommandCandidate;
import com.example.sysarch.storage.command.ModelCommandProtocol;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.sync.polarion.PolarionToken;
import com.example.sysarch.storage.polarion.PolarionSnapshotManifest.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;

/** Explicit dedicated-instance acceptance executable, never part of the ordinary test task. */
public final class PolarionLiveReadAcceptance {
    private static final URI SERVER = URI.create("http://localhost/polarion");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ModelCodec CODEC = new ModelCodec();
    private static final String RUN = UUID.randomUUID().toString();
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final List<Map<String, String>> FIXTURES = new ArrayList<>();
    private static final class Account implements AutoCloseable {
        final String project; final PolarionToken token;
        final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        final List<String> owned = new ArrayList<>();
        Account(String suffix) throws Exception {
            project = "sysarch_probe_" + suffix;
            Map<String,String> env = new HashMap<>();
            for (String line : Files.readAllLines(Path.of("build/private/polarion-test-" + suffix + ".env"))) {
                var m = java.util.regex.Pattern.compile("^\\s*(?:export\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*?)\\s*$").matcher(line.replace("\ufeff", ""));
                if (!m.matches()) continue;
                String value = m.group(2);
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1,value.length()-1);
                env.put(m.group(1),value);
            }
            if (!SERVER.toString().equals(env.getOrDefault("POLARION_BASE_URL", SERVER.toString()).replaceAll("/+$", ""))) throw new IllegalStateException("Unexpected dedicated server");
            token = new PolarionToken(env.get("POLARION_TOKEN"));
        }
        HttpResponse<byte[]> call(String method, String path, byte[] body, String contentType) throws Exception {
            var request = HttpRequest.newBuilder(URI.create(SERVER + path)).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token.value()).header("Accept", "application/json");
            if (contentType != null) request.header("Content-Type", contentType);
            return http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
        }
        JsonNode json(String method, String path, Object body, int status) throws Exception {
            var response = call(method,path,body == null ? null : JSON.writeValueAsBytes(body),body == null ? null : "application/json");
            require(response.statusCode() == status, method + " expected " + status + " received " + response.statusCode());
            return response.body().length == 0 ? JSON.createObjectNode() : JSON.readTree(response.body());
        }
        String base() { return "/rest/v1/projects/" + project + "/workitems"; }
        String create(String type, Map<String,Object> fields) throws Exception {
            Map<String,Object> attrs = new LinkedHashMap<>(fields);
            attrs.put("type",type); attrs.put("title","sysarch-m2-" + RUN + "-" + type); attrs.put("sysarchManaged",true); attrs.put("sysarchSchemaVersion",ModelCodec.SCHEMA);
            String id = json("POST",base(),Map.of("data",List.of(wire("workitems",null,attrs))),201).path("data").get(0).path("id").asText();
            return track(id);
        }
        String track(String id) throws Exception {
            require(id.matches(project + "/[A-Za-z0-9_-]+"),"Created identity mismatch");
            String shortId = id.substring(project.length()+1); owned.add(shortId);
            FIXTURES.add(Map.of("project",project,"workItem",shortId)); persist(); return shortId;
        }
        String createRequirement(String version,String text) throws Exception {
            return track(json("POST",base(),Map.of("data",List.of(wire("workitems",null,Map.of("type","requirement",
                "title","sysarch-m2-"+RUN+"-requirement-"+version,"description",Map.of("type","text/plain","value",text))))),201).path("data").get(0).path("id").asText());
        }
        void deleteOwned(String id) throws Exception {
            require(owned.contains(id),"Cleanup target not owned by this run");
            var attrs=json("GET",base()+"/"+id+"?fields%5Bworkitems%5D=title",null,200).path("data").path("attributes");
            require(attrs.path("title").asText().startsWith("sysarch-m2-"+RUN+"-"),"Cleanup scope mismatch");
            json("DELETE",base(),Map.of("data",List.of(Map.of("type","workitems","id",project+"/"+id))),204);
            require(call("GET",base()+"/"+id,null,null).statusCode()==404,"Deleted item still visible"); owned.remove(id);
        }
        String upload(String owner, String filename, byte[] content) throws Exception {
            String boundary = "sysarch" + UUID.randomUUID().toString().replace("-", "");
            String resource = JSON.writeValueAsString(Map.of("data",List.of(wire("workitem_attachments",null,Map.of("fileName",filename,"title",filename)))));
            var bytes = new java.io.ByteArrayOutputStream();
            // Match browser FormData: this server expects metadata as a string part.
            bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"resource\"\r\n\r\n"+resource+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"files\"; filename=\""+filename+"\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            bytes.write(content); bytes.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            var response = call("POST",base()+"/"+owner+"/attachments",bytes.toByteArray(),"multipart/form-data; boundary="+boundary);
            if (response.statusCode()!=201) {
                String title = "";
                try {
                    var error = JSON.readTree(response.body()).path("errors").path(0);
                    title=error.path("title").asText("")+" "+error.path("detail").asText("");
                } catch(Exception ignored) { }
                title=title.replace(token.value(),"REDACTED").replaceAll("[\\r\\n]"," ");
                require(false,"Attachment POST received " + response.statusCode() + " " + title.substring(0,Math.min(160,title.length())));
            }
            String id = JSON.readTree(response.body()).path("data").get(0).path("id").asText();
            require(id.startsWith(project+"/"+owner+"/"),"Attachment owner mismatch");
            return id.substring((project+"/"+owner+"/").length());
        }
        PolarionRestReadSession session() {
            return PolarionRestReadSession.open(SERVER,token);
        }
        public void close() { http.shutdownNow(); }
        void cleanup() throws Exception {
            boolean clean = true;
            for (String id : new ArrayList<>(owned).reversed()) {
                try {
                    deleteOwned(id);
                } catch (Exception e) { clean = false; }
            }
            record(project,"cleanup",clean); require(clean,"Fixture cleanup needs attention");
        }
    }
    private static void require(boolean value,String safeMessage) { if(!value) throw new IllegalStateException(safeMessage); }
    private static Map<String,Object> wire(String type,String id,Map<String,Object> attributes) {
        // The installed 2512 attachment parser rejects attributes before type (verified with otherwise identical requests).
        Map<String,Object> resource = new LinkedHashMap<>(); resource.put("type",type);
        if(id!=null) resource.put("id",id); resource.put("attributes",attributes); return resource;
    }
    private static void record(String project,String check,boolean passed) throws Exception {
        RESULTS.add(Map.of("project",project,"check",check,"passed",passed));
        persist();
        System.out.println(project + " " + check + " " + (passed ? "PASS" : "FAIL"));
    }
    private static void persist() throws Exception {
        Files.createDirectories(Path.of("build/polarion-store"));
        JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of("build/polarion-store/m2-live-"+RUN+".json").toFile(),Map.of("run",RUN,"checks",RESULTS,"fixtures",FIXTURES));
    }
    private static Location fixture(Account account) throws Exception {
        UUID model = UUID.randomUUID();
        var resource = new ResourceImpl(); var definition = SysarchFactory.eINSTANCE.createPartDefinition();
        definition.setId("PD-M2"); definition.setName("Real Polarion System");
        var part = SysarchFactory.eINSTANCE.createPart(); part.setId("PT-M2"); part.setName("Component"); part.setTypedBy(definition);
        definition.getOwnedParts().add(part); resource.getContents().add(definition);
        var snapshot = CODEC.capture(model,List.of(new ModelCodec.SourceDocument(UUID.randomUUID(),resource)),new ModelIdentityRegistry());
        byte[] snapshotBytes = CODEC.encode(snapshot);
        var location=publish(account,snapshot,List.of());
        try (var session = account.session()) {
            var loaded = new PolarionSnapshotLoader(CODEC).load(location,session);
            byte[] rebuilt = CODEC.encode(CODEC.capture(model,loaded.model().documents(),loaded.model().identities()));
            require(Arrays.equals(snapshotBytes,rebuilt),"Reconstructed graph differs from uploaded snapshot");
            record(account.project,"real-rest-reconstruction-and-hash",true);
        }
        return location;
    }
    private static Location publish(Account account,StoredModel snapshot,List<RequirementReference> requirements) throws Exception {
        return publish(account,snapshot,requirements,false);
    }
    private static Location publish(Account account,StoredModel snapshot,List<RequirementReference> requirements,boolean pinBindings) throws Exception {
        UUID model=snapshot.modelUuid(),commit=UUID.randomUUID();byte[] snapshotBytes=CODEC.encode(snapshot);
        String rootId = account.create("sysarchModel",Map.of("sysarchModelUuid",model.toString()));
        Item root = new Item(account.project,rootId,null); var bindings = new ArrayList<Element>();
        try(var session=pinBindings ? account.session() : null) {
            for (var node : snapshot.nodes()) {
                String id = account.create("sysarchElement",Map.of("sysarchModelUuid",model.toString(),"sysarchElementUuid",node.uuid().toString(),"sysarchElementKind",node.type()));
                Item item = new Item(account.project,id,null);
                bindings.add(new Element(node.uuid(),session == null ? item : session.pinCurrentRevision(item)));
            }
        }
        String snapshotId = account.upload(rootId,"snapshot.json",snapshotBytes);
        var manifest = new PolarionSnapshotManifest(ModelCodec.SCHEMA,model,1,commit,new Attachment(root,snapshotId,ModelCodec.sha256(snapshotBytes),snapshotBytes.length),bindings,requirements);
        byte[] manifestBytes = JSON.writeValueAsBytes(manifest);
        String manifestId = account.upload(rootId,"manifest.json",manifestBytes);
        account.json("PATCH",account.base()+"/"+rootId,Map.of("data",wire("workitems",account.project+"/"+rootId,Map.of(
            "sysarchHeadVersion",1,"sysarchHeadCommitId",commit.toString(),"sysarchHeadManifestAttachment",manifestId,
            "sysarchHeadManifestHash",ModelCodec.sha256(manifestBytes),"sysarchHeadManifestBytes",manifestBytes.length))),204);
        return new Location(SERVER,root,model);
    }
    private static void commandPreparation(Account account, Account other, Location unversioned) throws Exception {
        var resource = new ResourceImpl();
        var definition = SysarchFactory.eINSTANCE.createPartDefinition();
        definition.setId("PREP-1");
        definition.setName("Original preparation name");
        resource.getContents().add(definition);
        UUID model = UUID.randomUUID();
        var snapshot = CODEC.capture(model, List.of(new ModelCodec.SourceDocument(UUID.randomUUID(), resource)), new ModelIdentityRegistry());
        var location = publish(account, snapshot, List.of(), true);
        UUID target = snapshot.nodes().getFirst().uuid();
        var protocol = new ModelCommandProtocol();
        try (var session = account.session(); var constraints = new ConstraintValidator()) {
            var loader = new PolarionSnapshotLoader(CODEC);
            var preparation = new PolarionCommandPreparation(loader, new CommandCandidate(CODEC, constraints));
            byte[] body = rename(target, 1, "Prepared name");
            var command = protocol.bind(model, session.verifiedSubject(), body);
            var plan = preparation.prepare(location, command, session);
            require(plan.changes().size() == 1, "Unexpected prepared change count");
            var change = plan.changes().getFirst();
            require(target.equals(change.elementUuid()) && change.expectedItem().revision() != null, "Prepared element has no exact identity or revision");
            require(JSON.readTree(change.payload()).path("attributes").path("name").get(0).asText().equals("Prepared name"), "Candidate name missing");
            require(plan.expectedRoot().equals(session.pinCurrentRevision(location.root())) && plan.expectedHead().equals(session.head(location.root())), "Preparing wrote to the model root");
            require(change.expectedItem().equals(session.pinCurrentRevision(change.expectedItem().current())), "Preparing wrote to the element");
            require(snapshot.equals(loader.loadStored(location, session).model()), "Preparing modified persisted semantics");
            record(account.project, "command-preparation-real-revisions-and-no-write", true);

            var stale = protocol.bind(model, session.verifiedSubject(), rename(target, 0, "Prepared name"));
            org.junit.jupiter.api.Assertions.assertThrows(CommandCandidate.VersionConflict.class, () -> preparation.prepare(location, stale, session));
            var noChange = protocol.bind(model, session.verifiedSubject(), rename(target, 1, "Original preparation name"));
            var unchanged = org.junit.jupiter.api.Assertions.assertThrows(PolarionCommandPreparation.Rejected.class, () -> preparation.prepare(location, noChange, session));
            require(unchanged.failure() == PolarionCommandPreparation.Failure.NO_CHANGE, "No-op command was not rejected");
            record(account.project, "command-preparation-stale-version-and-no-op-denied", true);

            var legacy = protocol.bind(unversioned.modelUuid(), session.verifiedSubject(), body);
            var unpinned = org.junit.jupiter.api.Assertions.assertThrows(PolarionCommandPreparation.Rejected.class, () -> preparation.prepare(unversioned, legacy, session));
            require(unpinned.failure() == PolarionCommandPreparation.Failure.UNVERSIONED_BINDING, "Unversioned bindings were not rejected");
            record(account.project, "command-preparation-unversioned-model-remains-read-only", true);

            try (var foreign = other.session()) {
                var foreignCommand = protocol.bind(model, foreign.verifiedSubject(), body);
                var denied = org.junit.jupiter.api.Assertions.assertThrows(PolarionSnapshotLoader.ReadFailure.class, () -> preparation.prepare(location, foreignCommand, foreign));
                require(denied.failure() == PolarionSnapshotLoader.Failure.DENIED, "Foreign account prepared a command");
                record(other.project, "command-preparation-foreign-pat-denied", true);
            }

            Item item = change.expectedItem();
            account.json("PATCH", account.base() + "/" + item.workItem(), Map.of("data", wire("workitems", item.project() + "/" + item.workItem(),
                Map.of("title", "sysarch-m2-" + RUN + "-external-title-change"))), 204);
            var drift = org.junit.jupiter.api.Assertions.assertThrows(PolarionCommandPreparation.Rejected.class, () -> preparation.prepare(location, command, session));
            require(drift.failure() == PolarionCommandPreparation.Failure.EXTERNAL_CHANGE, "External element revision was not detected");
            require(plan.expectedHead().equals(session.head(location.root())), "Drift fixture changed model head");
            record(account.project, "command-preparation-external-element-drift-denied", true);
        }
    }
    private static byte[] rename(UUID target, long version, String name) throws Exception {
        return JSON.writeValueAsBytes(Map.of("commandId", UUID.randomUUID(), "expectedVersion", version, "schemaVersion", "1.0", "operations", List.of(
            Map.of("kind", "RenameElement", "elementUuid", target, "name", name))));
    }

    private static void managedSdkCommit(Account account, Account other) throws Exception {
        var resource = new ResourceImpl();
        var definition = SysarchFactory.eINSTANCE.createPartDefinition();
        definition.setId("SDK-COMMIT-1");
        definition.setName("Before the SDK transaction");
        resource.getContents().add(definition);
        UUID model = UUID.randomUUID();
        var original = CODEC.capture(model, List.of(new ModelCodec.SourceDocument(UUID.randomUUID(), resource)), new ModelIdentityRegistry());
        Location location = publish(account, original, List.of(), true);
        try (var session = account.session(); var constraints = new ConstraintValidator()) {
            var loader = new PolarionSnapshotLoader(CODEC);
            var preparation = new PolarionCommandPreparation(loader, new CommandCandidate(CODEC, constraints));
            var protocol = new ModelCommandProtocol();
            UUID target = original.nodes().getFirst().uuid();
            var rollback = protocol.bind(model, session.verifiedSubject(), rename(target, 1, "After the SDK transaction"));
            var rollbackPlan = preparation.prepare(location, rollback, session);
            String rootAttachments = account.base() + "/" + location.root().workItem() + "/attachments?fields%5Bworkitem_attachments%5D=fileName";
            JsonNode previousAttachments = account.json("GET", rootAttachments, null, 200).path("data");
            var denied = postManaged(other, rollbackPlan, false);
            boolean deniedByOwnership = denied.statusCode() == 409 && JSON.readTree(denied.body()).path("error").asText().equals("not-an-owned-managed-fixture");
            require(denied.statusCode() == 403 || deniedByOwnership, "Foreign PAT unexpectedly reached managed transaction: " + managedStatus(denied));
            require(rollbackPlan.expectedRoot().equals(session.pinCurrentRevision(location.root())), "Foreign request changed the model root");
            record(other.project, "managed-sdk-foreign-pat-denied", true);

            var failed = postManaged(account, rollbackPlan, true);
            require(failed.statusCode() == 409 && JSON.readTree(failed.body()).path("error").asText().equals("managed-test-rollback"),
                "Managed transaction did not reach rollback injection: " + managedStatus(failed));
            require(rollbackPlan.expectedRoot().equals(session.pinCurrentRevision(location.root()))
                && rollbackPlan.changes().getFirst().expectedItem().equals(session.pinCurrentRevision(rollbackPlan.changes().getFirst().expectedItem().current()))
                && original.equals(loader.loadStored(location, session).model()), "Rollback changed the model root or element");
            require(previousAttachments.isArray() && previousAttachments.equals(account.json("GET", rootAttachments, null, 200).path("data")),
                "Rollback left a model attachment");
            require(session.findCommandRecord(account.project, model, rollback.body().commandId()).isEmpty(), "Rollback left a visible receipt");
            record(account.project, "managed-sdk-rollback-preserves-root-element-and-no-receipt", true);

            var command = protocol.bind(model, session.verifiedSubject(), rename(target, 1, "After the SDK transaction"));
            var plan = preparation.prepare(location, command, session);
            var committed = postManaged(account, plan, false);
            if (committed.statusCode() != 201) {
                var found = session.findCommandRecord(account.project, model, command.body().commandId());
                if (found.isPresent()) account.track(account.project + "/" + found.get().workItem());
                throw new IllegalStateException("Managed SDK commit failed: " + managedStatus(committed));
            }
            JsonNode result = JSON.readTree(committed.body());
            String receiptId = account.track(account.project + "/" + result.path("workItem").asText());
            require(session.verifiedSubject().equals(result.path("actor").asText()), "Transaction actor mismatch");
            var stored = loader.loadStored(location, session);
            require(stored.head().version() == 2 && stored.head().commitId().toString().equals(result.path("commitId").asText())
                && stored.model().equals(plan.snapshot()), "SDK transaction did not publish the candidate snapshot");
            var change = plan.changes().getFirst();
            var element = account.json("GET", account.base() + "/" + change.expectedItem().workItem() + "?fields%5Bworkitems%5D=sysarchPayload", null, 200);
            var payload = element.path("data").path("attributes").path("sysarchPayload");
            require(payload.path("type").asText().equals("text/plain") && change.payload().equals(payload.path("value").asText()),
                "SDK transaction did not persist the candidate payload");
            var rootRevision = session.pinCurrentRevision(location.root()).revision();
            var receiptItem = session.pinCurrentRevision(new Item(account.project, receiptId, null));
            require(rootRevision.equals(receiptItem.revision()) && rootRevision.equals(session.pinCurrentRevision(change.expectedItem().current()).revision()),
                "The root, element, and receipt were not assigned the same revision");
            var receipt = session.commandReceipt(receiptItem, model, command.body().commandId());
            require(receipt.version() == 2 && receipt.commitId().equals(stored.head().commitId()) && receipt.requestHash().equals(command.requestHash())
                && receipt.manifestHash().equals(stored.head().manifest().sha256()), "SDK transaction receipt does not match published model");
            record(account.project, "managed-sdk-atomic-payload-snapshot-head-receipt-same-revision", true);
            var replay = postManaged(account, plan, false);
            require(replay.statusCode() == 409 && session.head(location.root()).version() == 2, "Stale transaction plan was accepted");
            record(account.project, "managed-sdk-stale-plan-denied", true);
        }
    }

    private static void historicalBindingRevisions(Account account, Account other) throws Exception {
        String targetId = account.createRequirement("checkpoint-element", "Earlier element data");
        Item target = new Item(account.project,targetId,null);
        try (var session = account.session()) {
            Item initial = session.pinCurrentRevision(target);
            String anchorId = account.createRequirement("checkpoint-anchor", "Repository checkpoint");
            String checkpoint = session.pinCurrentRevision(new Item(account.project,anchorId,null)).revision();
            require(new java.math.BigInteger(checkpoint).compareTo(new java.math.BigInteger(initial.revision())) > 0,"Test checkpoint must follow initial object revision");
            account.json("PATCH",account.base()+"/"+targetId,Map.of("data",wire("workitems",account.project+"/"+targetId,
                Map.of("title","sysarch-m2-"+RUN+"-rewritten-checkpoint-element"))),204);
            Item current = session.pinCurrentRevision(target);
            require(!initial.equals(current) && !checkpoint.equals(current.revision()),"Fixture did not advance to a later revision");
            require(initial.equals(session.pinRevisionAt(target,checkpoint)),"Checkpoint lookup did not recover the earlier object revision");
            require(current.equals(session.pinRevisionAt(target,current.revision())),"Checkpoint lookup did not resolve the changed object's revision");
            record(account.project,"historical-binding-resolves-unchanged-and-changed-revisions",true);
            try (var foreign=other.session()) {
                var denied=org.junit.jupiter.api.Assertions.assertThrows(PolarionSnapshotLoader.ReadFailure.class,()->foreign.pinRevisionAt(target,checkpoint));
                require(denied.failure()==PolarionSnapshotLoader.Failure.DENIED,"Foreign PAT unexpectedly resolved historical revision");
                record(other.project,"historical-binding-requires-current-access",true);
                String foreignId=other.createRequirement("checkpoint-foreign", "Other project's data");
                String foreignRevision=foreign.pinCurrentRevision(new Item(other.project,foreignId,null)).revision();
                String foreignPath="/sysarch-model/revision?project="+other.project+"&workItem="+foreignId+"&atRevision="+foreignRevision;
                require(account.call("GET",foreignPath,null,null).statusCode()==403,"SDK bridge allowed a foreign project directly");
                record(account.project,"historical-binding-sdk-enforces-project-access",true);
            }
            account.deleteOwned(targetId);
            var deleted=org.junit.jupiter.api.Assertions.assertThrows(PolarionSnapshotLoader.ReadFailure.class,()->session.pinRevisionAt(target,checkpoint));
            require(deleted.failure()==PolarionSnapshotLoader.Failure.DENIED,"Deleted binding was still resolved");
            record(account.project,"historical-binding-rejects-deleted-current-object",true);
        }
    }

    private static String managedStatus(HttpResponse<byte[]> response) {
        try {
            JsonNode body = JSON.readTree(response.body());
            String code = body.path("error").asText();
            List<String> types = new ArrayList<>();
            for (JsonNode type : body.path("types")) {
                if (types.size() < 8 && type.asText().matches("[A-Za-z][A-Za-z0-9]{0,100}")) types.add(type.asText());
            }
            String stage = body.path("stage").asText();
            return response.statusCode() + " " + (code.matches("[a-z0-9-]{1,80}") ? code : "unknown") + " " + types
                + (stage.matches("MANAGED_[A-Z_]{1,60}") ? " " + stage : "");
        } catch (Exception ignored) { return Integer.toString(response.statusCode()); }
    }

    private static HttpResponse<byte[]> postManaged(Account caller, PolarionCommandPreparation.Plan plan, boolean rollback) throws Exception {
        var fields = new LinkedHashMap<String, String>();
        var change = plan.changes().getFirst();
        fields.put("project", plan.location().root().project());
        fields.put("run", RUN);
        fields.put("root", plan.location().root().workItem());
        fields.put("modelUuid", plan.location().modelUuid().toString());
        fields.put("command", plan.command().body().commandId().toString());
        fields.put("element", change.expectedItem().workItem());
        fields.put("elementUuid", change.elementUuid().toString());
        fields.put("rootRevision", plan.expectedRoot().revision());
        fields.put("elementRevision", change.expectedItem().revision());
        fields.put("expectedCommit", plan.expectedHead().commitId().toString());
        fields.put("expectedManifestHash", plan.expectedHead().manifest().sha256());
        fields.put("requestHash", plan.command().requestHash());
        fields.put("snapshot", Base64.getUrlEncoder().encodeToString(CODEC.encode(plan.snapshot())));
        fields.put("payload", change.payload());
        if (rollback) fields.put("fault", "rollback");
        String body = fields.entrySet().stream().map(field -> field.getKey() + "=" + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8))
            .collect(java.util.stream.Collectors.joining("&"));
        var request = HttpRequest.newBuilder(URI.create(SERVER + "/sysarch-m0/managed-commit")).timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + caller.token.value()).header("X-Sysarch-Probe", "1")
            .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return caller.http.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
    private static Location reference(Account account,Item target) throws Exception {
        var resource=new ResourceImpl(); var requirement=SysarchFactory.eINSTANCE.createRequirement();
        requirement.setId("REF-HISTORY");requirement.setName("Requirement reference");resource.getContents().add(requirement);
        var identities=new ModelIdentityRegistry();
        var snapshot=CODEC.capture(UUID.randomUUID(),List.of(new ModelCodec.SourceDocument(UUID.randomUUID(),resource)),identities);
        return publish(account,snapshot,List.of(new RequirementReference(identities.id(requirement),target)));
    }
    private static void history(Account account,Account foreign,Location foreignModel) throws Exception {
        String oldBody="Historical requirement V1 "+RUN,newBody="Current requirement V2 "+RUN;
        String id=account.createRequirement("v1",oldBody);Item current=new Item(account.project,id,null),pinned;
        try(var session=account.session()) { pinned=session.pinCurrentRevision(current); }
        record(account.project,"requirement-real-sdk-revision",true);
        account.json("PATCH",account.base()+"/"+id,Map.of("data",wire("workitems",account.project+"/"+id,Map.of(
            "title","sysarch-m2-"+RUN+"-requirement-v2","description",Map.of("type","text/plain","value",newBody)))),204);
        try(var session=account.session()) {
            Item latest=session.pinCurrentRevision(current);require(!latest.revision().equals(pinned.revision()),"Requirement revision did not advance");
            require(oldBody.equals(session.requirement(pinned).text()),"Pinned requirement changed with HEAD");
            require(newBody.equals(session.requirement(latest).text()),"Latest requirement body mismatch");
        }
        record(account.project,"requirement-pinned-v1-after-v2",true);
        Location model=reference(account,pinned);
        PolarionSnapshotManifest manifest;
        try(var session=account.session()) {
            var loaded=new PolarionSnapshotLoader(CODEC).load(model,session);
            var requirement=(com.example.sysarch.model.Requirement)loaded.model().documents().getFirst().resource().getContents().getFirst();
            require(oldBody.equals(requirement.getText()) && requirement.getName().endsWith("-v1"),"Historical overlay mismatch");
            var head=session.head(model.root());manifest=JSON.readValue(session.attachment(head.manifest(),PolarionSnapshotLoader.MAX_MANIFEST_BYTES),PolarionSnapshotManifest.class);
            String stored=new String(session.attachment(manifest.snapshot(),ModelCodec.MAX_BYTES),StandardCharsets.UTF_8);
            require(!stored.contains(oldBody) && !stored.contains(newBody) && !stored.contains("requirement-v1"),"External body leaked into stored snapshot");
        }
        record(account.project,"requirement-history-overlay-and-no-stored-body",true);
        // B has SDK read rights on A; test the genuinely forbidden A -> B direction and use the binary media type.
        PolarionSnapshotManifest foreignManifest;
        try(var session=foreign.session()) {
            var head=session.head(foreignModel.root());foreignManifest=JSON.readValue(session.attachment(head.manifest(),PolarionSnapshotLoader.MAX_MANIFEST_BYTES),PolarionSnapshotManifest.class);
        }
        int ownStatus=nativeAttachment(foreign,foreignManifest.snapshot()),deniedStatus=nativeAttachment(account,foreignManifest.snapshot());
        require(ownStatus==200,"Native owner attachment status: "+ownStatus);
        require(deniedStatus==403 || deniedStatus==404,"Native forbidden attachment status: "+deniedStatus);
        record(account.project,"native-rest-forbidden-project-snapshot-denied",true);
        if("true".equals(System.getenv("SYSARCH_M2_DEPLOYED_HTTP"))) {
            var request=HttpRequest.newBuilder(URI.create("http://localhost:8080/api/v1/model-store/projects/"+account.project+"/models/"+model.root().workItem()+"/snapshot?modelUuid="+model.modelUuid()))
                .timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+account.token.value()).GET().build();
            var response=account.http.send(request,HttpResponse.BodyHandlers.ofByteArray());
            require(response.statusCode()==200,"Deployed requirement history read failed");
            require(oldBody.equals(JSON.readTree(response.body()).path("model").path("nodes").get(0).path("attributes").path("text").get(0).asText()),"Deployed historical body mismatch");
            record(account.project,"deployed-requirement-historical-overlay",true);
        }
        if("true".equals(System.getenv("SYSARCH_M2_DEPLOYED_UI"))) {
            var browser=new ProcessBuilder("node","tests/e2e/model-store-ui.mjs","C:/Program Files/Google/Chrome/Application/chrome.exe","--live-requirement",model.root().workItem(),pinned.revision()).inheritIO().start();
            boolean ended=browser.waitFor(60,java.util.concurrent.TimeUnit.SECONDS);
            if(!ended) {browser.descendants().forEach(ProcessHandle::destroyForcibly);browser.destroyForcibly();}
            require(ended && browser.exitValue()==0,"Historical requirement browser acceptance failed");
            record(account.project,"deployed-browser-requirement-provenance",true);
        }
        String foreignId=foreign.createRequirement("foreign","Restricted foreign requirement "+RUN); Item foreignPinned;
        try(var session=foreign.session()) { foreignPinned=session.pinCurrentRevision(new Item(foreign.project,foreignId,null)); }
        Location cross=reference(account,foreignPinned);
        try(var session=account.session()) {
            var defaultDenied=org.junit.jupiter.api.Assertions.assertThrows(PolarionSnapshotLoader.ReadFailure.class,()->new PolarionSnapshotLoader(CODEC).load(cross,session));
            require(defaultDenied.failure()==PolarionSnapshotLoader.Failure.DENIED,"Cross-project policy did not deny");
        }
        record(account.project,"requirement-cross-project-default-denied",true);
        try(var session=account.session()) {
            var allowed=new PolarionSnapshotLoader(CODEC,(source,target)->source.equals(account.project) && target.equals(foreign.project));
            var denied=org.junit.jupiter.api.Assertions.assertThrows(PolarionSnapshotLoader.ReadFailure.class,()->allowed.load(cross,session));
            require(denied.failure()==PolarionSnapshotLoader.Failure.DENIED,"Explicit policy bypassed requirement permission");
        }
        record(account.project,"requirement-allowlist-still-checks-object-permission",true);
        if("true".equals(System.getenv("SYSARCH_M2_DEPLOYED_HTTP"))) {
            deployedDependencyDenied(account,cross,oldBody);
            record(account.project,"deployed-requirement-cross-project-denied",true);
        }
        if("true".equals(System.getenv("SYSARCH_M2_LIFECYCLE")) && "true".equals(System.getenv("SYSARCH_M2_DEPLOYED_UI"))) {
            Path ready=Path.of("build/polarion-store/revocation-ready-"+model.root().workItem()+".json");
            require(!Files.exists(ready),"Revocation browser marker already exists");
            var browser=new ProcessBuilder("node","tests/e2e/model-store-ui.mjs","C:/Program Files/Google/Chrome/Application/chrome.exe","--live-revocation",model.root().workItem()).inheritIO().start();
            try {
                long deadline=System.nanoTime()+Duration.ofSeconds(30).toNanos();
                while(!Files.exists(ready) && browser.isAlive() && System.nanoTime()<deadline) Thread.sleep(100);
                require(Files.exists(ready),"Revocation browser did not open the dependency");
                account.deleteOwned(id);
                require(browser.waitFor(45,java.util.concurrent.TimeUnit.SECONDS) && browser.exitValue()==0,"Dependency revocation browser failed");
                record(account.project,"deployed-browser-active-read-dependency-revocation",true);
            } finally {
                if(browser.isAlive()) {browser.descendants().forEach(ProcessHandle::destroyForcibly);browser.destroyForcibly();}
            }
        } else {
            account.deleteOwned(id);
        }
        try(var session=account.session()) {
            var denied=org.junit.jupiter.api.Assertions.assertThrows(PolarionSnapshotLoader.ReadFailure.class,()->new PolarionSnapshotLoader(CODEC).load(model,session));
            require(denied.failure()==PolarionSnapshotLoader.Failure.DENIED,"Deleted dependency still readable through snapshot");
        }
        record(account.project,"requirement-deleted-current-dependency-denied",true);
        if("true".equals(System.getenv("SYSARCH_M2_DEPLOYED_HTTP"))) {
            deployedDependencyDenied(account,model,oldBody);
            record(account.project,"deployed-deleted-requirement-no-cached-body",true);
        }
    }
    private static void deployedDependencyDenied(Account account,Location model,String body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:8080/api/v1/model-store/projects/"+account.project+"/models/"+model.root().workItem()+"/snapshot?modelUuid="+model.modelUuid()))
            .timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+account.token.value()).GET().build();
        var response=account.http.send(request,HttpResponse.BodyHandlers.ofByteArray());
        require(response.statusCode()==403 && "no-store".equals(response.headers().firstValue("Cache-Control").orElse("")),"Unavailable dependency did not block deployed read");
        require(!new String(response.body(),StandardCharsets.UTF_8).contains(body),"Denied response leaked previously readable body");
    }
    private static void receipts(Account account,Account other,Location location) throws Exception {
        try(var session=account.session()) {
            var loaded=new PolarionSnapshotLoader(CODEC).load(location,session);var head=loaded.head();
            var snapshot=CODEC.capture(location.modelUuid(),loaded.model().documents(),loaded.model().identities());
            UUID commandId=UUID.randomUUID();
            byte[] request=JSON.writeValueAsBytes(Map.of("commandId",commandId,"expectedVersion",head.version()-1,"schemaVersion","1.0","operations",List.of(
                Map.of("kind","RenameElement","elementUuid",snapshot.nodes().getFirst().uuid(),"name","Receipt observation fixture"))));
            var protocol=new com.example.sysarch.storage.command.ModelCommandProtocol();
            var bound=protocol.bind(location.modelUuid(),session.verifiedSubject(),request);
            var receipt=new com.example.sysarch.storage.command.CommittedCommandVerifier.Receipt("1.0",location.modelUuid(),commandId,session.verifiedSubject(),bound.requestHash(),
                head.version()-1,head.version(),head.commitId(),head.manifest().sha256());
            Map<String,Object> fields=Map.of("sysarchModelUuid",location.modelUuid().toString(),"sysarchCommandId",commandId.toString(),"sysarchCommitId",head.commitId().toString(),
                "sysarchRequestHash",bound.requestHash(),"sysarchManifestHash",head.manifest().sha256());
            String id=account.create("sysarchCommit",fields);Item current=new Item(account.project,id,null);
            account.upload(id,PolarionRestReadSession.COMMAND_RECEIPT_ATTACHMENT,JSON.writeValueAsBytes(receipt));
            Item initial=session.pinCurrentRevision(current);
            try {require(receipt.equals(session.commandReceipt(initial,location.modelUuid(),commandId)),"Receipt payload differs");}
            catch(PolarionSnapshotLoader.ReadFailure failure) {
                throw new IllegalStateException("Direct receipt rejected: "+failure.failure().name());
            }
            var lookup=new PolarionCommandReceiptLookup(new PolarionSnapshotLoader(CODEC));
            Optional<PolarionCommandReceiptLookup.Observation> observed=Optional.empty();long deadline=System.nanoTime()+Duration.ofSeconds(30).toNanos();
            do {
                try {observed=lookup.observe(location,bound,session);}
                catch(PolarionSnapshotLoader.ReadFailure failure) {throw new IllegalStateException("Receipt observation rejected: "+failure.failure().name());}
                if(observed.isPresent()) break;Thread.sleep(200);
            } while(System.nanoTime()<deadline);
            require(observed.isPresent() && receipt.equals(observed.get().receipt()),"Real receipt observation failed");
            require(observed.get().commitRecord().revision().matches("[0-9]{1,20}"),"Receipt has no actual SDK revision");
            record(account.project,"receipt-real-rest-and-sdk-pinned-revision",true);
            require(observed.get().assurance()==PolarionCommandReceiptLookup.Assurance.UNATTESTED
                && observed.get().gateOutcome()==com.example.sysarch.storage.command.DurableCommandGate.Outcome.UNKNOWN,"Native fixture incorrectly certified as a commit");
            record(account.project,"receipt-native-fixture-never-unlocks-command-gate",true);
            var changed=protocol.bind(location.modelUuid(),session.verifiedSubject(),new String(request,StandardCharsets.UTF_8).replace("Receipt observation fixture","Different fixture").getBytes(StandardCharsets.UTF_8));
            org.junit.jupiter.api.Assertions.assertThrows(com.example.sysarch.storage.command.CommittedCommandVerifier.Conflict.class,()->lookup.observe(location,changed,session));
            record(account.project,"receipt-same-command-different-request-conflict",true);
            try(var foreign=other.session()) {
                var foreignBound=protocol.bind(location.modelUuid(),foreign.verifiedSubject(),request);
                var denied=org.junit.jupiter.api.Assertions.assertThrows(PolarionSnapshotLoader.ReadFailure.class,()->lookup.observe(location,foreignBound,foreign));
                require(denied.failure()==PolarionSnapshotLoader.Failure.DENIED,"Foreign account read receipt through model");
                record(other.project,"receipt-cross-project-access-denied",true);
            }
            String duplicate=account.create("sysarchCommit",fields);boolean rejected=false;deadline=System.nanoTime()+Duration.ofSeconds(30).toNanos();
            do {
                try {session.findCommandRecord(account.project,location.modelUuid(),commandId);}
                catch(PolarionSnapshotLoader.ReadFailure failure) {if(failure.failure()!=PolarionSnapshotLoader.Failure.INCOMPLETE) throw failure;rejected=true;}
                if(rejected) break;Thread.sleep(200);
            } while(System.nanoTime()<deadline);
            require(rejected,"Duplicate command records were not rejected");record(account.project,"receipt-duplicate-command-records-denied",true);account.deleteOwned(duplicate);
            Item pinned=observed.get().commitRecord();
            account.json("PATCH",account.base()+"/"+id,Map.of("data",wire("workitems",account.project+"/"+id,Map.of("sysarchRequestHash","c".repeat(64)))),204);
            Item altered=session.pinCurrentRevision(current);require(!altered.revision().equals(pinned.revision()),"Receipt revision did not advance");
            var invalid=org.junit.jupiter.api.Assertions.assertThrows(PolarionSnapshotLoader.ReadFailure.class,()->session.commandReceipt(altered,location.modelUuid(),commandId));
            require(invalid.failure()==PolarionSnapshotLoader.Failure.IDENTITY,"Altered receipt fields were accepted");
            require(receipt.equals(session.commandReceipt(pinned,location.modelUuid(),commandId)),"Original receipt revision changed");
            record(account.project,"receipt-current-field-drift-denied-and-history-pinned",true);
        }
    }
    private static int nativeAttachment(Account account,Attachment attachment) throws Exception {
        Item owner=attachment.owner();
        var request=HttpRequest.newBuilder(URI.create(SERVER+"/rest/v1/projects/"+owner.project()+"/workitems/"+owner.workItem()+"/attachments/"+attachment.attachmentId()+"/content"))
            .timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+account.token.value()).header("Accept","application/octet-stream").GET().build();
        var response=account.http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(var input=response.body()) { return response.statusCode(); }
    }
    private static void views(Account account,Account other) throws Exception {
        var f=SysarchFactory.eINSTANCE;var resource=new ResourceImpl();UUID model=UUID.randomUUID();
        var system=f.createPartDefinition();system.setId("DUPLICATE");system.setName("View System");system.setLayer(com.example.sysarch.model.Layer.SYSTEM);
        var logical=f.createPartDefinition();logical.setId("DUPLICATE");logical.setName("View Logical");logical.setLayer(com.example.sysarch.model.Layer.LOGICAL);
        var physical=f.createPartDefinition();physical.setId("DUPLICATE");physical.setName("View Physical");physical.setLayer(com.example.sysarch.model.Layer.PHYSICAL);
        var part=f.createPart();part.setId("P-VIEW");part.setName("View Component");part.setTypedBy(logical);system.getOwnedParts().add(part);
        var iface=f.createInterfaceDef();iface.setId("IF-VIEW");iface.setName("CAN");
        var source=f.createPort();source.setId("PO-OUT");source.setName("external");source.setInterfaceType(iface);source.setDirection(com.example.sysarch.model.PortDirection.OUT);system.getOwnedPorts().add(source);
        var target=f.createPort();target.setId("PO-IN");target.setName("internal");target.setInterfaceType(iface);target.setDirection(com.example.sysarch.model.PortDirection.IN);part.getPorts().add(target);
        var connection=f.createConnection();connection.setId("C-VIEW");connection.setName("Bus");connection.setSource(source);connection.setTarget(target);connection.setInterfaceType(iface);
        var a=f.createAllocate();a.setId("AL-VIEW1");a.setFrom(system);a.setTo(logical);
        var b=f.createAllocate();b.setId("AL-VIEW2");b.setFrom(logical);b.setTo(physical);
        var requirement=f.createRequirement();requirement.setId("R-VIEW");requirement.setName("Trace requirement");requirement.getSatisfiedBy().add(logical);
        resource.getContents().addAll(List.of(system,logical,physical,iface,connection,a,b,requirement));
        var identities=new ModelIdentityRegistry();var stored=CODEC.capture(model,List.of(new ModelCodec.SourceDocument(UUID.randomUUID(),resource)),identities);
        byte[] bytes=CODEC.encode(stored);Location location=publish(account,stored,List.of());
        var engine=new PolarionReadViews();
        try(var session=account.session()) {
            var loaded=new PolarionSnapshotLoader(CODEC).load(location,session);
            for(var description:engine.descriptions()) {
                UUID root=description.kind().equals("DIAGRAM")?identities.id(system):null;
                var result=engine.render(loaded.model(),description.id(),root);
                if(result.matrix()!=null) {
                    require(result.matrix().actions().isEmpty(),"Projection exposes edit actions");
                    require(result.matrix().cells().size()==(description.id().equals("allocation-matrix")?2:1),"Matrix reconstruction mismatch");
                } else require(!result.diagram().getNodes().isEmpty(),"Sirius diagram is empty");
                record(account.project,"read-view-"+description.id(),true);
                if("true".equals(System.getenv("SYSARCH_M2_DEPLOYED_HTTP"))) {
                    String path="http://localhost:8080/api/v1/model-store/projects/"+account.project+"/models/"+location.root().workItem()+"/views/"+description.id()+"?modelUuid="+model+(root==null?"":"&elementUuid="+root);
                    var request=HttpRequest.newBuilder(URI.create(path)).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+account.token.value()).GET().build();
                    var response=account.http.send(request,HttpResponse.BodyHandlers.ofByteArray());
                    require(response.statusCode()==200,"Deployed view status: "+response.statusCode());
                    require("no-store".equals(response.headers().firstValue("Cache-Control").orElse("")),"Deployed view is cacheable");
                    JsonNode payload=JSON.readTree(response.body()).path("representation");
                    require(description.id().equals(payload.path("viewId").asText()),"Wrong view returned");
                    if(result.matrix()!=null) require(payload.path("matrix").path("actions").isEmpty() && payload.path("matrix").path("cells").size()==result.matrix().cells().size(),"Deployed matrix differs");
                    else require(payload.path("diagram").path("nodes").size()>0,"Deployed Sirius diagram absent");
                    record(account.project,"deployed-view-"+description.id(),true);
                }
            }
            require(Arrays.equals(bytes,CODEC.encode(CODEC.capture(model,loaded.model().documents(),loaded.model().identities()))),"Read views modified semantic model");
            record(account.project,"read-views-preserve-semantic-hash",true);
        }
        try(var session=account.session()) {
            var fresh=new PolarionSnapshotLoader(CODEC).load(location,session);
            require(!engine.render(fresh.model(),"bdd",identities.id(system)).diagram().getNodes().isEmpty(),"Fresh reconstruction failed");
        }
        record(account.project,"read-view-fresh-reconstruction-without-cached-objects",true);
        if("true".equals(System.getenv("SYSARCH_M2_DEPLOYED_HTTP"))) {
            var request=HttpRequest.newBuilder(URI.create("http://localhost:8080/api/v1/model-store/projects/"+account.project+"/models/"+location.root().workItem()+"/views/bdd?modelUuid="+model+"&elementUuid="+identities.id(system)))
                .timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+other.token.value()).GET().build();
            require(other.http.send(request,HttpResponse.BodyHandlers.discarding()).statusCode()==403,"Projection bypassed model permission");
            record(other.project,"deployed-view-cross-project-denied",true);
        }
        if("true".equals(System.getenv("SYSARCH_M2_DEPLOYED_UI"))) {
            var browser=new ProcessBuilder("node","tests/e2e/model-store-ui.mjs","C:/Program Files/Google/Chrome/Application/chrome.exe","--live-matrix",location.root().workItem()).inheritIO().start();
            boolean ended=browser.waitFor(60,java.util.concurrent.TimeUnit.SECONDS);
            if(!ended) {browser.descendants().forEach(ProcessHandle::destroyForcibly);browser.destroyForcibly();}
            require(ended && browser.exitValue()==0,"Read-only matrix browser failed");record(account.project,"deployed-browser-read-only-matrices",true);
            if("true".equals(System.getenv("SYSARCH_M2_CANVAS"))) {
                var canvasBrowser=new ProcessBuilder("node","tests/e2e/model-store-ui.mjs","C:/Program Files/Google/Chrome/Application/chrome.exe","--live-canvas",location.root().workItem()).inheritIO().start();
                boolean canvasEnded=canvasBrowser.waitFor(60,java.util.concurrent.TimeUnit.SECONDS);
                if(!canvasEnded) {canvasBrowser.descendants().forEach(ProcessHandle::destroyForcibly);canvasBrowser.destroyForcibly();}
                require(canvasEnded && canvasBrowser.exitValue()==0,"Read-only native canvas browser failed");record(account.project,"deployed-browser-native-read-only-canvases",true);
            }
        }
    }
    private static void fields(Account account) throws Exception {
        JsonNode schema=JSON.readTree(Files.readAllBytes(Path.of("backend/src/main/resources/storage/polarion-store-schema.json")));
        var types=schema.path("types").fields();
        while(types.hasNext()) {
            var type=types.next(); Map<String,Object> attrs=new LinkedHashMap<>();
            var common=schema.path("common").fields();
            while(common.hasNext()) { var field=common.next(); attrs.put(field.getKey(),fieldValue(field.getKey(),field.getValue().asText())); }
            var custom=type.getValue().fields();
            while(custom.hasNext()) { var field=custom.next(); attrs.put(field.getKey(),fieldValue(field.getKey(),field.getValue().asText())); }
            attrs.put("sysarchSchemaVersion",ModelCodec.SCHEMA);
            String id=account.create(type.getKey(),attrs);
            JsonNode read=account.json("GET",account.base()+"/"+id+"?fields%5Bworkitems%5D="+String.join(",",attrs.keySet()),null,200).path("data").path("attributes");
            for(var field:attrs.entrySet()) require(JSON.valueToTree(field.getValue()).equals(read.path(field.getKey())),"Field roundtrip mismatch: "+type.getKey()+"."+field.getKey());
            record(account.project,"fields-"+type.getKey(),true);
        }
    }
    private static Object fieldValue(String name,String kind) {
        return switch(kind) {
            case "boolean" -> true;
            case "integer" -> 7;
            case "text" -> Map.of("type","text/plain","value","x".repeat(64*1024));
            case "string" -> name.endsWith("Hash") ? "a".repeat(64) : UUID.randomUUID().toString();
            default -> throw new IllegalStateException("Unsupported schema kind");
        };
    }
    private static void discover(Account account,Location expected) throws Exception {
        Set<UUID> seen=new HashSet<>(); boolean found=false, ended=false; int pages=0;
        try(var session=account.session()) {
            for(int page=1;page<=30;page++) {
                var result=session.models(account.project,page,1); pages++;
                for(var model:result.models()) {
                    require(seen.add(model.location().modelUuid()),"Discovery duplicate UUID across pages");
                    if(model.location().equals(expected)) found=true;
                }
                if(!result.mayHaveMore()) { ended=true; break; }
            }
        }
        require(found && ended && pages>=2,"Paged discovery incomplete");
        record(account.project,"real-model-discovery-pagination",true);
    }
    private static org.springframework.test.web.servlet.MockMvc httpController() {
        var properties=new com.example.sysarch.sync.SyncProperties(); properties.setBaseUrl(SERVER.toString());
        return org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new PolarionReadController(properties,CODEC))
            .addFilters(new com.example.sysarch.identity.ReadAccessFilter(new com.example.sysarch.identity.AuthProperties())).build();
    }
    private static void controller(Account account,Location location,boolean denied) throws Exception {
        var mvc=httpController();
        var request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/model-store/projects/"+location.root().project()+"/models/"+location.root().workItem()+"/snapshot")
            .param("modelUuid",location.modelUuid().toString()).header("Authorization","Bearer "+account.token.value());
        var response=mvc.perform(request).andReturn().getResponse();
        require(response.getStatus()==(denied?403:200),"HTTP snapshot status mismatch");
        require("no-store".equals(response.getHeader("Cache-Control")),"HTTP response is cacheable");
        if(!denied) {
            JsonNode body=JSON.readTree(response.getContentAsByteArray());
            require(body.path("model").path("modelUuid").asText().equals(location.modelUuid().toString()) && body.path("model").path("nodes").size()==2,"HTTP rebuilt model mismatch");
        }
        record(account.project,denied?"http-cross-project-denial":"http-rebuilt-snapshot",true);
        if("true".equals(System.getenv("SYSARCH_M2_DEPLOYED_HTTP"))) {
            String path="/api/v1/model-store/projects/"+location.root().project()+"/models/"+location.root().workItem()+"/snapshot?modelUuid="+location.modelUuid();
            var liveRequest=HttpRequest.newBuilder(URI.create("http://localhost:8080"+path)).timeout(Duration.ofSeconds(30))
                .header("Authorization","Bearer "+account.token.value()).GET().build();
            var live=account.http.send(liveRequest,HttpResponse.BodyHandlers.ofByteArray());
            require(live.statusCode()==(denied?403:200),"Deployed HTTP snapshot status mismatch");
            require("no-store".equals(live.headers().firstValue("Cache-Control").orElse("")),"Deployed response is cacheable");
            if(!denied) require(JSON.readTree(live.body()).path("model").path("modelUuid").asText().equals(location.modelUuid().toString()),"Deployed model identity mismatch");
            record(account.project,denied?"deployed-http-cross-project-denial":"deployed-http-rebuilt-snapshot",true);
        }
    }
    public static void main(String[] args) throws Exception {
        try (var a = new Account("a"); var b = new Account("b")) {
            try {
                fields(a); fields(b);
                Location first = fixture(a), second = fixture(b);
                discover(a,first); discover(b,second);
                controller(a,first,false); controller(b,second,false); controller(b,first,true);
                if("true".equals(System.getenv("SYSARCH_M3_RECEIPTS"))) receipts(a,b,first);
                if("true".equals(System.getenv("SYSARCH_M3_PREPARATION"))) commandPreparation(a,b,first);
                if("true".equals(System.getenv("SYSARCH_M3_MANAGED_SDK"))) managedSdkCommit(a,b);
                if("true".equals(System.getenv("SYSARCH_M3_HISTORICAL_BINDINGS"))) historicalBindingRevisions(a,b);
                if("true".equals(System.getenv("SYSARCH_M2_REQUIREMENTS"))) history(a,b,second);
                if("true".equals(System.getenv("SYSARCH_M2_VIEWS"))) views(a,b);
                if("true".equals(System.getenv("SYSARCH_M2_DEPLOYED_UI"))) {
                    require("true".equals(System.getenv("SYSARCH_M2_DEPLOYED_HTTP")),"Browser acceptance requires deployed HTTP checks");
                    var browser=new ProcessBuilder("node","tests/e2e/model-store-ui.mjs","C:/Program Files/Google/Chrome/Application/chrome.exe","--live",first.root().workItem()).inheritIO().start();
                    boolean ended=browser.waitFor(60,java.util.concurrent.TimeUnit.SECONDS);
                    if(!ended) { browser.descendants().forEach(ProcessHandle::destroyForcibly); browser.destroyForcibly(); }
                    require(ended && browser.exitValue()==0,"Real model browser acceptance failed");
                    record(a.project,"deployed-browser-model-and-reference",true);
                }
                try (var session = b.session()) {
                    var failure = org.junit.jupiter.api.Assertions.assertThrows(PolarionSnapshotLoader.ReadFailure.class,()->new PolarionSnapshotLoader(CODEC).load(first,session));
                    require(failure.failure()==PolarionSnapshotLoader.Failure.DENIED,"Cross-project denial failed");
                    record(b.project,"cross-project-rest-denial",true);
                }
            } catch (Exception | AssertionError e) {
                record("dedicated-instance","execution",false);
                throw new IllegalStateException("Dedicated acceptance failed: " + e.getClass().getSimpleName() + (e instanceof IllegalStateException ? " (" + e.getMessage() + ")" : ""));
            } finally {
                try { a.cleanup(); } finally { b.cleanup(); }
            }
        }
        System.out.println("Report: build/polarion-store/m2-live-"+RUN+".json");
    }
}

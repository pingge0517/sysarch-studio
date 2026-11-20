package com.example.sysarch.storage.polarion;

import com.example.sysarch.storage.ModelCodec;
import com.example.sysarch.storage.command.CommandReceiptCodec;
import com.example.sysarch.storage.command.CommittedCommandVerifier.Receipt;
import com.example.sysarch.storage.polarion.PolarionSnapshotLoader.Failure;
import com.example.sysarch.storage.polarion.PolarionSnapshotLoader.ReadFailure;
import com.example.sysarch.storage.polarion.PolarionSnapshotManifest.*;
import com.example.sysarch.sync.polarion.PolarionText;
import com.example.sysarch.sync.polarion.PolarionToken;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Request-scoped, GET-only transport. No implicit service account, redirect following, credential serialization or cache. */
public final class PolarionRestReadSession implements PolarionModelReadSession, AutoCloseable {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final ScheduledThreadPoolExecutor DEADLINES;
    static {
        DEADLINES = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "sysarch-polarion-read-deadline"); thread.setDaemon(true); return thread;
        });
        DEADLINES.setRemoveOnCancelPolicy(true);
    }
    @FunctionalInterface
    public interface CredentialVerifier {
        /** Trusted server-side verifier must resolve the real token owner, not accept a client-supplied name. */
        String verify(URI configuredServer, PolarionToken token);
    }

    private final URI server;
    private final String subject;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final Set<InputStream> active = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private PolarionToken token;

    private PolarionRestReadSession(URI server, PolarionToken token, String subject) {
        this.server = server; this.token = token; this.subject = subject;
        this.http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
    }

    public static PolarionRestReadSession open(URI server, PolarionToken token, CredentialVerifier verifier) {
        Objects.requireNonNull(token); Objects.requireNonNull(verifier);
        // Reuse the locator's URL validation without granting any model access.
        new Location(server, new Item("validation", "VALIDATION-1", null), new UUID(0, 0));
        if ("http".equals(server.getScheme()) && !Set.of("localhost", "127.0.0.1", "[::1]").contains(server.getHost())) throw new ReadFailure(Failure.WRONG_SERVER);
        String verified = verifier.verify(server, token);
        if (verified == null || verified.isBlank()) throw new ReadFailure(Failure.DENIED);
        return new PolarionRestReadSession(server, token, verified);
    }

    /** Production SDK bridge; never calls the dedicated M0 test extension or accepts a claimed username. */
    public static PolarionRestReadSession open(URI server, PolarionToken token) {
        return open(server,token,(configured,credential)->{
            try(var verification=new PolarionRestReadSession(configured,credential,null)) {
                byte[] bytes=verification.get("/sysarch-model/identity",16*1024,true,false);
                JsonNode identity=verification.json.readTree(bytes);
                if(!"sysarch-model-read/1".equals(text(identity,"protocol"))
                    || !"sdk-access-token".equals(text(identity,"authentication"))) throw new ReadFailure(Failure.IDENTITY);
                return text(identity,"actor");
            } catch(ReadFailure failure) { throw failure; }
            catch(Exception failure) { throw new ReadFailure(Failure.IDENTITY); }
        });
    }

    @Override public String verifiedSubject() { return this.token == null ? null : this.subject; }
    @Override public URI configuredServer() { return this.server; }
    @Override public void close() {
        this.token = null;
        this.active.forEach(input -> { try { input.close(); } catch (Exception ignored) { } });
        this.http.shutdownNow();
    }
    @Override public String toString() { return "PolarionRestReadSession[credential=REDACTED]"; }

    public record ModelSummary(Location location, String title) { }
    /** Obtain a real SDK revision after confirming REST current-read permission; timestamps are never used as revisions. */
    public Item pinCurrentRevision(Item current) {
        if(current.revision()!=null) throw new ReadFailure(Failure.SCHEMA);
        requireCurrentRead(current);
        byte[] body=get("/sysarch-model/revision?project="+encode(current.project())+"&workItem="+encode(current.workItem()),16*1024,true,false);
        try {
            JsonNode metadata=this.json.readTree(body);
            if(!"sysarch-model-read/1".equals(text(metadata,"protocol")) || !this.subject.equals(text(metadata,"actor"))
                || !current.project().equals(text(metadata,"project")) || !current.workItem().equals(text(metadata,"workItem"))) throw new ReadFailure(Failure.IDENTITY);
            return new Item(current.project(),current.workItem(),text(metadata,"revision"));
        } catch(ReadFailure failure) { throw failure; }
        catch(Exception failure) { throw new ReadFailure(Failure.SCHEMA); }
    }
    /** A full page may have another page; callers must not treat a bounded page as the whole project. */
    public record ModelPage(List<ModelSummary> models, int page, int pageSize, boolean mayHaveMore) {
        public ModelPage { models = List.copyOf(models); }
    }

    /** Current, caller-authorized model roots only. Never follow server-supplied pagination URLs. */
    public ModelPage models(String project, int page, int pageSize) {
        new Item(project,"validation",null);
        if(page<1 || page>10_000 || pageSize<1 || pageSize>100) throw new IllegalArgumentException("Invalid discovery bounds");
        String fields="id,title,type,sysarchManaged,sysarchSchemaVersion,sysarchModelUuid";
        String path="/projects/"+encode(project)+"/workitems?query="+encode("type:sysarchModel AND sysarchManaged:true")
            +"&sort=id&fields%5Bworkitems%5D="+encode(fields)+"&page%5Bnumber%5D="+page+"&page%5Bsize%5D="+pageSize;
        byte[] body=get(path,PolarionSnapshotLoader.MAX_MANIFEST_BYTES,true);
        try {
            JsonNode document=this.json.readTree(body), data=document.path("data");
            if(!data.isArray() || data.size()>pageSize || document.has("errors")) throw new ReadFailure(Failure.SCHEMA);
            List<ModelSummary> models=new ArrayList<>(); Set<UUID> uuids=new HashSet<>(); Set<String> ids=new HashSet<>();
            for(JsonNode entry:data) {
                String identity=text(entry,"id");
                if(!"workitems".equals(text(entry,"type")) || !identity.startsWith(project+"/")) throw new ReadFailure(Failure.IDENTITY);
                Item item=new Item(project,identity.substring(project.length()+1),null);
                JsonNode attrs=entry.path("attributes");
                if(!"sysarchModel".equals(text(attrs,"type")) || !attrs.path("sysarchManaged").isBoolean()
                    || !attrs.path("sysarchManaged").booleanValue() || !ModelCodec.SCHEMA.equals(text(attrs,"sysarchSchemaVersion"))) throw new ReadFailure(Failure.SCHEMA);
                UUID model=uuid(attrs,"sysarchModelUuid");
                if(!ids.add(identity) || !uuids.add(model)) throw new ReadFailure(Failure.IDENTITY);
                models.add(new ModelSummary(new Location(this.server,item,model),text(attrs,"title")));
            }
            // Sparse/short pages may still have a next page if the server clamps its page-size limit.
            JsonNode next=document.path("links").path("next");
            boolean more=(next.isTextual() && !next.textValue().isBlank()) || data.size()==pageSize;
            if(data.isEmpty() && more) throw new ReadFailure(Failure.SCHEMA);
            return new ModelPage(models,page,pageSize,more);
        } catch(ReadFailure failure) { throw failure; }
        catch(Exception failure) { throw new ReadFailure(Failure.SCHEMA); }
    }

    @Override
    public void requireCurrentRead(Item item) {
        if (item.revision() != null) throw new ReadFailure(Failure.SCHEMA);
        resource(item, "id");
    }

    /** Exact-scope candidate discovery; a search miss never proves transaction rollback. */
    Optional<Item> findCommandRecord(String project,UUID modelUuid,UUID commandId) {
        new Item(project,"validation",null);Objects.requireNonNull(modelUuid);Objects.requireNonNull(commandId);
        String fields="id,type,sysarchManaged,sysarchSchemaVersion,sysarchModelUuid,sysarchCommandId";
        String query="type:sysarchCommit AND sysarchManaged:true AND sysarchModelUuid:\""+modelUuid+"\" AND sysarchCommandId:\""+commandId+"\"";
        String path="/projects/"+encode(project)+"/workitems?query="+encode(query)+"&sort=id&fields%5Bworkitems%5D="+encode(fields)+"&page%5Bnumber%5D=1&page%5Bsize%5D=2";
        try {
            JsonNode document=json.readTree(get(path,PolarionSnapshotLoader.MAX_MANIFEST_BYTES,true)),rows=document.path("data"),next=document.path("links").path("next");
            if(!rows.isArray() || document.has("errors")) throw new ReadFailure(Failure.SCHEMA);
            if(rows.size()>1 || hasNextPage(next)) throw new ReadFailure(Failure.INCOMPLETE);
            if(rows.isEmpty()) return Optional.empty();
            JsonNode row=rows.get(0),attrs=row.path("attributes");String identity=text(row,"id");
            if(!"workitems".equals(text(row,"type")) || !identity.startsWith(project+"/")) throw new ReadFailure(Failure.IDENTITY);
            requireCommitIdentity(attrs,modelUuid,commandId);
            return Optional.of(new Item(project,identity.substring(project.length()+1),null));
        } catch(ReadFailure failure) {throw failure;}
        catch(Exception failure) {throw new ReadFailure(Failure.SCHEMA);}
    }

    public static final String COMMAND_RECEIPT_ATTACHMENT="sysarch-command-receipt.json";
    /** Reads bounded machine metadata from a fixed attachment at an SDK-pinned revision. Not a transaction attestation. */
    Receipt commandReceipt(Item pinned,UUID modelUuid,UUID commandId) {
        if(pinned.revision()==null) throw new ReadFailure(Failure.SCHEMA);
        requireCurrentRead(pinned.current());
        JsonNode attrs=resource(pinned,"type,sysarchManaged,sysarchSchemaVersion,sysarchModelUuid,sysarchCommandId,sysarchCommitId,sysarchRequestHash,sysarchManifestHash").path("attributes");
        requireCommitIdentity(attrs,modelUuid,commandId);
        String attachmentId=receiptAttachmentId(pinned);
        byte[] bytes=get(itemPath(pinned)+"/attachments/"+encode(attachmentId)+"/content"+query(pinned,null),CommandReceiptCodec.MAX_BYTES,false);
        Receipt receipt;
        try {receipt=new CommandReceiptCodec().read(bytes);}
        catch(CommandReceiptCodec.InvalidReceipt failure) {throw new ReadFailure(Failure.SCHEMA);}
        if(!modelUuid.equals(receipt.modelUuid()) || !commandId.equals(receipt.commandId()) || !uuid(attrs,"sysarchCommitId").equals(receipt.commitId())
            || !text(attrs,"sysarchRequestHash").equals(receipt.requestHash()) || !text(attrs,"sysarchManifestHash").equals(receipt.manifestHash())) throw new ReadFailure(Failure.IDENTITY);
        return receipt;
    }
    private String receiptAttachmentId(Item pinned) {
        String path=itemPath(pinned)+"/attachments"+query(pinned,null)
            +"&fields%5Bworkitem_attachments%5D=fileName&page%5Bsize%5D=16";
        byte[] bytes=get(path,CommandReceiptCodec.MAX_BYTES,true);
        try {
            JsonNode document=json.readTree(bytes), rows=document.path("data");
            if(!rows.isArray() || document.has("errors")) throw new ReadFailure(Failure.SCHEMA);
            if(rows.size()>=16 || hasNextPage(document.path("links").path("next"))) throw new ReadFailure(Failure.INCOMPLETE);
            String prefix=pinned.project()+"/"+pinned.workItem()+"/", found=null;
            for(JsonNode row:rows) {
                String identity=text(row,"id");
                if(!"workitem_attachments".equals(text(row,"type")) || !identity.startsWith(prefix)) throw new ReadFailure(Failure.IDENTITY);
                if(!COMMAND_RECEIPT_ATTACHMENT.equals(text(row.path("attributes"),"fileName"))) continue;
                if(found!=null) throw new ReadFailure(Failure.INCOMPLETE);
                found=identity.substring(prefix.length());
                if(!found.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,254}")) throw new ReadFailure(Failure.IDENTITY);
            }
            if(found==null) throw new ReadFailure(Failure.INCOMPLETE);
            return found;
        } catch(ReadFailure failure) {throw failure;}
        catch(Exception failure) {throw new ReadFailure(Failure.SCHEMA);}
    }
    private static boolean hasNextPage(JsonNode next) {
        return !next.isMissingNode() && !next.isNull() && !(next.isTextual() && next.textValue().isBlank());
    }
    private static void requireCommitIdentity(JsonNode attrs,UUID modelUuid,UUID commandId) {
        if(!"sysarchCommit".equals(text(attrs,"type")) || !attrs.path("sysarchManaged").isBoolean() || !attrs.path("sysarchManaged").booleanValue()
            || !ModelCodec.SCHEMA.equals(text(attrs,"sysarchSchemaVersion"))) throw new ReadFailure(Failure.SCHEMA);
        if(!modelUuid.equals(uuid(attrs,"sysarchModelUuid")) || !commandId.equals(uuid(attrs,"sysarchCommandId"))) throw new ReadFailure(Failure.IDENTITY);
    }

    @Override
    public Head head(Item item) {
        JsonNode attrs = resource(item, "type,sysarchManaged,sysarchSchemaVersion,sysarchModelUuid,sysarchHeadVersion,sysarchHeadCommitId,"
                + "sysarchHeadManifestAttachment,sysarchHeadManifestHash,sysarchHeadManifestBytes").path("attributes");
        if (!"sysarchModel".equals(text(attrs, "type")) || !attrs.path("sysarchManaged").isBoolean()
                || !attrs.path("sysarchManaged").booleanValue() || !ModelCodec.SCHEMA.equals(text(attrs, "sysarchSchemaVersion"))) throw new ReadFailure(Failure.SCHEMA);
        return new Head(uuid(attrs, "sysarchModelUuid"), number(attrs, "sysarchHeadVersion"), uuid(attrs, "sysarchHeadCommitId"),
                new Attachment(item, text(attrs, "sysarchHeadManifestAttachment"), text(attrs, "sysarchHeadManifestHash"), number(attrs, "sysarchHeadManifestBytes")));
    }

    @Override
    public ElementIdentity element(Item item) {
        JsonNode attrs = resource(item, "type,sysarchManaged,sysarchModelUuid,sysarchElementUuid,sysarchElementKind").path("attributes");
        if (!Set.of("sysarchElement", "sysarchRelation", "sysarchDiagram").contains(text(attrs, "type"))
                || !attrs.path("sysarchManaged").isBoolean() || !attrs.path("sysarchManaged").booleanValue()) throw new ReadFailure(Failure.SCHEMA);
        return new ElementIdentity(uuid(attrs, "sysarchModelUuid"), uuid(attrs, "sysarchElementUuid"), text(attrs, "sysarchElementKind"));
    }

    @Override
    public RequirementText requirement(Item item) {
        if (item.revision() == null) throw new ReadFailure(Failure.SCHEMA);
        JsonNode attrs = resource(item, "type,title,description").path("attributes");
        if (!"requirement".equals(text(attrs, "type"))) throw new ReadFailure(Failure.SCHEMA);
        JsonNode description = attrs.path("description");
        String content;
        if (description.isMissingNode() || description.isNull()) content = "";
        else if (description.isTextual()) content = description.textValue();
        else if (description.isObject() && description.path("value").isTextual() && description.path("type").isTextual()) {
            content = PolarionText.toPlain(description.path("type").textValue(), description.path("value").textValue());
        } else throw new ReadFailure(Failure.SCHEMA);
        return new RequirementText(text(attrs, "title"), content, item.workItem());
    }

    @Override
    public byte[] attachment(Attachment attachment, int maxBytes) {
        if (maxBytes <= 0 || maxBytes > ModelCodec.MAX_BYTES || attachment.bytes() > maxBytes) throw new ReadFailure(Failure.INTEGRITY);
        String path = itemPath(attachment.owner()) + "/attachments/" + encode(attachment.attachmentId()) + "/content";
        return get(path + query(attachment.owner(), null), maxBytes, false);
    }

    private JsonNode resource(Item item, String fields) {
        byte[] body = get(itemPath(item) + query(item, fields), PolarionSnapshotLoader.MAX_MANIFEST_BYTES, true);
        try {
            JsonNode root = this.json.readTree(body);
            JsonNode data = root.path("data");
            if (!data.isObject() || !"workitems".equals(data.path("type").asText())
                    || !(item.project() + "/" + item.workItem()).equals(data.path("id").asText())) throw new ReadFailure(Failure.IDENTITY);
            return data;
        } catch (ReadFailure failure) { throw failure; }
        catch (Exception e) { throw new ReadFailure(Failure.SCHEMA); }
    }

    private byte[] get(String pathAndQuery, int maximum, boolean jsonExpected) {
        return get(pathAndQuery,maximum,jsonExpected,true);
    }

    private byte[] get(String pathAndQuery, int maximum, boolean jsonExpected, boolean rest) {
        if (this.token == null) throw new ReadFailure(Failure.DENIED);
        String root = this.server.toString().replaceAll("/+$", "") + (rest ? "/rest/v1" : "");
        try {
            long started = System.nanoTime();
            HttpRequest request = HttpRequest.newBuilder(URI.create(root + pathAndQuery)).timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + this.token.value()).header("Accept", jsonExpected ? "application/json" : "application/octet-stream").GET().build();
            HttpResponse<InputStream> response = this.http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                this.active.add(input);
                if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 404) throw new ReadFailure(Failure.DENIED);
                if (response.statusCode() >= 300 && response.statusCode() < 400) throw new ReadFailure(Failure.WRONG_SERVER);
                if (response.statusCode() == 429 || response.statusCode() >= 500) throw new ReadFailure(Failure.UNAVAILABLE);
                if (response.statusCode() != 200) throw new ReadFailure(Failure.SCHEMA);
                if (jsonExpected && !response.headers().firstValue("Content-Type").orElse("").toLowerCase(java.util.Locale.ROOT).contains("json")) throw new ReadFailure(Failure.SCHEMA);
                long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (declared > maximum) throw new ReadFailure(Failure.INTEGRITY);
                long remaining = REQUEST_TIMEOUT.toNanos() - (System.nanoTime() - started);
                if (remaining <= 0) throw new ReadFailure(Failure.UNAVAILABLE);
                var expired = new java.util.concurrent.atomic.AtomicBoolean();
                var deadline = DEADLINES.schedule(() -> {
                    expired.set(true); try { input.close(); } catch (Exception ignored) { }
                }, remaining, TimeUnit.NANOSECONDS);
                try {
                    byte[] bytes = input.readNBytes(maximum + 1);
                    if (expired.get()) throw new ReadFailure(Failure.UNAVAILABLE);
                    if (bytes.length > maximum) throw new ReadFailure(Failure.INTEGRITY);
                    return bytes;
                } finally { deadline.cancel(false); }
            } finally {
                this.active.remove(response.body());
            }
        } catch (ReadFailure failure) { throw failure; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ReadFailure(Failure.UNAVAILABLE); }
        catch (Exception e) { throw new ReadFailure(Failure.UNAVAILABLE); }
    }

    private static String itemPath(Item item) { return "/projects/" + encode(item.project()) + "/workitems/" + encode(item.workItem()); }
    private static String query(Item item, String fields) {
        String params = fields == null ? "" : "fields%5Bworkitems%5D=" + encode(fields);
        if (item.revision() != null) params += (params.isEmpty() ? "" : "&") + "revision=" + encode(item.revision());
        return params.isEmpty() ? "" : "?" + params;
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String text(JsonNode attrs, String field) {
        JsonNode value = attrs.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) throw new ReadFailure(Failure.SCHEMA);
        return value.textValue();
    }
    private static UUID uuid(JsonNode attrs, String field) {
        try {
            String value = text(attrs, field);
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equalsIgnoreCase(value)) throw new ReadFailure(Failure.SCHEMA);
            return parsed;
        }
        catch (IllegalArgumentException e) { throw new ReadFailure(Failure.SCHEMA); }
    }
    private static long number(JsonNode attrs, String field) {
        JsonNode value = attrs.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) throw new ReadFailure(Failure.SCHEMA);
        return value.longValue();
    }
}

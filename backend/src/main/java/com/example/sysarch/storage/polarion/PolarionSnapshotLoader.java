package com.example.sysarch.storage.polarion;

import com.example.sysarch.model.Requirement;
import com.example.sysarch.storage.ModelCodec;
import com.example.sysarch.storage.StoredModel;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.eclipse.emf.ecore.EObject;

/** Internal M2 read pipeline: no writes, persistent cache, Sirius publication or credential storage. */
public final class PolarionSnapshotLoader {
    public static final int MAX_MANIFEST_BYTES = 2 * 1024 * 1024;
    private final ModelCodec codec;
    private final java.util.function.BiPredicate<String,String> requirementProjects;
    private final com.fasterxml.jackson.databind.ObjectMapper json = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public enum Failure { DENIED, WRONG_SERVER, IDENTITY, INTEGRITY, SCHEMA, INCOMPLETE, EXTERNAL_REQUIREMENT_BODY, UNAVAILABLE }
    public static final class ReadFailure extends IllegalArgumentException {
        private final Failure failure;
        public ReadFailure(Failure failure) { super("Model snapshot read rejected: " + failure.name()); this.failure = failure; }
        public Failure failure() { return this.failure; }
    }

    public record Loaded(PolarionSnapshotManifest.Head head, PolarionSnapshotManifest manifest, ModelCodec.Decoded model, String subject) { }

    public PolarionSnapshotLoader(ModelCodec codec) { this(codec,String::equals); }
    public PolarionSnapshotLoader(ModelCodec codec,java.util.function.BiPredicate<String,String> requirementProjects) {
        this.codec=Objects.requireNonNull(codec); this.requirementProjects=Objects.requireNonNull(requirementProjects);
    }

    public Loaded load(PolarionSnapshotManifest.Location location, PolarionModelReadSession session) {
        if (session == null || session.verifiedSubject() == null || session.verifiedSubject().isBlank()) reject(Failure.DENIED);
        String subject = session.verifiedSubject();
        if (!location.server().equals(session.configuredServer())) reject(Failure.WRONG_SERVER);
        session.requireCurrentRead(location.root().current());
        var head = session.head(location.root());
        if (head == null || !location.modelUuid().equals(head.modelUuid())) reject(Failure.IDENTITY);
        sameProject(location.root().project(), head.manifest().owner());
        session.requireCurrentRead(head.manifest().owner().current());
        byte[] manifestBytes = checkedBytes(head.manifest(), session, MAX_MANIFEST_BYTES);
        PolarionSnapshotManifest manifest;
        try { manifest = this.json.readValue(manifestBytes, PolarionSnapshotManifest.class); }
        catch (Exception e) { throw new ReadFailure(Failure.SCHEMA); }
        if (manifest == null || !ModelCodec.SCHEMA.equals(manifest.schemaVersion())) reject(Failure.SCHEMA);
        if (!head.modelUuid().equals(manifest.modelUuid()) || head.version() != manifest.version() || !head.commitId().equals(manifest.commitId())) reject(Failure.IDENTITY);
        sameProject(location.root().project(), manifest.snapshot().owner());

        Set<UUID> bindingIds = new HashSet<>();
        Set<String> workItems = new HashSet<>();
        for (var binding : manifest.elements()) {
            sameProject(location.root().project(), binding.item());
            if (!bindingIds.add(binding.uuid()) || !workItems.add(binding.item().project() + "/" + binding.item().workItem())) reject(Failure.INCOMPLETE);
            session.requireCurrentRead(binding.item().current());
        }
        Set<UUID> externalIds = new HashSet<>();
        for (var reference : manifest.requirements()) {
            if (!bindingIds.contains(reference.uuid()) || !externalIds.add(reference.uuid())) reject(Failure.INCOMPLETE);
            if (!this.requirementProjects.test(location.root().project(),reference.item().project())) reject(Failure.DENIED);
            session.requireCurrentRead(reference.item().current());
        }
        session.requireCurrentRead(manifest.snapshot().owner().current());
        // Whole-model and external dependency authorization precede reading any semantic snapshot bytes.
        byte[] snapshotBytes = checkedBytes(manifest.snapshot(), session, ModelCodec.MAX_BYTES);
        StoredModel snapshot;
        ModelCodec.Decoded decoded;
        try {
            snapshot = this.codec.read(snapshotBytes);
            decoded = this.codec.decode(snapshotBytes, manifest.snapshot().sha256());
        } catch (IllegalArgumentException e) { throw new ReadFailure(Failure.SCHEMA); }
        if (!manifest.modelUuid().equals(snapshot.modelUuid())) reject(Failure.IDENTITY);
        Map<UUID, StoredModel.Node> nodes = new HashMap<>();
        snapshot.nodes().forEach(node -> nodes.put(node.uuid(), node));
        if (!nodes.keySet().equals(bindingIds)) reject(Failure.INCOMPLETE);
        for (var binding : manifest.elements()) {
            var identity = session.element(binding.item());
            if (identity == null || !manifest.modelUuid().equals(identity.modelUuid()) || !binding.uuid().equals(identity.elementUuid())
                    || !nodes.get(binding.uuid()).type().equals(identity.kind())) reject(Failure.IDENTITY);
        }

        // A Polarion attachment can be downloaded outside this application. Never embed independently restricted requirement bodies.
        for (var reference : manifest.requirements()) {
            var node = nodes.get(reference.uuid());
            if (!"Requirement".equals(node.type())) reject(Failure.IDENTITY);
            var attributes = node.attributes();
            if (Set.of("text", "description", "reqId", "workItemId").stream().anyMatch(attributes::containsKey)
                    || attributes.containsKey("name") && !attributes.get("name").equals(List.of("Requirement reference"))) {
                reject(Failure.EXTERNAL_REQUIREMENT_BODY);
            }
        }
        Map<UUID, EObject> objects = new HashMap<>();
        for (var document : decoded.documents()) {
            for (var it = document.resource().getAllContents(); it.hasNext();) {
                EObject object = it.next(); objects.put(decoded.identities().id(object), object);
            }
        }
        for (var reference : manifest.requirements()) {
            var text = session.requirement(reference.item());
            if (text == null) reject(Failure.DENIED);
            Requirement requirement = (Requirement) objects.get(reference.uuid());
            requirement.setName(text.title()); requirement.setText(text.text()); requirement.setReqId(text.reqId());
        }
        if (!subject.equals(session.verifiedSubject())) reject(Failure.IDENTITY);
        return new Loaded(head, manifest, decoded, subject);
    }

    private static byte[] checkedBytes(PolarionSnapshotManifest.Attachment ref, PolarionModelReadSession session, int maximum) {
        if (ref.bytes() > maximum) throw new ReadFailure(Failure.INTEGRITY);
        byte[] bytes = session.attachment(ref, maximum);
        if (bytes == null || bytes.length != ref.bytes() || bytes.length > maximum || !ModelCodec.sha256(bytes).equals(ref.sha256())) reject(Failure.INTEGRITY);
        return bytes;
    }

    private static void sameProject(String project, PolarionSnapshotManifest.Item ref) {
        if (!project.equals(ref.project())) reject(Failure.IDENTITY);
    }

    private static void reject(Failure failure) { throw new ReadFailure(failure); }
}

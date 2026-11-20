package com.example.sysarch.storage.polarion;

import static org.junit.jupiter.api.Assertions.*;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.storage.ModelCodec;
import com.example.sysarch.storage.ModelIdentityRegistry;
import com.example.sysarch.storage.StoredModel;
import com.example.sysarch.storage.polarion.PolarionSnapshotLoader.Failure;
import com.example.sysarch.storage.polarion.PolarionSnapshotLoader.ReadFailure;
import com.example.sysarch.storage.polarion.PolarionSnapshotManifest.*;
import com.example.sysarch.storage.polarion.PolarionModelReadSession.ElementIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.Test;

class PolarionSnapshotLoaderTest {
    private static final URI SERVER = URI.create("https://polarion.example/polarion");
    private final ModelCodec codec = new ModelCodec();
    private final PolarionSnapshotLoader loader = new PolarionSnapshotLoader(this.codec,(source,target)->source.equals(target) || source.equals("alpha") && target.equals("beta"));

    @Test
    void rebuildsAConsistentSnapshotInIsolatedResourcesWithoutWriting() throws Exception {
        Fixture f = new Fixture(false);
        var loaded = this.loader.load(f.location, f);
        assertEquals("alice", loaded.subject()); assertEquals(7, loaded.head().version());
        assertArrayEquals(f.snapshotBytes, this.codec.encode(this.codec.capture(f.modelUuid, loaded.model().documents(), loaded.model().identities())));
        assertNotSame(f.resource, loaded.model().documents().getFirst().resource());
        int snapshotRead = f.calls.indexOf("bytes:snapshot.json");
        for (var binding : f.manifest.elements()) assertTrue(f.calls.indexOf("auth:" + key(binding.item())) < snapshotRead);
    }

    @Test
    void anonymousAndWrongServerAreRejectedBeforeAnyRemoteRead() throws Exception {
        Fixture f = new Fixture(false); f.subject = "";
        assertFailure(Failure.DENIED, f); assertTrue(f.calls.isEmpty());
        f.subject = "alice"; f.server = URI.create("https://other.example/polarion");
        assertFailure(Failure.WRONG_SERVER, f); assertTrue(f.calls.isEmpty());
    }

    @Test
    void rootDenialPrecedesManifestRead() throws Exception {
        Fixture f = new Fixture(false); f.denied.add(key(f.root));
        assertFailure(Failure.DENIED, f); assertEquals(List.of("auth:" + key(f.root)), f.calls);
    }

    @Test
    void deniedElementStopsBeforeReadingSemanticBytes() throws Exception {
        Fixture f = new Fixture(false); f.denied.add(key(f.manifest.elements().getLast().item()));
        assertFailure(Failure.DENIED, f); assertFalse(f.calls.contains("bytes:snapshot.json"));
    }

    @Test
    void currentPermissionIsCheckedEvenForHistoricalExternalRequirements() throws Exception {
        Fixture f = new Fixture(true); f.denied.add(key(f.external));
        assertFailure(Failure.DENIED, f); assertFalse(f.calls.contains("bytes:snapshot.json"));
        assertFalse(f.calls.contains("requirement:" + key(f.external)));
    }

    @Test
    void commandPreparationGetsTheAuthorizedAtRestSnapshotWithoutFetchingRequirementBodies() throws Exception {
        Fixture f = new Fixture(true);
        var stored = this.loader.loadStored(f.location, f);
        assertArrayEquals(f.snapshotBytes, this.codec.encode(stored.model()));
        assertEquals(f.manifest.requirements(), stored.manifest().requirements());
        assertTrue(f.calls.contains("auth:" + key(f.external)));
        assertFalse(f.calls.contains("requirement:" + key(f.external)));
        f.denied.add(key(f.external));
        assertEquals(Failure.DENIED, assertThrows(ReadFailure.class, () -> this.loader.loadStored(f.location, f)).failure());
    }
    @Test
    void crossProjectRequirementsNeedExplicitServerPolicyBeforeSemanticRead() throws Exception {
        Fixture f=new Fixture(true);
        assertEquals(Failure.DENIED,assertThrows(ReadFailure.class,()->new PolarionSnapshotLoader(codec).load(f.location,f)).failure());
        assertFalse(f.calls.contains("bytes:snapshot.json")); assertFalse(f.calls.contains("auth:"+key(f.external)));
    }

    @Test
    void badManifestAndSnapshotHashesAreRejected() throws Exception {
        Fixture manifest = new Fixture(false); manifest.manifestBytes[0] ^= 1;
        assertFailure(Failure.INTEGRITY, manifest);
        Fixture snapshot = new Fixture(false); snapshot.snapshotBytes[0] ^= 1;
        assertFailure(Failure.INTEGRITY, snapshot);
    }

    @Test
    void oversizedManifestIsRejectedBeforeTransportAllocation() throws Exception {
        Fixture f = new Fixture(false);
        f.head = new Head(f.modelUuid, 7, f.commitId, new Attachment(f.root, "manifest.json", "0".repeat(64), PolarionSnapshotLoader.MAX_MANIFEST_BYTES + 1));
        assertFailure(Failure.INTEGRITY, f); assertFalse(f.calls.contains("bytes:manifest.json"));
    }

    @Test
    void schemaAndHeadCommitMustAgree() throws Exception {
        Fixture schema = new Fixture(false);
        schema.replace(new PolarionSnapshotManifest("2.0", schema.modelUuid, 7, schema.commitId, schema.manifest.snapshot(), schema.manifest.elements(), List.of()));
        assertFailure(Failure.SCHEMA, schema);
        Fixture commit = new Fixture(false);
        commit.head = new Head(commit.modelUuid, 7, UUID.randomUUID(), commit.head.manifest());
        assertFailure(Failure.IDENTITY, commit);
    }

    @Test
    void duplicateNodeBindingsAndReusedWorkItemsAreRejected() throws Exception {
        Fixture duplicate = new Fixture(false);
        var bindings = new ArrayList<>(duplicate.manifest.elements()); bindings.add(bindings.getFirst());
        duplicate.bindings(bindings); assertFailure(Failure.INCOMPLETE, duplicate);
        Fixture reused = new Fixture(false);
        bindings = new ArrayList<>(reused.manifest.elements());
        bindings.set(1, new Element(bindings.get(1).uuid(), bindings.getFirst().item()));
        reused.bindings(bindings); assertFailure(Failure.INCOMPLETE, reused);
    }

    @Test
    void omittedBindingsCannotSilentlyDropObjects() throws Exception {
        Fixture f = new Fixture(false); var bindings = new ArrayList<>(f.manifest.elements()); bindings.removeLast();
        f.bindings(bindings); assertFailure(Failure.INCOMPLETE, f);
    }

    @Test
    void foreignProjectModelObjectsAndSnapshotOwnersAreRejected() throws Exception {
        Fixture f = new Fixture(false); var bindings = new ArrayList<>(f.manifest.elements());
        bindings.set(0, new Element(bindings.getFirst().uuid(), new Item("beta", "FOREIGN-1", "42")));
        f.bindings(bindings); assertFailure(Failure.IDENTITY, f);
        Fixture owner = new Fixture(false);
        var ref = owner.manifest.snapshot();
        owner.replace(new PolarionSnapshotManifest(ModelCodec.SCHEMA, owner.modelUuid, 7, owner.commitId,
                new Attachment(new Item("beta", "SECRET-1", "42"), ref.attachmentId(), ref.sha256(), ref.bytes()), owner.manifest.elements(), List.of()));
        assertFailure(Failure.IDENTITY, owner);
    }

    @Test
    void pinnedWorkItemIdentityMustMatchSnapshotUuidAndKind() throws Exception {
        Fixture f = new Fixture(false); var item = f.manifest.elements().getFirst().item();
        f.identities.put(key(item), new ElementIdentity(UUID.randomUUID(), f.manifest.elements().getFirst().uuid(), "PartDefinition"));
        assertFailure(Failure.IDENTITY, f);
    }

    @Test
    void snapshotMustBelongToRequestedModel() throws Exception {
        Fixture f = new Fixture(false);
        StoredModel original = this.codec.read(f.snapshotBytes);
        f.snapshotBytes = this.codec.encode(new StoredModel(ModelCodec.SCHEMA, UUID.randomUUID(), original.documents(), original.nodes()));
        f.snapshotReference(); assertFailure(Failure.IDENTITY, f);
    }

    @Test
    void requirementBodiesAreReadOnlyOverlaysAfterDependencyAuthorization() throws Exception {
        Fixture f = new Fixture(true);
        assertFalse(new String(f.snapshotBytes, StandardCharsets.UTF_8).contains("confidential"));
        var loaded = this.loader.load(f.location, f);
        Requirement requirement = null;
        for (var document : loaded.model().documents()) for (var it = document.resource().getAllContents(); it.hasNext();) {
            if (it.next() instanceof Requirement candidate) requirement = candidate;
        }
        assertNotNull(requirement); assertEquals("Authorized title", requirement.getName());
        assertEquals("confidential authorized text", requirement.getText()); assertEquals("REQ-42", requirement.getReqId());
        assertEquals("Requirement reference", f.requirement.getName()); assertNull(f.requirement.getText());
        assertTrue(f.calls.contains("requirement:beta/REQ-42"));
        assertEquals("42", f.lastRequirementRevision);
    }

    @Test
    void externalRequirementTextMustNeverBeEmbeddedInSharedSnapshot() throws Exception {
        Fixture f = new Fixture(true); f.requirement.setText("confidential copied body"); f.capture();
        assertFailure(Failure.EXTERNAL_REQUIREMENT_BODY, f);
        assertFalse(f.calls.contains("requirement:" + key(f.external)));
    }

    @Test
    void malformedAndDuplicateJsonFieldsAreRejectedWithoutExposingContent() throws Exception {
        Fixture f = new Fixture(false);
        f.manifestBytes = "{\"schemaVersion\":\"1.0\",\"schemaVersion\":\"secret\"}".getBytes(StandardCharsets.UTF_8);
        f.headReference();
        ReadFailure failure = assertThrows(ReadFailure.class, () -> this.loader.load(f.location, f));
        assertEquals(Failure.SCHEMA, failure.failure()); assertFalse(failure.getMessage().contains("secret"));
    }

    private void assertFailure(Failure expected, Fixture fixture) {
        assertEquals(expected, assertThrows(ReadFailure.class, () -> this.loader.load(fixture.location, fixture)).failure());
    }
    private static String key(Item item) { return item.project() + "/" + item.workItem(); }

    private final class Fixture implements PolarionModelReadSession {
        final UUID modelUuid = UUID.randomUUID(), commitId = UUID.randomUUID(), documentUuid = UUID.randomUUID();
        final Item root = new Item("alpha", "ROOT-1", "10"), external = new Item("beta", "REQ-42", "42");
        final Location location = new Location(SERVER, this.root, this.modelUuid);
        final Resource resource = new ResourceImpl();
        final ModelIdentityRegistry registry = new ModelIdentityRegistry();
        final Map<String, ElementIdentity> identities = new HashMap<>();
        final List<String> calls = new ArrayList<>(); final Set<String> denied = new HashSet<>();
        String subject = "alice", lastRequirementRevision; URI server = SERVER;
        Requirement requirement;
        StoredModel snapshot;
        PolarionSnapshotManifest manifest; Head head;
        byte[] snapshotBytes, manifestBytes;

        Fixture(boolean externalRequirement) throws Exception {
            var block = SysarchFactory.eINSTANCE.createPartDefinition(); block.setId("PD-001"); block.setName("System");
            var part = SysarchFactory.eINSTANCE.createPart(); part.setId("PT-001"); part.setName("Component"); part.setTypedBy(block);
            block.getOwnedParts().add(part); this.resource.getContents().add(block);
            if (externalRequirement) {
                this.requirement = SysarchFactory.eINSTANCE.createRequirement(); this.requirement.setId("REF-1");
                this.requirement.setName("Requirement reference"); this.requirement.getSatisfiedBy().add(block);
                this.resource.getContents().add(this.requirement);
            }
            capture();
        }

        void capture() throws Exception {
            this.snapshot = codec.capture(this.modelUuid, List.of(new ModelCodec.SourceDocument(this.documentUuid, this.resource)), this.registry);
            this.snapshotBytes = codec.encode(this.snapshot);
            List<Element> bindings = new ArrayList<>(); int index = 0;
            for (var node : this.snapshot.nodes()) {
                Item item = new Item("alpha", "E-" + index++, "10"); bindings.add(new Element(node.uuid(), item));
                this.identities.put(key(item), new ElementIdentity(this.modelUuid, node.uuid(), node.type()));
            }
            List<RequirementReference> refs = this.requirement == null ? List.of() : List.of(new RequirementReference(this.registry.id(this.requirement), this.external));
            replace(new PolarionSnapshotManifest(ModelCodec.SCHEMA, this.modelUuid, 7, this.commitId,
                    new Attachment(this.root, "snapshot.json", ModelCodec.sha256(this.snapshotBytes), this.snapshotBytes.length), bindings, refs));
        }
        void bindings(List<Element> bindings) throws Exception {
            replace(new PolarionSnapshotManifest(ModelCodec.SCHEMA, this.modelUuid, 7, this.commitId, this.manifest.snapshot(), bindings, this.manifest.requirements()));
        }
        void snapshotReference() throws Exception {
            replace(new PolarionSnapshotManifest(ModelCodec.SCHEMA, this.modelUuid, 7, this.commitId,
                    new Attachment(this.root, "snapshot.json", ModelCodec.sha256(this.snapshotBytes), this.snapshotBytes.length), this.manifest.elements(), this.manifest.requirements()));
        }
        void replace(PolarionSnapshotManifest manifest) throws Exception {
            this.manifest = manifest; this.manifestBytes = new ObjectMapper().writeValueAsBytes(manifest); headReference();
        }
        void headReference() { this.head = new Head(this.modelUuid, 7, this.commitId, new Attachment(this.root, "manifest.json", ModelCodec.sha256(this.manifestBytes), this.manifestBytes.length)); }
        public String verifiedSubject() { return this.subject; }
        public URI configuredServer() { return this.server; }
        public void requireCurrentRead(Item item) {
            assertNull(item.revision()); this.calls.add("auth:" + key(item)); if (this.denied.contains(key(item))) throw new ReadFailure(Failure.DENIED);
        }
        public Head head(Item item) { this.calls.add("head"); return this.head; }
        public byte[] attachment(Attachment ref, int max) {
            this.calls.add("bytes:" + ref.attachmentId());
            assertTrue(max <= ModelCodec.MAX_BYTES);
            return ref.attachmentId().equals("manifest.json") ? this.manifestBytes : this.snapshotBytes;
        }
        public ElementIdentity element(Item item) { this.calls.add("identity:" + key(item)); return this.identities.get(key(item)); }
        public RequirementText requirement(Item item) {
            this.calls.add("requirement:" + key(item)); this.lastRequirementRevision = item.revision();
            return new RequirementText("Authorized title", "confidential authorized text", "REQ-42");
        }
    }
}

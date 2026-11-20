package com.example.sysarch.storage.polarion;

import com.example.sysarch.storage.ModelCodec;
import com.example.sysarch.storage.StoredModel;
import com.example.sysarch.storage.command.CommandCandidate;
import com.example.sysarch.storage.command.ModelCommandProtocol.BoundCommand;
import com.example.sysarch.storage.polarion.PolarionSnapshotManifest.Element;
import com.example.sysarch.storage.polarion.PolarionSnapshotManifest.Head;
import com.example.sysarch.storage.polarion.PolarionSnapshotManifest.Item;
import com.example.sysarch.storage.polarion.PolarionSnapshotManifest.Location;
import com.example.sysarch.storage.polarion.PolarionSnapshotManifest.RequirementReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds write preconditions from Polarion reads. A future SDK transaction must recheck permissions and revisions. */
public final class PolarionCommandPreparation {
    private static final int MAX_ELEMENT_PAYLOAD_BYTES = 64 * 1024;
    private final PolarionSnapshotLoader loader;
    private final CommandCandidate candidate;
    private final JsonMapper json = JsonMapper.builder().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY).build();

    public enum Failure { UNVERSIONED_BINDING, EXTERNAL_CHANGE, NO_CHANGE, PAYLOAD_TOO_LARGE }

    public static final class Rejected extends IllegalStateException {
        private final Failure failure;
        Rejected(Failure failure) {
            super("Polarion command preparation rejected: " + failure);
            this.failure = failure;
        }
        public Failure failure() { return failure; }
    }

    public record ElementChange(UUID elementUuid, Item expectedItem, String payload) { }

    /** Internal state only. Contains model content and must not be logged or returned to an unverified actor. */
    public record Plan(BoundCommand command, Location location, Item expectedRoot, Head expectedHead,
                       List<Element> expectedBindings, List<RequirementReference> requirements,
                       List<ElementChange> changes, StoredModel snapshot) {
        public Plan {
            expectedBindings = List.copyOf(expectedBindings);
            requirements = List.copyOf(requirements);
            changes = List.copyOf(changes);
        }
        @Override public String toString() { return "PolarionCommandPlan[command=" + command + ", changes=" + changes.size() + "]"; }
    }

    public PolarionCommandPreparation(PolarionSnapshotLoader loader, CommandCandidate candidate) {
        this.loader = Objects.requireNonNull(loader);
        this.candidate = Objects.requireNonNull(candidate);
    }

    public Plan prepare(Location location, BoundCommand command, PolarionRestReadSession session) {
        if (!command.actor().equals(session.verifiedSubject())) throw new PolarionSnapshotLoader.ReadFailure(PolarionSnapshotLoader.Failure.DENIED);
        if (location.root().revision() != null || !location.modelUuid().equals(command.modelUuid())
                || !location.server().equals(session.configuredServer())) {
            throw new PolarionSnapshotLoader.ReadFailure(PolarionSnapshotLoader.Failure.IDENTITY);
        }

        Item root = session.pinCurrentRevision(location.root());
        PolarionSnapshotLoader.Stored stored = loader.loadStored(location, session);
        var manifest = stored.manifest();
        var head = stored.head();
        if (head.version() != command.body().expectedVersion()) throw new CommandCandidate.VersionConflict();
        requireRootOwnership(location.root(), head, manifest);

        Map<UUID, Item> bindings = manifest.elements().stream().collect(Collectors.toMap(Element::uuid, Element::item));
        for (Item baseline : bindings.values()) {
            if (baseline.revision() == null) throw new Rejected(Failure.UNVERSIONED_BINDING);
            if (!baseline.equals(session.pinCurrentRevision(baseline.current()))) throw new Rejected(Failure.EXTERNAL_CHANGE);
        }
        var external = manifest.requirements().stream().map(RequirementReference::uuid).collect(Collectors.toUnmodifiableSet());
        StoredModel next = candidate.prepare(stored.model(), head.version(), external, command);
        List<ElementChange> changes = changedElements(stored.model(), next, bindings);
        if (changes.isEmpty()) throw new Rejected(Failure.NO_CHANGE);

        if (!head.equals(session.head(location.root())) || !root.equals(session.pinCurrentRevision(location.root()))) {
            throw new Rejected(Failure.EXTERNAL_CHANGE);
        }
        if (!stored.subject().equals(session.verifiedSubject())) throw new PolarionSnapshotLoader.ReadFailure(PolarionSnapshotLoader.Failure.IDENTITY);
        return new Plan(command, location, root, head, manifest.elements(), manifest.requirements(), changes, next);
    }

    private List<ElementChange> changedElements(StoredModel base, StoredModel next, Map<UUID, Item> bindings) {
        Map<UUID, StoredModel.Node> original = base.nodes().stream().collect(Collectors.toMap(StoredModel.Node::uuid, Function.identity()));
        List<ElementChange> changes = new ArrayList<>();
        for (var node : next.nodes()) {
            if (node.equals(original.get(node.uuid()))) continue;
            try {
                byte[] bytes = json.writeValueAsBytes(node);
                if (bytes.length > MAX_ELEMENT_PAYLOAD_BYTES) throw new Rejected(Failure.PAYLOAD_TOO_LARGE);
                changes.add(new ElementChange(node.uuid(), Objects.requireNonNull(bindings.get(node.uuid())),
                    new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
            } catch (JsonProcessingException failure) {
                throw new PolarionSnapshotLoader.ReadFailure(PolarionSnapshotLoader.Failure.SCHEMA);
            }
        }
        return changes;
    }

    private static void requireRootOwnership(Item root, Head head, PolarionSnapshotManifest manifest) {
        if (!root.equals(head.manifest().owner().current()) || !root.equals(manifest.snapshot().owner().current())
                || manifest.elements().stream().anyMatch(binding -> root.equals(binding.item().current()))) {
            throw new PolarionSnapshotLoader.ReadFailure(PolarionSnapshotLoader.Failure.IDENTITY);
        }
    }
}

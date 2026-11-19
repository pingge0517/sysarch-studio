package com.example.sysarch.storage.polarion;

import com.example.sysarch.storage.ModelCodec;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Public snapshot metadata, never credentials or requirement bodies. Revision null means an immutable hash-addressed artifact. */
public record PolarionSnapshotManifest(String schemaVersion, UUID modelUuid, long version, UUID commitId,
        Attachment snapshot, List<Element> elements, List<RequirementReference> requirements) {
    public PolarionSnapshotManifest {
        Objects.requireNonNull(schemaVersion); Objects.requireNonNull(modelUuid); Objects.requireNonNull(commitId);
        Objects.requireNonNull(snapshot);
        if (version < 0) throw new IllegalArgumentException("Negative model version");
        elements = List.copyOf(elements); requirements = List.copyOf(requirements);
    }

    public record Item(String project, String workItem, String revision) {
        public Item {
            if (project == null || !project.matches("[A-Za-z0-9_-]{1,128}") || workItem == null || !workItem.matches("[A-Za-z0-9_-]{1,128}")) {
                throw new IllegalArgumentException("Invalid item identity");
            }
            if (revision != null && !revision.matches("[0-9]{1,20}")) throw new IllegalArgumentException("Invalid item revision");
        }
        public Item current() { return new Item(project, workItem, null); }
    }

    public record Attachment(Item owner, String attachmentId, String sha256, long bytes) {
        public Attachment {
            Objects.requireNonNull(owner);
            if (attachmentId == null || attachmentId.isBlank() || attachmentId.length() > 255
                    || attachmentId.matches(".*[/\\\\%?#\\p{Cntrl}].*") || attachmentId.equals(".") || attachmentId.equals("..")) {
                throw new IllegalArgumentException("Invalid attachment identity");
            }
            if (sha256 == null || !sha256.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid digest");
            if (bytes <= 0 || bytes > ModelCodec.MAX_BYTES) throw new IllegalArgumentException("Invalid attachment size");
        }
    }

    public record Element(UUID uuid, Item item) {
        public Element { Objects.requireNonNull(uuid); Objects.requireNonNull(item); }
    }

    public record RequirementReference(UUID uuid, Item item) {
        public RequirementReference {
            Objects.requireNonNull(uuid); Objects.requireNonNull(item);
            if (item.revision() == null) throw new IllegalArgumentException("External requirements need a pinned revision");
        }
    }

    public record Location(URI server, Item root, UUID modelUuid) {
        public Location {
            Objects.requireNonNull(server); Objects.requireNonNull(root); Objects.requireNonNull(modelUuid);
            if (!("http".equals(server.getScheme()) || "https".equals(server.getScheme())) || server.getHost() == null
                    || server.getUserInfo() != null || server.getQuery() != null || server.getFragment() != null) {
                throw new IllegalArgumentException("Invalid configured server");
            }
        }
    }

    public record Head(UUID modelUuid, long version, UUID commitId, Attachment manifest) {
        public Head {
            Objects.requireNonNull(modelUuid); Objects.requireNonNull(commitId); Objects.requireNonNull(manifest);
            if (version < 0) throw new IllegalArgumentException("Negative head version");
        }
    }
}

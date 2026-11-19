package com.example.sysarch.storage.polarion;

import java.net.URI;
import java.util.UUID;

/**
 * Request-scoped port, constructed only after verifying the caller's identity and credential ownership.
 * Implementations must use that same user's authorization for EVERY call and enforce byte limits while streaming.
 * This is not an HTTP input DTO, an impersonation mechanism, or a singleton cache.
 */
public interface PolarionModelReadSession {
    String verifiedSubject();
    URI configuredServer();
    void requireCurrentRead(PolarionSnapshotManifest.Item item);
    PolarionSnapshotManifest.Head head(PolarionSnapshotManifest.Item root);
    byte[] attachment(PolarionSnapshotManifest.Attachment attachment, int maxBytes);
    ElementIdentity element(PolarionSnapshotManifest.Item item);
    RequirementText requirement(PolarionSnapshotManifest.Item item);

    record ElementIdentity(UUID modelUuid, UUID elementUuid, String kind) { }
    record RequirementText(String title, String text, String reqId) { }
}

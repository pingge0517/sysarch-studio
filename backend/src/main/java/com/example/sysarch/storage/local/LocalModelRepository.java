package com.example.sysarch.storage.local;

import com.example.sysarch.access.ProjectReadAccess;
import com.example.sysarch.identity.Principal;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.storage.ModelCodec;
import com.example.sysarch.storage.ModelIdentityRegistry;
import com.example.sysarch.storage.ModelRepository;
import com.example.sysarch.storage.ModelStorageMode;
import com.example.sysarch.storage.ModelStorageRegistry;
import com.example.sysarch.storage.StoredModel;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Read-only adapter to existing Sirius persistence; all model access stays on the editing context thread. */
public final class LocalModelRepository implements ModelRepository {
    private final ProjectModels projects;
    private final ProjectReadAccess access;
    private final ModelCodec codec;
    private final ModelStorageRegistry registry;

    public LocalModelRepository(ProjectModels projects, ProjectReadAccess access, ModelCodec codec) {
        this(projects, access, codec, null);
    }

    public LocalModelRepository(ProjectModels projects, ProjectReadAccess access, ModelCodec codec, ModelStorageRegistry registry) {
        this.projects = Objects.requireNonNull(projects);
        this.access = Objects.requireNonNull(access);
        this.codec = Objects.requireNonNull(codec);
        this.registry = registry;
    }

    @Override
    public ModelStorageMode mode() { return ModelStorageMode.LOCAL; }

    @Override
    public StoredModel capture(String project, Principal principal, ModelIdentityRegistry identities) {
        if (principal == null) throw new ProjectReadAccess.Denied();
        String resolved = this.projects.siriusProjectId(project);
        this.access.require(principal, resolved);
        var registration = this.registry == null ? java.util.Optional.<ModelStorageRegistry.Registration>empty()
                : this.registry.find(resolved, principal);
        if (registration.isPresent() && registration.get().mode() != ModelStorageMode.LOCAL) {
            throw new IllegalStateException("POLARION models cannot be captured from LOCAL persistence");
        }
        UUID modelUuid = registration.map(ModelStorageRegistry.Registration::modelUuid).orElseGet(() -> UUID.fromString(resolved));
        return this.projects.read(resolved, resources -> this.codec.capture(modelUuid,
                resources.stream().map(r -> {
                    if (r.getURI() == null) throw new ModelCodec.InvalidSnapshot("Document has no stable URI");
                    // Document identity is independent of its display name or machine-local export path.
                    UUID document = UUID.nameUUIDFromBytes((resolved + "\n" + r.getURI()).getBytes(StandardCharsets.UTF_8));
                    return new ModelCodec.SourceDocument(document, r);
                }).toList(), identities));
    }
}

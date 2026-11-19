package com.example.sysarch.storage;

import com.example.sysarch.identity.Principal;
import java.util.Objects;

/** Internal M1 routing seam. A POLARION registration must never silently use local persistence. */
public final class ModelRepositoryRouter {
    private final ModelStorageRegistry registry;
    private final ModelRepository local;

    public ModelRepositoryRouter(ModelStorageRegistry registry, ModelRepository local) {
        this.registry = Objects.requireNonNull(registry);
        this.local = Objects.requireNonNull(local);
        if (local.mode() != ModelStorageMode.LOCAL) throw new IllegalArgumentException("Expected LOCAL adapter");
    }

    public ModelRepository repository(String siriusProject, Principal principal) {
        if (this.registry.mode(siriusProject, principal) == ModelStorageMode.LOCAL) return this.local;
        throw new IllegalStateException("POLARION repository is not available until M0/M2 validation; no LOCAL fallback");
    }
}

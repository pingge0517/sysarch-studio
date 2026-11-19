package com.example.sysarch.storage;

import com.example.sysarch.identity.Principal;

/** M1 read seam. Command/version contracts are deliberately deferred until M0 proves remote atomicity. */
public interface ModelRepository {
    ModelStorageMode mode();

    /** Caller retains the registry only for this editing context; snapshot UUIDs survive decode/recapture. */
    StoredModel capture(String project, Principal principal, ModelIdentityRegistry identities);
}

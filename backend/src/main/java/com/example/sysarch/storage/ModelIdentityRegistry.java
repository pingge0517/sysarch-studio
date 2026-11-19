package com.example.sysarch.storage;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.eclipse.emf.ecore.EObject;

/** Session-local identities, persisted by ModelCodec, never derived from mutable business identifiers. */
public final class ModelIdentityRegistry {
    private final Map<EObject, UUID> identities = new IdentityHashMap<>();
    private final Map<UUID, EObject> objects = new java.util.HashMap<>();

    public UUID id(EObject object) {
        Objects.requireNonNull(object);
        UUID id = this.identities.get(object);
        if (id == null) {
            do { id = UUID.randomUUID(); } while (this.objects.containsKey(id));
            register(object, id);
        }
        return id;
    }

    public void register(EObject object, UUID id) {
        Objects.requireNonNull(object);
        Objects.requireNonNull(id);
        UUID existing = this.identities.get(object);
        if (existing != null && !existing.equals(id)) {
            throw new IllegalArgumentException("An object cannot change its stable identity");
        }
        if (this.objects.containsKey(id) && this.objects.get(id) != object) {
            throw new IllegalArgumentException("Duplicate model UUID");
        }
        this.identities.put(object, id);
        this.objects.put(id, object);
    }
}

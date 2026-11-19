package com.example.sysarch.storage;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/** Immutable wire format. Every EObject, including ConstraintExpr, receives its own UUID. */
public record StoredModel(String schemaVersion, UUID modelUuid, List<Document> documents, List<Node> nodes) {
    public StoredModel {
        Objects.requireNonNull(schemaVersion);
        Objects.requireNonNull(modelUuid);
        documents = List.copyOf(documents);
        nodes = List.copyOf(nodes);
    }

    public record Document(UUID documentUuid, List<UUID> roots) {
        public Document {
            Objects.requireNonNull(documentUuid);
            roots = List.copyOf(roots);
        }
    }

    /** Attributes are EMF datatype lexical strings. Reference lists preserve Ecore ordering. */
    public record Node(UUID uuid, String type, Map<String, List<String>> attributes, Map<String, List<UUID>> references) {
        public Node {
            Objects.requireNonNull(uuid);
            Objects.requireNonNull(type);
            attributes = immutable(attributes);
            references = immutable(references);
        }

        private static <T> Map<String, List<T>> immutable(Map<String, List<T>> input) {
            TreeMap<String, List<T>> result = new TreeMap<>();
            input.forEach((key, value) -> result.put(Objects.requireNonNull(key), List.copyOf(value)));
            return Collections.unmodifiableMap(result);
        }
    }
}

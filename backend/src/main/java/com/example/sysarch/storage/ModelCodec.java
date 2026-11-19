package com.example.sysarch.storage;

import com.example.sysarch.model.DiagramLayout;
import com.example.sysarch.model.SysarchPackage;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;

/** Strict, deterministic snapshot codec; no network, database, live-model mutation or proxy resolution. */
public final class ModelCodec {
    public static final String SCHEMA = "1.0";
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    private final ObjectMapper json = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    public static final class InvalidSnapshot extends IllegalArgumentException {
        public InvalidSnapshot(String message) { super(message); }
        public InvalidSnapshot(String message, Throwable cause) { super(message, cause); }
    }

    public record SourceDocument(UUID uuid, Resource resource) { }
    public record Decoded(List<SourceDocument> documents, ModelIdentityRegistry identities) { }
    public record Digests(String complete, String semantic, String layout) { }

    public StoredModel capture(UUID modelUuid, List<SourceDocument> documents, ModelIdentityRegistry identities) {
        Map<EObject, UUID> objects = new IdentityHashMap<>();
        for (SourceDocument document : documents) {
            if (document.uuid() == null || document.resource() == null) throw invalid("Missing document identity or resource");
            for (var it = document.resource().getAllContents(); it.hasNext();) {
                EObject object = it.next();
                if (object.eIsProxy() || object.eClass().getEPackage() != SysarchPackage.eINSTANCE) {
                    throw invalid("Unsupported object or unresolved proxy");
                }
                objects.put(object, identities.id(object));
            }
        }
        List<StoredModel.Node> nodes = new ArrayList<>();
        objects.forEach((object, uuid) -> {
            Map<String, List<String>> attributes = new TreeMap<>();
            Map<String, List<UUID>> references = new TreeMap<>();
            for (EStructuralFeature feature : object.eClass().getEAllStructuralFeatures()) {
                if (!persistent(feature) || !object.eIsSet(feature)) continue;
                Object raw = object.eGet(feature, false);
                List<?> values = feature.isMany() ? List.copyOf((Collection<?>) raw) : raw == null ? List.of() : List.of(raw);
                if (feature instanceof EAttribute attribute) {
                    attributes.put(feature.getName(), values.stream()
                            .map(v -> EcoreUtil.convertToString(attribute.getEAttributeType(), v)).toList());
                } else {
                    List<UUID> targets = new ArrayList<>();
                    for (Object value : values) {
                        UUID target = objects.get(value);
                        if (target == null) throw invalid("Reference outside snapshot: " + feature.getName());
                        targets.add(target);
                    }
                    references.put(feature.getName(), targets);
                }
            }
            nodes.add(new StoredModel.Node(uuid, object.eClass().getName(), attributes, references));
        });
        List<StoredModel.Document> sources = documents.stream().map(d -> new StoredModel.Document(d.uuid(),
                d.resource().getContents().stream().map(objects::get).toList())).toList();
        StoredModel result = canonical(new StoredModel(SCHEMA, modelUuid, sources, nodes));
        validate(result);
        return result;
    }

    public byte[] encode(StoredModel model) {
        validate(model);
        return bytes(canonical(model));
    }

    public StoredModel read(byte[] input) {
        if (input == null || input.length > MAX_BYTES) throw invalid("Snapshot exceeds size limit");
        try {
            StoredModel model = this.json.readValue(input, StoredModel.class);
            validate(model);
            return canonical(model);
        } catch (IOException | NullPointerException e) {
            throw new InvalidSnapshot("Malformed snapshot", e);
        }
    }

    public Decoded decode(byte[] input, String expectedSha256) {
        if (input == null || input.length > MAX_BYTES) throw invalid("Snapshot exceeds size limit");
        if (expectedSha256 == null || !sha256(input).equals(expectedSha256)) throw invalid("Snapshot hash mismatch");
        StoredModel model = read(input);
        Map<UUID, EObject> objects = validate(model);
        ModelIdentityRegistry identities = new ModelIdentityRegistry();
        for (StoredModel.Node node : model.nodes()) {
            EObject object = objects.get(node.uuid());
            identities.register(object, node.uuid());
            node.attributes().forEach((name, values) -> {
                EAttribute attribute = (EAttribute) object.eClass().getEStructuralFeature(name);
                List<Object> parsed = values.stream().map(v -> EcoreUtil.createFromString(attribute.getEAttributeType(), v)).toList();
                assign(object, attribute, parsed);
            });
        }
        // All targets exist before assigning either containment or cross references.
        for (StoredModel.Node node : model.nodes()) {
            EObject object = objects.get(node.uuid());
            node.references().forEach((name, targets) -> assign(object, object.eClass().getEStructuralFeature(name),
                    targets.stream().map(objects::get).toList()));
        }
        List<SourceDocument> resources = new ArrayList<>();
        for (StoredModel.Document document : model.documents()) {
            Resource resource = new ResourceImpl(URI.createURI("sysarch-snapshot:/" + document.documentUuid()));
            document.roots().forEach(id -> resource.getContents().add(objects.get(id)));
            resources.add(new SourceDocument(document.documentUuid(), resource));
        }
        return new Decoded(List.copyOf(resources), identities);
    }

    /** Layout nodes do not affect semantic identity; no omitted semantics are accepted in complete snapshots. */
    public Digests digests(StoredModel model) {
        validate(model);
        Set<UUID> layout = new HashSet<>();
        model.nodes().stream().filter(n -> n.type().equals("DiagramLayout")).forEach(n -> layout.add(n.uuid()));
        return new Digests(sha256(bytes(canonical(model))), digestPartition(model, layout, false), digestPartition(model, layout, true));
    }

    private String digestPartition(StoredModel model, Set<UUID> layout, boolean selectedLayout) {
        List<StoredModel.Node> nodes = model.nodes().stream().filter(n -> layout.contains(n.uuid()) == selectedLayout).toList();
        Set<UUID> included = new HashSet<>();
        nodes.forEach(n -> included.add(n.uuid()));
        List<StoredModel.Document> documents = model.documents().stream().map(d -> new StoredModel.Document(d.documentUuid(),
                d.roots().stream().filter(included::contains).toList())).filter(d -> !d.roots().isEmpty()).toList();
        return sha256(bytes(canonical(new StoredModel(SCHEMA, model.modelUuid(), documents, nodes))));
    }

    private Map<UUID, EObject> validate(StoredModel model) {
        if (model == null || !SCHEMA.equals(model.schemaVersion())) throw invalid("Unsupported snapshot schema");
        Map<UUID, EObject> objects = new LinkedHashMap<>();
        for (StoredModel.Node node : model.nodes()) {
            if (!(SysarchPackage.eINSTANCE.getEClassifier(node.type()) instanceof EClass type) || type.isAbstract()) {
                throw invalid("Unknown model type: " + node.type());
            }
            if (objects.putIfAbsent(node.uuid(), EcoreUtil.create(type)) != null) throw invalid("Duplicate element UUID");
        }
        Map<UUID, UUID> parents = new HashMap<>();
        Set<UUID> roots = new HashSet<>();
        Set<UUID> documentIds = new HashSet<>();
        for (StoredModel.Document document : model.documents()) {
            if (!documentIds.add(document.documentUuid())) throw invalid("Duplicate document UUID");
            for (UUID id : document.roots()) {
                if (!objects.containsKey(id) || !roots.add(id)) throw invalid("Invalid or duplicate document root");
            }
        }
        for (StoredModel.Node node : model.nodes()) {
            EObject object = objects.get(node.uuid());
            node.attributes().forEach((name, values) -> {
                EStructuralFeature f = feature(object, name);
                if (!(f instanceof EAttribute attribute)) throw invalid("Not an attribute: " + name);
                cardinality(f, values);
                if (attribute.isUnique() && new HashSet<>(values).size() != values.size()) throw invalid("Duplicate attribute: " + name);
                if (values.isEmpty() && !attribute.isMany() && attribute.getEAttributeType().getInstanceClass().isPrimitive()) {
                    throw invalid("Null primitive attribute: " + name);
                }
                for (String value : values) {
                    try {
                        Object parsed = EcoreUtil.createFromString(attribute.getEAttributeType(), value);
                        if (parsed == null || !value.equals(EcoreUtil.convertToString(attribute.getEAttributeType(), parsed))) {
                            throw invalid("Noncanonical attribute literal: " + name);
                        }
                    } catch (RuntimeException e) { throw new InvalidSnapshot("Invalid attribute literal: " + name, e); }
                }
            });
            node.references().forEach((name, ids) -> {
                EStructuralFeature f = feature(object, name);
                if (!(f instanceof EReference reference)) throw invalid("Not a reference: " + name);
                cardinality(f, ids);
                if (reference.isUnique() && new HashSet<>(ids).size() != ids.size()) throw invalid("Duplicate reference: " + name);
                for (UUID target : ids) {
                    EObject other = objects.get(target);
                    if (other == null || !reference.getEReferenceType().isSuperTypeOf(other.eClass())) {
                        throw invalid("Missing or wrongly typed reference: " + name);
                    }
                    if ((object instanceof DiagramLayout) != (other instanceof DiagramLayout)) {
                        throw invalid("Semantic and layout references must stay separate");
                    }
                    if (reference.isContainment() && parents.putIfAbsent(target, node.uuid()) != null) {
                        throw invalid("Multiple containment owners");
                    }
                }
            });
        }
        for (UUID id : objects.keySet()) {
            if (roots.contains(id) == parents.containsKey(id)) throw invalid("Every element needs exactly one document or containment owner");
        }
        // Linear cycle check, performed before assigning EMF containment (which would silently reparent invalid input).
        Set<UUID> completed = new HashSet<>();
        for (UUID id : objects.keySet()) {
            Set<UUID> path = new HashSet<>();
            UUID next = id;
            while (next != null && !completed.contains(next)) {
                if (!path.add(next)) throw invalid("Containment cycle");
                next = parents.get(next);
            }
            completed.addAll(path);
        }
        return objects;
    }

    private static void cardinality(EStructuralFeature feature, List<?> values) {
        if (!feature.isMany() && values.size() != 1 || feature.getUpperBound() >= 0 && values.size() > feature.getUpperBound()) {
            throw invalid("Invalid feature cardinality: " + feature.getName());
        }
    }

    private static EStructuralFeature feature(EObject object, String name) {
        EStructuralFeature feature = object.eClass().getEStructuralFeature(name);
        if (feature == null || !persistent(feature)) throw invalid("Unknown or nonpersistent feature: " + name);
        return feature;
    }

    private static boolean persistent(EStructuralFeature feature) {
        return feature.isChangeable() && !feature.isDerived() && !feature.isTransient() && !feature.isVolatile()
                && !(feature instanceof EReference reference && reference.isContainer());
    }

    @SuppressWarnings("unchecked")
    private static void assign(EObject object, EStructuralFeature feature, List<?> values) {
        if (feature.isMany()) ((List<Object>) object.eGet(feature)).addAll(values);
        else object.eSet(feature, values.isEmpty() ? null : values.getFirst());
    }

    private static StoredModel canonical(StoredModel model) {
        return new StoredModel(model.schemaVersion(), model.modelUuid(),
                model.documents().stream().sorted(Comparator.comparing(d -> d.documentUuid().toString())).toList(),
                model.nodes().stream().sorted(Comparator.comparing(n -> n.uuid().toString())).toList());
    }

    private byte[] bytes(StoredModel model) {
        try {
            byte[] output = this.json.writeValueAsBytes(model);
            if (output.length > MAX_BYTES) throw invalid("Snapshot exceeds size limit");
            return output;
        } catch (IOException e) { throw new InvalidSnapshot("Cannot encode snapshot", e); }
    }

    public static String sha256(byte[] input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static InvalidSnapshot invalid(String message) { return new InvalidSnapshot(message); }
}

package com.example.sysarch.migration;

import com.example.sysarch.model.SysarchPackage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The interface data migration (spec P1 §2.4), as a pure function on a stored Sirius document (its JSON).
 *
 * <p>Metamodel v1.1 turns {@code interfaceType} from a string into a reference to an {@code InterfaceDef}: on
 * {@code Port} in P1 T1, on {@code Connection} in P1 T3 (its edge label is {@code {interfaceType.name}}). In the stored
 * JSON both are plain strings: P0 wrote the interface <i>name</i> ({@code "interfaceType":"CAN"}), v1.1 writes the
 * referenced object's <i>id</i>. Loaded unconverted, a name would be taken for an id that does not exist and the
 * interface silently lost on the next save, so the conversion runs on the JSON before EMF reads it.
 *
 * <ol>
 * <li>collect every port's and connection's non-blank {@code interfaceType} string, in document order;</li>
 * <li>a value that already is the id of an {@code InterfaceDef} of the document is left alone (converted before);</li>
 * <li>a value that names an {@code InterfaceDef} already in the document refers to it ({@link Status#LINKED}): a
 * document whose ports went through T1 keeps one entity per interface when its connections follow;</li>
 * <li>otherwise one {@code InterfaceDef} per distinct value is created at the top of the document ({@code name} = the
 * string, {@code layer} = LOGICAL, protocol and data type left empty) ({@link Status#CONVERTED});</li>
 * <li>a blank port value is removed and the port reported as {@link Status#EMPTY}, like a port that has none; those
 * ports already failed P0's {@code InterfaceTypeRequired} unless INOUT. A connection without a value is normal (a
 * delegation) and is not reported.</li>
 * </ol>
 *
 * <p>Ports of a document whose migration version is at least {@link #PORTS_VERSION} were converted by T1 and are not
 * looked at again (no second EMPTY row for the same port).
 *
 * <p>Ids are derived from the document and the value, so converting the same document twice (a dry-run, then the real
 * load; or a load that is not saved, then another) gives the same ids and the same report. {@link #revert} undoes a
 * conversion from its rows, for the rollback.
 */
public final class InterfaceEntityConversion {

    /** What happened to one element. */
    public enum Status {
        /** Now refers to an entity this conversion created. */
        CONVERTED,
        /** Now refers to an entity that was already in the document. */
        LINKED,
        /** A port without value. */
        EMPTY
    }

    /**
     * One report line (and backup row): the element, its old string and the entity it now refers to.
     *
     * @param elementClass {@code Port} or {@code Connection}
     */
    public record Row(String documentKey, String elementId, String elementName, String oldValue, String interfaceDefId, Status status,
            String elementClass) {

        /** A port's row (T1's shape). */
        public Row(String documentKey, String elementId, String elementName, String oldValue, String interfaceDefId, Status status) {
            this(documentKey, elementId, elementName, oldValue, interfaceDefId, status, PORT);
        }
    }

    /** The migration version of T1, which converted ports only. */
    public static final String PORTS_VERSION = "2026.11.0-202611080000";

    static final String PORT = "Port";

    static final String CONNECTION = "Connection";

    private static final String INTERFACE_DEF = "InterfaceDef";

    private static final String FEATURE = "interfaceType";

    private InterfaceEntityConversion() {
    }

    /**
     * Converts {@code document} in place.
     *
     * @param documentKey identifies the document (its resource URI); part of the derived ids
     * @return one row per element, in document order; empty if the document has no sysarch content or nothing to convert
     */
    public static List<Row> convert(String documentKey, JsonObject document) {
        String prefix = sysarchPrefix(document);
        if (prefix == null || !document.has("content") || !document.get("content").isJsonArray()) {
            return List.of();
        }
        boolean portsDone = migrationVersion(document).compareTo(PORTS_VERSION) >= 0;
        JsonArray content = document.getAsJsonArray("content");
        Set<String> existingIds = new HashSet<>();
        Map<String, String> existingIdByName = new LinkedHashMap<>();
        forEachObject(content, object -> {
            if (isClass(object, prefix, INTERFACE_DEF) && string(object, "id") != null) {
                existingIds.add(string(object, "id"));
                String name = object.has("data") && object.get("data").isJsonObject() ? string(object.getAsJsonObject("data"), "name") : null;
                if (name != null) {
                    existingIdByName.putIfAbsent(name, string(object, "id"));
                }
            }
        });
        Map<String, String> createdIdByValue = new LinkedHashMap<>();
        List<Row> rows = new ArrayList<>();
        forEachObject(content, object -> {
            String eClass = isClass(object, prefix, PORT) && !portsDone ? PORT : isClass(object, prefix, CONNECTION) ? CONNECTION : null;
            if (eClass == null) {
                return;
            }
            JsonObject data = object.has("data") && object.get("data").isJsonObject() ? object.getAsJsonObject("data") : null;
            String id = string(object, "id");
            String name = data == null ? null : string(data, "name");
            JsonElement value = data == null ? null : data.get(FEATURE);
            if (value == null || !value.isJsonPrimitive() || value.getAsString().isBlank()) {
                if (data != null) {
                    data.remove(FEATURE);
                }
                if (eClass.equals(PORT)) {
                    rows.add(new Row(documentKey, id, name, value == null || !value.isJsonPrimitive() ? null : value.getAsString(), null, Status.EMPTY, eClass));
                }
                return;
            }
            String text = value.getAsString();
            if (existingIds.contains(text)) {
                return; // already a reference
            }
            String existing = existingIdByName.get(text);
            if (existing != null) {
                data.addProperty(FEATURE, existing);
                rows.add(new Row(documentKey, id, name, text, existing, Status.LINKED, eClass));
                return;
            }
            String entityId = createdIdByValue.computeIfAbsent(text, v -> entityId(documentKey, v));
            data.addProperty(FEATURE, entityId);
            rows.add(new Row(documentKey, id, name, text, entityId, Status.CONVERTED, eClass));
        });
        createdIdByValue.forEach((text, entityId) -> {
            JsonObject entity = new JsonObject();
            entity.addProperty("id", entityId);
            entity.addProperty("eClass", prefix + ":" + INTERFACE_DEF);
            JsonObject data = new JsonObject();
            data.addProperty("name", text);
            data.addProperty("layer", "LOGICAL");
            entity.add("data", data);
            content.add(entity);
        });
        return rows;
    }

    /**
     * Undoes {@link #convert} in place: each converted or linked element gets its old string back, and the entities the
     * conversion created are removed (linked ones were there before and stay). For the rollback to metamodel v1.0 (P0).
     */
    public static void revert(JsonObject document, List<Row> rows) {
        String prefix = sysarchPrefix(document);
        if (prefix == null || !document.has("content")) {
            return;
        }
        Map<String, Row> rowByElement = new LinkedHashMap<>();
        Set<String> createdEntities = new HashSet<>();
        for (Row row : rows) {
            if (row.status() != Status.EMPTY) {
                rowByElement.put(row.elementId(), row);
            }
            if (row.status() == Status.CONVERTED) {
                createdEntities.add(row.interfaceDefId());
            }
        }
        JsonArray content = document.getAsJsonArray("content");
        forEachObject(content, object -> {
            Row row = rowByElement.get(string(object, "id"));
            if (row != null && isClass(object, prefix, row.elementClass()) && object.has("data")) {
                object.getAsJsonObject("data").addProperty(FEATURE, row.oldValue());
            }
        });
        for (int i = content.size() - 1; i >= 0; i--) {
            JsonElement root = content.get(i);
            if (root.isJsonObject() && createdEntities.contains(string(root.getAsJsonObject(), "id"))) {
                content.remove(i);
            }
        }
    }

    /** The document's recorded migration version ({@code migration.migrationVersion}), or "0" if it has none. */
    static String migrationVersion(JsonObject document) {
        if (document.has("migration") && document.get("migration").isJsonObject()) {
            JsonElement version = document.getAsJsonObject("migration").get("migrationVersion");
            if (version != null && version.isJsonPrimitive()) {
                return version.getAsString();
            }
        }
        return "0";
    }

    /** The id of the entity created for {@code value} in a document: stable across runs. */
    static String entityId(String documentKey, String value) {
        return UUID.nameUUIDFromBytes(("sysarch:InterfaceDef\u0000" + documentKey + "\u0000" + value).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** The prefix the document uses for the sysarch package ({@code "ns": {"sysarch": "<nsURI>"}}), or null. */
    static String sysarchPrefix(JsonObject document) {
        if (!document.has("ns") || !document.get("ns").isJsonObject()) {
            return null;
        }
        for (var entry : document.getAsJsonObject("ns").entrySet()) {
            if (entry.getValue().isJsonPrimitive() && SysarchPackage.eNS_URI.equals(entry.getValue().getAsString())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static boolean isClass(JsonObject object, String prefix, String eClass) {
        return (prefix + ":" + eClass).equals(string(object, "eClass"));
    }

    private static String string(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : null;
    }

    /** Every object of the content tree (roots and everything contained), depth first, in document order. */
    private static void forEachObject(JsonElement element, Consumer<JsonObject> action) {
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                forEachObject(item, action);
            }
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("eClass")) {
                action.accept(object);
            }
            if (object.has("data") && object.get("data").isJsonObject()) {
                for (var member : object.getAsJsonObject("data").entrySet()) {
                    if (member.getValue().isJsonArray() || member.getValue().isJsonObject()) {
                        forEachObject(member.getValue(), action);
                    }
                }
            }
        }
    }
}

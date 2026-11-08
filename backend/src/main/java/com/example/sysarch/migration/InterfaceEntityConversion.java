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
 * P1 T1 data migration (spec P1 §2.4), as a pure function on a stored Sirius document (its JSON).
 *
 * <p>Metamodel v1.1 turns {@code Port.interfaceType} from a string into a reference to an {@code InterfaceDef}. In the
 * stored JSON both are plain strings: P0 wrote the interface <i>name</i> ({@code "interfaceType":"CAN"}), v1.1 writes the
 * referenced object's <i>id</i>. Loaded unconverted, a name would be taken for an id that does not exist and the
 * interface silently lost on the next save, so the conversion runs on the JSON before EMF reads it.
 *
 * <ol>
 * <li>collect every port's non-blank {@code interfaceType} string, de-duplicated in document order;</li>
 * <li>create one {@code InterfaceDef} per value at the top of the document ({@code name} = the string,
 * {@code layer} = LOGICAL, protocol and data type left empty);</li>
 * <li>point each port at its entity;</li>
 * <li>a blank value is removed and the port reported as {@link Status#EMPTY}, like a port that has none; those ports
 * already failed P0's {@code InterfaceTypeRequired} unless INOUT.</li>
 * </ol>
 *
 * <p>Ids are derived from the document and the value, so converting the same document twice (a dry-run, then the real
 * load; or a load that is not saved, then another) gives the same ids and the same report. {@link #revert} undoes a
 * conversion from its rows, for the rollback.
 */
public final class InterfaceEntityConversion {

    /** What happened to one port. */
    public enum Status {
        CONVERTED, EMPTY
    }

    /** One report line (and backup row): the port, its old string and the entity it now refers to. */
    public record Row(String documentKey, String portId, String portName, String oldValue, String interfaceDefId, Status status) {
    }

    private static final String PORT = "Port";

    private static final String INTERFACE_DEF = "InterfaceDef";

    private static final String FEATURE = "interfaceType";

    private InterfaceEntityConversion() {
    }

    /**
     * Converts {@code document} in place.
     *
     * @param documentKey identifies the document (its resource URI); part of the derived ids
     * @return one row per port, in document order; empty if the document has no sysarch content
     */
    public static List<Row> convert(String documentKey, JsonObject document) {
        String prefix = sysarchPrefix(document);
        if (prefix == null || !document.has("content") || !document.get("content").isJsonArray()) {
            return List.of();
        }
        JsonArray content = document.getAsJsonArray("content");
        Map<String, String> entityIdByValue = new LinkedHashMap<>();
        List<Row> rows = new ArrayList<>();
        forEachObject(content, object -> {
            if (!isClass(object, prefix, PORT)) {
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
                rows.add(new Row(documentKey, id, name, value == null || !value.isJsonPrimitive() ? null : value.getAsString(), null, Status.EMPTY));
                return;
            }
            String text = value.getAsString();
            String entityId = entityIdByValue.computeIfAbsent(text, v -> entityId(documentKey, v));
            data.addProperty(FEATURE, entityId);
            rows.add(new Row(documentKey, id, name, text, entityId, Status.CONVERTED));
        });
        entityIdByValue.forEach((text, entityId) -> {
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
     * Undoes {@link #convert} in place: each converted port gets its old string back, and the entities the conversion
     * created are removed. For the rollback to metamodel v1.0 (P0), whose ports hold the string.
     */
    public static void revert(JsonObject document, List<Row> rows) {
        String prefix = sysarchPrefix(document);
        if (prefix == null || !document.has("content")) {
            return;
        }
        Map<String, String> oldValueByPort = new LinkedHashMap<>();
        Set<String> createdEntities = new HashSet<>();
        for (Row row : rows) {
            if (row.status() == Status.CONVERTED) {
                oldValueByPort.put(row.portId(), row.oldValue());
                createdEntities.add(row.interfaceDefId());
            }
        }
        JsonArray content = document.getAsJsonArray("content");
        forEachObject(content, object -> {
            String oldValue = oldValueByPort.get(string(object, "id"));
            if (oldValue != null && isClass(object, prefix, PORT) && object.has("data")) {
                object.getAsJsonObject("data").addProperty(FEATURE, oldValue);
            }
        });
        for (int i = content.size() - 1; i >= 0; i--) {
            JsonElement root = content.get(i);
            if (root.isJsonObject() && createdEntities.contains(string(root.getAsJsonObject(), "id"))) {
                content.remove(i);
            }
        }
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

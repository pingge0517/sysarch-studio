package com.example.sysarch.baseline;

import com.example.sysarch.baseline.ModelSnapshot.ElementState;
import com.example.sysarch.sync.api.model.ModelDiff;
import com.example.sysarch.sync.api.model.SyncResultConflictsInner; // the generator reuses it: same shape {elementId, fields}

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * The diff of two model states (P1 T7 step 2): elements aligned by element id; {@code added} and {@code removed} ids,
 * and for {@code modified} the fields that differ: {@code name}, {@code layer}, {@code type} and the attribute names
 * ({@link ModelSnapshot}; {@code container} when an element moved, P2). Everything sorted, so the same two states always give the same diff.
 */
public final class ModelDiffs {

    private ModelDiffs() {
    }

    /** What changed from {@code before} to {@code after}. */
    public static ModelDiff diff(Map<String, ElementState> before, Map<String, ElementState> after) {
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<SyncResultConflictsInner> modified = new ArrayList<>();
        new TreeSet<>(after.keySet()).forEach(id -> {
            if (!before.containsKey(id)) {
                added.add(id);
            }
        });
        for (String id : new TreeSet<>(before.keySet())) {
            ElementState old = before.get(id);
            ElementState now = after.get(id);
            if (now == null) {
                removed.add(id);
                continue;
            }
            List<String> fields = fields(old, now);
            if (!fields.isEmpty()) {
                modified.add(new SyncResultConflictsInner().elementId(id).fields(fields));
            }
        }
        return new ModelDiff().added(added).removed(removed).modified(modified);
    }

    /** The fields that differ (also a branch changeset row's {@code fields}). */
    public static List<String> fields(ElementState old, ElementState now) {
        List<String> fields = new ArrayList<>();
        if (!Objects.equals(old.type(), now.type())) {
            fields.add("type");
        }
        if (!Objects.equals(old.name(), now.name())) {
            fields.add("name");
        }
        if (!Objects.equals(old.layer(), now.layer())) {
            fields.add("layer");
        }
        TreeSet<String> keys = new TreeSet<>(old.attributes().keySet());
        keys.addAll(now.attributes().keySet());
        for (String key : keys) {
            if (key.startsWith("$") && !(old.attributes().containsKey(key) && now.attributes().containsKey(key))) {
                continue; // a baseline from before P2 has no structure: not a change
            }
            if (!Objects.equals(old.attributes().get(key), now.attributes().get(key))) {
                fields.add(key.startsWith("$") ? key.substring(1) : key);
            }
        }
        return fields;
    }
}

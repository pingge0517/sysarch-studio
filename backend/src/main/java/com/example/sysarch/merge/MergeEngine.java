package com.example.sysarch.merge;

import com.example.sysarch.baseline.ModelSnapshot;
import com.example.sysarch.baseline.ModelSnapshot.ElementState;
import com.example.sysarch.model.SysarchPackage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * The three-way merge of a branch into its trunk (P2 §3.2, T4), aligned by element id. A pure function of three model
 * states: {@code base} (the baseline the branch forked from), {@code theirs} (the branch now), {@code ours} (the trunk
 * now), plus the strategy and the user's choices for conflicts.
 *
 * <ol>
 * <li>Outer join by element id.</li>
 * <li>Added on both sides with the same id: equal → once; different → conflict {@code DUPLICATE_ID}.</li>
 * <li>Deleted on one side: if the other side left the element as in the base → deleted; if it modified it → conflict
 * {@code DELETE_MODIFY} (user's decision: nobody's edit is dropped silently). Deleted on both sides → deleted.</li>
 * <li>Present on all sides: field by field. Changed on one side → that side; on both to the same value → that value;
 * on both to different values → a Polarion-owned field ({@link #POLARION_FIELDS}) is decided by the strategy
 * (POLARION_WINS → the trunk, which is synced with Polarion; MODEL_WINS → the branch) and listed as auto-resolved;
 * every other field is a {@code FIELD} conflict (user's decision: modelling content is never overwritten
 * automatically). Where an element sits ({@code container}) is one field.</li>
 * <li>The result: the operations on the trunk (ADD / MODIFY / DELETE) and the conflicts. A conflict without a choice
 * keeps the trunk's value in the result, but {@link Result#applicable()} is false until every conflict has one (user's
 * decision); references that would point at a deleted element are {@code DANGLING} problems, which no choice of
 * OURS/THEIRS on that element can fix, so they block too.</li>
 * </ol>
 */
public final class MergeEngine {

    /** Fields Polarion owns in the model (P0 §6.2, P1 T6): the requirement text and the bound Work Item id. */
    public static final Set<String> POLARION_FIELDS = Set.of("text", "reqId");

    /** Above this many conflicts automatic merging is refused: every conflict is handled per element (spec §3.3). */
    public static final int CONFLICT_LIMIT = 50;

    public enum Strategy {
        MODEL_WINS, POLARION_WINS
    }

    public enum Side {
        OURS, THEIRS
    }

    public enum Kind {
        FIELD, DELETE_MODIFY, DUPLICATE_ID, DANGLING
    }

    /** A conflict; {@code field} is null for a whole element; values are as stored (lists as JSON-like text). */
    public record Conflict(String elementId, String field, Kind kind, String base, String theirs, String ours) {
    }

    /** A user's choice: for one field, or (field null) for every conflict of the element. */
    public record Resolution(String elementId, String field, Side take) {
    }

    /** A field decided by the strategy. */
    public record AutoResolved(String elementId, String field, Strategy strategy, String value) {
    }

    /** One change to the trunk. */
    public record Operation(String elementId, String op, List<String> fields) {
    }

    public record Stats(int added, int modified, int deleted, int conflicts, int unresolved, int autoResolved) {
    }

    /**
     * @param merged the trunk's state after the merge, by element id (unresolved conflicts keep the trunk's side)
     * @param operations what changes in the trunk
     * @param conflicts every conflict found (resolved or not)
     * @param unresolved the conflicts without a choice, and the DANGLING problems
     */
    public record Result(Map<String, ElementState> merged, List<Operation> operations, List<Conflict> conflicts, List<Conflict> unresolved,
            List<AutoResolved> autoResolved, Stats stats) {

        /** Every conflict has a choice and nothing dangles. */
        public boolean applicable() {
            return this.unresolved.isEmpty();
        }

        /** More than {@value MergeEngine#CONFLICT_LIMIT} conflicts: no automatic merge, per-element handling (§3.3). */
        public boolean autoMergeRefused() {
            return this.conflicts.size() > CONFLICT_LIMIT;
        }
    }

    private static final String TYPE = "type";

    private static final String NAME = "name";

    private static final String LAYER = "layer";

    private static final String CONTAINER = "container";

    private MergeEngine() {
    }

    public static Result merge(Map<String, ElementState> base, Map<String, ElementState> theirs, Map<String, ElementState> ours, Strategy strategy,
            Collection<Resolution> resolutions) {
        Map<String, Side> byElement = new TreeMap<>();
        Map<String, Side> byField = new TreeMap<>();
        for (Resolution r : resolutions) {
            if (r.field() == null || r.field().isBlank()) {
                byElement.put(r.elementId(), r.take());
            } else {
                byField.put(r.elementId() + "#" + r.field(), r.take());
            }
        }
        Map<String, ElementState> merged = new TreeMap<>();
        List<Conflict> conflicts = new ArrayList<>();
        List<Conflict> unresolved = new ArrayList<>();
        List<AutoResolved> auto = new ArrayList<>();

        Set<String> ids = new TreeSet<>(base.keySet());
        ids.addAll(theirs.keySet());
        ids.addAll(ours.keySet());
        for (String id : ids) {
            ElementState b = base.get(id);
            ElementState t = theirs.get(id);
            ElementState o = ours.get(id);
            ElementState result;
            if (b == null) {
                if (t != null && o != null && !t.equals(o)) {
                    Conflict c = new Conflict(id, null, Kind.DUPLICATE_ID, null, summary(t), summary(o));
                    result = choose(c, byElement, byField, conflicts, unresolved) == Side.THEIRS ? t : o;
                } else {
                    result = o != null ? o : t;
                }
            } else if (t == null || o == null) {
                ElementState survivor = t != null ? t : o;
                if (survivor == null || survivor.equals(b)) {
                    result = null; // deleted on one side and untouched on the other, or deleted on both
                } else {
                    Conflict c = new Conflict(id, null, Kind.DELETE_MODIFY, summary(b), t == null ? "（已删除）" : summary(t), o == null ? "（已删除）" : summary(o));
                    Side side = choose(c, byElement, byField, conflicts, unresolved);
                    ElementState chosen = side == Side.THEIRS ? t : o;
                    result = side == null ? o : chosen;
                }
            } else {
                result = mergeFields(id, b, t, o, strategy, byElement, byField, conflicts, unresolved, auto);
            }
            if (result != null) {
                merged.put(id, result);
            }
        }

        dangling(merged).forEach(unresolved::add);
        List<Operation> operations = operations(ours, merged);
        Stats stats = new Stats((int) operations.stream().filter(op -> op.op().equals("ADD")).count(),
                (int) operations.stream().filter(op -> op.op().equals("MODIFY")).count(), (int) operations.stream().filter(op -> op.op().equals("DELETE")).count(),
                conflicts.size(), unresolved.size(), auto.size());
        return new Result(merged, operations, conflicts, unresolved, auto, stats);
    }

    private static ElementState mergeFields(String id, ElementState b, ElementState t, ElementState o, Strategy strategy, Map<String, Side> byElement,
            Map<String, Side> byField, List<Conflict> conflicts, List<Conflict> unresolved, List<AutoResolved> auto) {
        if (!Objects.equals(t.type(), o.type())) {
            Conflict c = new Conflict(id, TYPE, Kind.FIELD, b.type(), t.type(), o.type());
            return choose(c, byElement, byField, conflicts, unresolved) == Side.THEIRS ? t : o;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : fields(b, t, o)) {
            Object bv = get(b, field);
            Object tv = get(t, field);
            Object ov = get(o, field);
            Object value;
            if (Objects.equals(tv, bv) || Objects.equals(tv, ov)) {
                value = ov;
            } else if (Objects.equals(ov, bv)) {
                value = tv;
            } else if (POLARION_FIELDS.contains(field)) {
                value = strategy == Strategy.POLARION_WINS ? ov : tv;
                auto.add(new AutoResolved(id, field, strategy, text(value)));
            } else {
                Conflict c = new Conflict(id, field, Kind.FIELD, text(bv), text(tv), text(ov));
                value = choose(c, byElement, byField, conflicts, unresolved) == Side.THEIRS ? tv : ov;
            }
            values.put(field, value);
        }
        return state(id, o.type(), values);
    }

    /** The fields of an element: type, name, layer, container (one field for $container + $feature), attributes. */
    private static Set<String> fields(ElementState... states) {
        Set<String> fields = new TreeSet<>(List.of(NAME, LAYER, CONTAINER));
        for (ElementState s : states) {
            s.attributes().keySet().stream().filter(k -> !k.startsWith("$")).forEach(fields::add);
        }
        return fields;
    }

    private static Object get(ElementState state, String field) {
        return switch (field) {
            case NAME -> state.name();
            case LAYER -> state.layer();
            case CONTAINER -> List.of(String.valueOf(state.attributes().getOrDefault(ModelSnapshot.CONTAINER, "")),
                    String.valueOf(state.attributes().getOrDefault(ModelSnapshot.FEATURE, "")));
            default -> state.attributes().get(field);
        };
    }

    private static ElementState state(String id, String type, Map<String, Object> values) {
        Map<String, Object> attributes = new TreeMap<>();
        values.forEach((field, value) -> {
            if (field.equals(CONTAINER)) {
                List<?> place = (List<?>) value;
                attributes.put(ModelSnapshot.CONTAINER, place.get(0));
                if (!"".equals(place.get(1))) {
                    attributes.put(ModelSnapshot.FEATURE, place.get(1));
                }
            } else if (!field.equals(NAME) && !field.equals(LAYER) && value != null) {
                attributes.put(field, value);
            }
        });
        return new ElementState(id, type, (String) values.get(NAME), (String) values.get(LAYER), attributes);
    }

    /** The user's choice for a conflict; null (and listed as unresolved) when there is none. */
    private static Side choose(Conflict conflict, Map<String, Side> byElement, Map<String, Side> byField, List<Conflict> conflicts,
            List<Conflict> unresolved) {
        conflicts.add(conflict);
        Side side = conflict.field() == null ? byElement.get(conflict.elementId())
                : byField.getOrDefault(conflict.elementId() + "#" + conflict.field(), byElement.get(conflict.elementId()));
        if (side == null) {
            unresolved.add(conflict);
        }
        return side;
    }

    /** References and containers in the merged model that point at elements it no longer has. */
    static List<Conflict> dangling(Map<String, ElementState> merged) {
        List<Conflict> problems = new ArrayList<>();
        for (ElementState state : merged.values()) {
            Object container = state.attributes().get(ModelSnapshot.CONTAINER);
            if (container != null && !"".equals(container) && !merged.containsKey(container.toString())) {
                problems.add(new Conflict(state.id(), CONTAINER, Kind.DANGLING, null, container.toString(), "所在元素在合并结果里已不存在"));
            }
            if (!(SysarchPackage.eINSTANCE.getEClassifier(state.type()) instanceof EClass eClass)) {
                continue;
            }
            for (EStructuralFeature feature : eClass.getEAllStructuralFeatures()) {
                if (feature instanceof EReference reference && !reference.isContainment() && !reference.isContainer()) {
                    Object value = state.attributes().get(feature.getName());
                    List<?> targets = value instanceof List<?> list ? list : value == null ? List.of() : List.of(value);
                    for (Object target : targets) {
                        if (!merged.containsKey(target.toString())) {
                            problems.add(new Conflict(state.id(), feature.getName(), Kind.DANGLING, null, target.toString(), "引用的元素在合并结果里已不存在"));
                        }
                    }
                }
            }
        }
        return problems;
    }

    /** What changes in the trunk: by element id. */
    private static List<Operation> operations(Map<String, ElementState> ours, Map<String, ElementState> merged) {
        List<Operation> operations = new ArrayList<>();
        Set<String> ids = new TreeSet<>(ours.keySet());
        ids.addAll(merged.keySet());
        for (String id : ids) {
            ElementState o = ours.get(id);
            ElementState m = merged.get(id);
            if (o == null) {
                operations.add(new Operation(id, "ADD", List.of()));
            } else if (m == null) {
                operations.add(new Operation(id, "DELETE", List.of()));
            } else if (!o.equals(m)) {
                operations.add(new Operation(id, "MODIFY", com.example.sysarch.baseline.ModelDiffs.fields(o, m)));
            }
        }
        return operations;
    }

    private static String summary(ElementState state) {
        return state.type() + " " + state.name();
    }

    private static String text(Object value) {
        return value == null ? null : value instanceof List<?> list ? String.join(", ", list.stream().map(String::valueOf).toList()) : value.toString();
    }
}

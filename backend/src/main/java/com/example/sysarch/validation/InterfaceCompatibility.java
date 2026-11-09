package com.example.sysarch.validation;

import com.example.sysarch.ModelStamp;
import com.example.sysarch.model.InterfaceDef;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.notify.impl.AdapterImpl;

/**
 * {@code InterfaceDef.compatibleWith} (P1 T2, spec P1 §2.3): two interfaces are assignable when they are the same entity
 * or one is an ancestor of the other along {@code superTypes}, transitively. The generated method delegates here (a
 * GenModel {@code body} annotation in the Ecore), and the OCL invariant {@code PortInterfaceAssignable} calls it.
 *
 * <p>Java rather than an OCL {@code closure}, as the spec asks, so results can be cached: per model (the cache is an
 * adapter on the resource set, so it lives and dies with the model), per pair, and dropped as soon as anything in the
 * model changes ({@link ModelStamp}). Cycles in {@code superTypes} are tolerated. A missing interface is never
 * assignable; the invariant already rejects it before calling this.
 */
public final class InterfaceCompatibility {

    private InterfaceCompatibility() {
    }

    public static boolean compatible(InterfaceDef a, InterfaceDef b) {
        if (a == null || b == null) {
            return false;
        }
        if (a == b) {
            return true;
        }
        Notifier scope = ModelStamp.scope(a);
        long stamp = ModelStamp.of(a);
        Cache cache = Cache.of(scope);
        synchronized (cache) {
            if (cache.stamp != stamp) {
                cache.results.clear();
                cache.stamp = stamp;
            }
            Map<InterfaceDef, Boolean> row = cache.results.computeIfAbsent(a, key -> new IdentityHashMap<>());
            Boolean known = row.get(b);
            if (known == null) {
                known = ancestors(a).contains(b) || ancestors(b).contains(a);
                row.put(b, known);
            }
            return known;
        }
    }

    /** Every interface reachable from {@code start} through {@code superTypes}, without {@code start} unless on a cycle. */
    static Set<InterfaceDef> ancestors(InterfaceDef start) {
        Set<InterfaceDef> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<InterfaceDef> todo = new ArrayDeque<>(start.getSuperTypes());
        while (!todo.isEmpty()) {
            InterfaceDef next = todo.pop();
            if (next != null && seen.add(next)) {
                todo.addAll(next.getSuperTypes());
            }
        }
        return seen;
    }

    /** Pair results of one model, valid for one model stamp. */
    private static final class Cache extends AdapterImpl {

        private long stamp = -1;

        private final Map<InterfaceDef, Map<InterfaceDef, Boolean>> results = new HashMap<>();

        static Cache of(Notifier scope) {
            synchronized (scope) {
                for (Object adapter : scope.eAdapters()) {
                    if (adapter instanceof Cache cache) {
                        return cache;
                    }
                }
                Cache cache = new Cache();
                scope.eAdapters().add(cache);
                return cache;
            }
        }

        @Override
        public boolean isAdapterForType(Object type) {
            return type == Cache.class;
        }
    }
}

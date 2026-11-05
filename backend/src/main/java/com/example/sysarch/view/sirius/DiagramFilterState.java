package com.example.sysarch.view.sirius;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Which VSM filters (e.g. {@code hideAbstract}) are switched on, per open diagram (Sirius representation id).
 * Filters change only what a diagram shows, never the model (spec §5.1 invariant 3).
 *
 * <p>P0 keeps this in memory: after a restart every diagram shows everything again.
 */
@Component
public class DiagramFilterState {

    private final Map<String, Set<String>> activeByDiagram = new ConcurrentHashMap<>();

    public Set<String> active(String diagramId) {
        return diagramId == null ? Set.of() : Set.copyOf(this.activeByDiagram.getOrDefault(diagramId, Set.of()));
    }

    /** Switches a filter on if it was off and off if it was on; returns the new state. */
    public boolean toggle(String diagramId, String filterId) {
        boolean[] nowActive = new boolean[1];
        this.activeByDiagram.compute(diagramId, (id, current) -> {
            Set<String> next = current == null ? new HashSet<>() : new HashSet<>(current);
            if (next.contains(filterId)) {
                next.remove(filterId);
                nowActive[0] = false;
            } else {
                next.add(filterId);
                nowActive[0] = true;
            }
            return next;
        });
        return nowActive[0];
    }
}

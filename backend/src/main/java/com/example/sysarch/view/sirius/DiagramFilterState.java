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

    /** P3 (spec §9): the partition shown per diagram (0 = the first; only views with partitionSize use it). */
    private final Map<String, Integer> partitionByDiagram = new ConcurrentHashMap<>();

    /** The switched-on filters, plus the partition token when a partition other than the first is shown (the cache keys on both). */
    public Set<String> active(String diagramId) {
        if (diagramId == null) {
            return Set.of();
        }
        Set<String> active = new HashSet<>(this.activeByDiagram.getOrDefault(diagramId, Set.of()));
        int partition = this.partitionByDiagram.getOrDefault(diagramId, 0);
        if (partition > 0) {
            active.add(com.example.sysarch.view.projection.DiagramProjector.PARTITION_TOKEN + partition);
        }
        return Set.copyOf(active);
    }

    /** Moves to the previous (-1) or next (+1) partition, within 0..count-1; returns the new index. */
    public int movePartition(String diagramId, int delta, int count) {
        return this.partitionByDiagram.merge(diagramId, Math.max(0, Math.min(count - 1, delta)), (old, d) -> Math.max(0, Math.min(count - 1, old + delta)));
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

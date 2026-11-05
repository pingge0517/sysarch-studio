package com.example.sysarch.view.refresh;

import com.example.sysarch.view.projection.DiagramSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Incremental change between two snapshots of one diagram: what to insert, delete and update. This is all that
 * is sent to an open canvas; the diagram is never redrawn as a whole (spec §5.2).
 *
 * <p>An update means a node or edge with the same id has a different label, style, parent or compartment. A
 * rename therefore becomes a single label update and moves nothing.
 */
public record SnapshotDiff(
        List<DiagramSnapshot.Node> addedNodes,
        List<String> removedNodeIds,
        List<DiagramSnapshot.Node> updatedNodes,
        List<DiagramSnapshot.Edge> addedEdges,
        List<String> removedEdgeIds,
        List<DiagramSnapshot.Edge> updatedEdges) {

    public SnapshotDiff {
        addedNodes = List.copyOf(addedNodes);
        removedNodeIds = List.copyOf(removedNodeIds);
        updatedNodes = List.copyOf(updatedNodes);
        addedEdges = List.copyOf(addedEdges);
        removedEdgeIds = List.copyOf(removedEdgeIds);
        updatedEdges = List.copyOf(updatedEdges);
    }

    public static SnapshotDiff between(DiagramSnapshot before, DiagramSnapshot after) {
        Changes<DiagramSnapshot.Node> nodes = changes(before.nodes(), after.nodes(), DiagramSnapshot.Node::id);
        Changes<DiagramSnapshot.Edge> edges = changes(before.edges(), after.edges(), DiagramSnapshot.Edge::id);
        return new SnapshotDiff(nodes.added(), nodes.removed(), nodes.updated(), edges.added(), edges.removed(), edges.updated());
    }

    public boolean isEmpty() {
        return this.size() == 0;
    }

    /** Number of individual insert/delete/update operations. */
    public int size() {
        return this.addedNodes.size() + this.removedNodeIds.size() + this.updatedNodes.size() + this.addedEdges.size()
                + this.removedEdgeIds.size() + this.updatedEdges.size();
    }

    private static <T> Changes<T> changes(List<T> before, List<T> after, Function<T, String> id) {
        Map<String, T> old = new LinkedHashMap<>();
        before.forEach(item -> old.put(id.apply(item), item));
        List<T> added = new ArrayList<>();
        List<T> updated = new ArrayList<>();
        for (T item : after) {
            T previous = old.remove(id.apply(item));
            if (previous == null) {
                added.add(item);
            } else if (!previous.equals(item)) {
                updated.add(item);
            }
        }
        return new Changes<>(added, List.copyOf(old.keySet()), updated);
    }

    private record Changes<T>(List<T> added, List<String> removed, List<T> updated) {
    }
}

package com.example.sysarch.view.projection;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What a diagram shows for a given model state: nodes, border nodes, edges and their labels. It is a plain value
 * (no EMF objects), so two snapshots can be compared and diffed (T5 incremental refresh), and tests can assert
 * on {@link #describe()}.
 *
 * <p>Every node and edge refers back to a model element through {@code elementId} (spec §5.1 invariant 1).
 *
 * @param rootId element id of the diagram root; border nodes of the root use it as their parent (diagram frame)
 */
public record DiagramSnapshot(String representationId, String rootId, List<Node> nodes, List<Edge> edges) {

    public DiagramSnapshot {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public Optional<Node> node(String id) {
        return this.nodes.stream().filter(n -> n.id().equals(id)).findFirst();
    }

    public List<Edge> edges(String styleId) {
        return this.edges.stream().filter(e -> e.styleId().equals(styleId)).toList();
    }

    /** Stable, human-readable listing for snapshot tests: one line per node, compartment item and edge. */
    public String describe() {
        StringBuilder text = new StringBuilder();
        for (Node node : this.nodes) {
            text.append("node ").append(node.id()).append(" [").append(node.styleId()).append("]");
            if (node.parentId() != null) {
                text.append(" on ").append(node.parentId());
            }
            text.append(" \"").append(node.label()).append("\"");
            if (node.subLabel() != null) {
                text.append(" / \"").append(node.subLabel()).append("\"");
            }
            text.append('\n');
            for (Compartment compartment : node.compartments()) {
                String items = compartment.items().isEmpty() ? "-"
                        : compartment.items().stream().map(i -> "\"" + i + "\"").collect(Collectors.joining(", "));
                text.append("  ").append(compartment.label()).append(": ").append(items).append('\n');
            }
        }
        for (Edge edge : this.edges) {
            text.append("edge ").append(edge.sourceId()).append(" -> ").append(edge.targetId()).append(" [").append(edge.styleId())
                    .append("] \"").append(edge.label()).append("\"\n");
        }
        return text.toString();
    }

    /**
     * @param id equals {@code elementId}: each element is shown by at most one node
     * @param parentId for border nodes, the id of the node (or the diagram root) they sit on; null otherwise
     * @param subLabel null when the style has no sub-label
     */
    public record Node(String id, String elementId, String styleId, String parentId, String label, String subLabel,
            List<Compartment> compartments) {

        public Node {
            Objects.requireNonNull(id);
            compartments = List.copyOf(compartments);
        }
    }

    public record Compartment(String feature, String label, List<String> items) {

        public Compartment {
            items = List.copyOf(items);
        }
    }

    /**
     * @param elementId the element the label is read from (e.g. the Part of a composition edge)
     * @param sourceId id of the source node
     * @param targetId id of the target node
     */
    public record Edge(String id, String elementId, String styleId, String sourceId, String targetId, String label) {
    }
}

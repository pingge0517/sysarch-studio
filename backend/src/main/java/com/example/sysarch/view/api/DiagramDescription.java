package com.example.sysarch.view.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runtime form of one VSM diagram representation (spec §4.2, §4.5). Instances are built only by code that
 * vsm-compiler generates from {@code views/*.vsm.yaml}; YAML is never read at runtime.
 *
 * <p>This is our own descriptor API, not Sirius Web's diagram API. A hand-written adapter (T4) maps it onto
 * Sirius Web, so a Sirius upgrade touches the adapter only and never the generated code.
 *
 * @param domainType root element type, as {@code nsPrefix::EClass} (e.g. {@code sysarch::PartDefinition})
 */
public record DiagramDescription(
        String id,
        String label,
        String domainType,
        SynchronizationKind synchronizationKind,
        RefreshPolicy refreshPolicy,
        List<AutoExpand> autoExpands,
        List<Filter> filters,
        List<NodeStyle> nodes,
        List<EdgeStyle> edges,
        List<Tool> tools) {

    public DiagramDescription {
        Objects.requireNonNull(id);
        Objects.requireNonNull(domainType);
        Objects.requireNonNull(synchronizationKind);
        autoExpands = List.copyOf(autoExpands);
        filters = List.copyOf(filters);
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        tools = List.copyOf(tools);
    }

    public static Builder newDiagramDescription(String id) {
        return new Builder(id);
    }

    /** Fluent builder used by generated providers. */
    public static final class Builder {
        private final String id;
        private String label;
        private String domainType;
        private SynchronizationKind synchronizationKind;
        private RefreshPolicy refreshPolicy;
        private final List<AutoExpand> autoExpands = new ArrayList<>();
        private final List<Filter> filters = new ArrayList<>();
        private final List<NodeStyle> nodes = new ArrayList<>();
        private final List<EdgeStyle> edges = new ArrayList<>();
        private final List<Tool> tools = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder domainType(String domainType) {
            this.domainType = domainType;
            return this;
        }

        public Builder synchronizationKind(SynchronizationKind synchronizationKind) {
            this.synchronizationKind = synchronizationKind;
            return this;
        }

        public Builder refreshPolicy(RefreshPolicy refreshPolicy) {
            this.refreshPolicy = refreshPolicy;
            return this;
        }

        public Builder autoExpand(String feature, int maxDepth) {
            this.autoExpands.add(new AutoExpand(feature, maxDepth));
            return this;
        }

        public Builder addFilter(Filter filter) {
            this.filters.add(filter);
            return this;
        }

        public Builder addNode(NodeStyle node) {
            this.nodes.add(node);
            return this;
        }

        public Builder addEdge(EdgeStyle edge) {
            this.edges.add(edge);
            return this;
        }

        public Builder addTool(Tool tool) {
            this.tools.add(tool);
            return this;
        }

        public DiagramDescription build() {
            return new DiagramDescription(this.id, this.label, this.domainType, this.synchronizationKind, this.refreshPolicy,
                    this.autoExpands, this.filters, this.nodes, this.edges, this.tools);
        }
    }
}

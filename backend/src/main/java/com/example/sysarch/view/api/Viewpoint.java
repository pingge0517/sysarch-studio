package com.example.sysarch.view.api;

import java.util.List;

/**
 * A viewpoint (VSM {@code viewpoint}) and the representations it groups. Several YAML files may contribute
 * representations to one viewpoint, provided they declare it identically.
 */
public record Viewpoint(String id, String label, String metamodelUri, int priority, List<Representation> representations) {

    public Viewpoint {
        representations = List.copyOf(representations);
    }

    /**
     * One representation of the viewpoint. P0 implements only diagrams; a table representation is registered
     * as a placeholder without a description provider (spec §4.2).
     */
    public record Representation(String id, String label, RepresentationKind kind) {
    }

    public enum RepresentationKind {
        DIAGRAM, TABLE
    }
}

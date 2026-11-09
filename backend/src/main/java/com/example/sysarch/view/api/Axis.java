package com.example.sysarch.view.api;

import java.util.Objects;

/**
 * VSM table {@code rows} / {@code columns} (P1 §3.3): every element of {@code type} in the model that passes
 * {@code filter} (the diagram filter grammar), labelled by {@code labelTemplate}, optionally grouped by {@code groupBy}
 * (P1: {@code layer}).
 *
 * <p>When {@code type} is the abstract root {@code ArchElement}, an axis holds only structural elements, i.e.
 * {@code PartDefinition} and {@code Part}: relationships (Allocate, Connection, ItemFlow), ports, interfaces,
 * requirements and layout records are not allocated (P1 T4, user's decision; the YAML stays verbatim).
 */
public record Axis(String type, String labelTemplate, String filter, String groupBy) {

    public Axis {
        Objects.requireNonNull(type);
    }

    public static Axis of(String type, String labelTemplate) {
        return new Axis(type, labelTemplate, null, null);
    }

    public Axis filter(String expression) {
        return new Axis(this.type, this.labelTemplate, expression, this.groupBy);
    }

    public Axis groupBy(String feature) {
        return new Axis(this.type, this.labelTemplate, this.filter, feature);
    }
}

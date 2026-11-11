package com.example.sysarch.view.api;

import java.util.Objects;

/**
 * Edge mapping of a diagram (VSM {@code edgeStyles[]}). One edge is drawn per element of {@code domainType},
 * from its {@code sourceEnd} to each element of its {@code targetEnd}.
 *
 * @param sourceEnd {@code "self"} or a reference of {@code domainType}
 * @param targetEnd {@code "self"} or a reference of {@code domainType}
 * @param when P3 T5: null, or a filter expression over the element's attributes; among alternative styles (same type
 *        and ends) the first whose {@code when} holds wins, before the {@code kind} rule
 */
public record EdgeStyle(
        String id,
        String domainType,
        String sourceEnd,
        String targetEnd,
        Routing routing,
        Arrow arrow,
        LineStyle lineStyle,
        String color,
        String labelTemplate,
        String when) {

    public static final String SELF = "self";

    public EdgeStyle {
        Objects.requireNonNull(id);
        Objects.requireNonNull(domainType);
        Objects.requireNonNull(sourceEnd);
        Objects.requireNonNull(targetEnd);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Fluent builder used by generated providers. */
    public static final class Builder {
        private final String id;
        private String domainType;
        private String sourceEnd;
        private String targetEnd;
        private Routing routing;
        private Arrow arrow = Arrow.NONE;
        private LineStyle lineStyle = LineStyle.SOLID;
        private String color;
        private String labelTemplate;
        private String when;

        private Builder(String id) {
            this.id = id;
        }

        public Builder domainType(String domainType) {
            this.domainType = domainType;
            return this;
        }

        public Builder sourceEnd(String sourceEnd) {
            this.sourceEnd = sourceEnd;
            return this;
        }

        public Builder targetEnd(String targetEnd) {
            this.targetEnd = targetEnd;
            return this;
        }

        public Builder routing(Routing routing) {
            this.routing = routing;
            return this;
        }

        public Builder arrow(Arrow arrow) {
            this.arrow = arrow;
            return this;
        }

        public Builder lineStyle(LineStyle lineStyle) {
            this.lineStyle = lineStyle;
            return this;
        }

        public Builder color(String color) {
            this.color = color;
            return this;
        }

        public Builder labelTemplate(String labelTemplate) {
            this.labelTemplate = labelTemplate;
            return this;
        }

        public Builder when(String when) {
            this.when = when;
            return this;
        }

        public EdgeStyle build() {
            return new EdgeStyle(this.id, this.domainType, this.sourceEnd, this.targetEnd, this.routing, this.arrow, this.lineStyle,
                    this.color, this.labelTemplate, this.when);
        }
    }
}

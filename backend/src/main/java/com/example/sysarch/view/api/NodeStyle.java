package com.example.sysarch.view.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Node mapping of a diagram: which elements become nodes and how they look (VSM {@code nodeStyles[]}).
 * Optional values the YAML leaves out are {@code null}, so the adapter can apply Sirius Web defaults.
 *
 * @param appliesTo element type, as {@code nsPrefix::EClass}
 * @param affiliationFeature for border nodes, the container reference to the parent node's element
 */
public record NodeStyle(
        String id,
        String appliesTo,
        Shape shape,
        String color,
        String borderColor,
        Integer borderWidth,
        boolean headerBold,
        boolean italic,
        Integer width,
        Integer height,
        boolean borderNode,
        String affiliationFeature,
        LabelPosition labelPosition,
        String labelTemplate,
        String subLabelTemplate,
        List<Compartment> compartments) {

    public NodeStyle {
        Objects.requireNonNull(id);
        Objects.requireNonNull(appliesTo);
        compartments = List.copyOf(compartments);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** A list compartment inside a node, one line per element of {@code feature}. */
    public record Compartment(String feature, String label, String itemTemplate) {
    }

    /** Fluent builder used by generated providers. */
    public static final class Builder {
        private final String id;
        private String appliesTo;
        private Shape shape;
        private String color;
        private String borderColor;
        private Integer borderWidth;
        private boolean headerBold;
        private boolean italic;
        private Integer width;
        private Integer height;
        private boolean borderNode;
        private String affiliationFeature;
        private LabelPosition labelPosition;
        private String labelTemplate;
        private String subLabelTemplate;
        private final List<Compartment> compartments = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder appliesTo(String appliesTo) {
            this.appliesTo = appliesTo;
            return this;
        }

        public Builder shape(Shape shape) {
            this.shape = shape;
            return this;
        }

        public Builder color(String color) {
            this.color = color;
            return this;
        }

        public Builder borderColor(String borderColor) {
            this.borderColor = borderColor;
            return this;
        }

        public Builder borderWidth(int borderWidth) {
            this.borderWidth = borderWidth;
            return this;
        }

        public Builder headerBold(boolean headerBold) {
            this.headerBold = headerBold;
            return this;
        }

        public Builder italic(boolean italic) {
            this.italic = italic;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Builder borderNode(String affiliationFeature) {
            this.borderNode = true;
            this.affiliationFeature = affiliationFeature;
            return this;
        }

        public Builder labelPosition(LabelPosition labelPosition) {
            this.labelPosition = labelPosition;
            return this;
        }

        public Builder labelTemplate(String labelTemplate) {
            this.labelTemplate = labelTemplate;
            return this;
        }

        public Builder subLabelTemplate(String subLabelTemplate) {
            this.subLabelTemplate = subLabelTemplate;
            return this;
        }

        public Builder compartment(String feature, String label, String itemTemplate) {
            this.compartments.add(new Compartment(feature, label, itemTemplate));
            return this;
        }

        public NodeStyle build() {
            return new NodeStyle(this.id, this.appliesTo, this.shape, this.color, this.borderColor, this.borderWidth, this.headerBold,
                    this.italic, this.width, this.height, this.borderNode, this.affiliationFeature, this.labelPosition, this.labelTemplate,
                    this.subLabelTemplate, this.compartments);
        }
    }
}

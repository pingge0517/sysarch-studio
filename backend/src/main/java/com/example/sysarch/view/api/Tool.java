package com.example.sysarch.view.api;

import java.util.Objects;

/**
 * Canvas tool (VSM {@code tools[]}). Only the fields relevant to {@code action} are set; the others are null.
 *
 * @param targetClass element type the tool creates, as {@code nsPrefix::EClass}
 * @param containmentFeature for create-child: the containment reference of the diagram root that receives it
 * @param validateOnDrop for create-edge: run constraint checks before the edge is stored (spec AC-5)
 */
public record Tool(
        String id,
        String label,
        ToolAction action,
        String targetClass,
        String containmentFeature,
        String linkType,
        boolean validateOnDrop) {

    public Tool {
        Objects.requireNonNull(id);
        Objects.requireNonNull(action);
    }

    public static Tool createChild(String id, String label, String targetClass, String containmentFeature) {
        return new Tool(id, label, ToolAction.CREATE_CHILD, targetClass, containmentFeature, null, false);
    }

    public static Tool createEdge(String id, String label, String targetClass, boolean validateOnDrop) {
        return new Tool(id, label, ToolAction.CREATE_EDGE, targetClass, null, null, validateOnDrop);
    }

    public static Tool externalLink(String id, String label, String linkType) {
        return new Tool(id, label, ToolAction.EXTERNAL_LINK, null, null, linkType, false);
    }
}

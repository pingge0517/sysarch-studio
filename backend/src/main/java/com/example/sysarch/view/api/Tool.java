package com.example.sysarch.view.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Canvas tool (VSM {@code tools[]}). Only the fields relevant to {@code action} are set; the others are null.
 *
 * @param targetClass element type the tool creates, as {@code nsPrefix::EClass}
 * @param containmentFeature for create-child: the containment reference of the diagram root that receives it; null (P3 T3):
 *        the new element becomes a root of the document that holds the diagram root
 *        For create-edge (P3 T5): null = the edge element goes to the top of the document of its source end; else the
 *        containment reference of the diagram root that receives it (a Transition in its StateMachine's {@code transitions})
 * @param validateOnDrop for create-edge: run constraint checks before the edge is stored (spec AC-5)
 * @param defaults for create-child: attribute values (as their Ecore literal) a new element gets, in YAML order
 *        (spec P1 §3.5 {@code defaults}, e.g. {@code layer = SYSTEM})
 */
public record Tool(
        String id,
        String label,
        ToolAction action,
        String targetClass,
        String containmentFeature,
        String linkType,
        boolean validateOnDrop,
        Map<String, String> defaults) {

    public Tool {
        Objects.requireNonNull(id);
        Objects.requireNonNull(action);
        defaults = Collections.unmodifiableMap(new LinkedHashMap<>(defaults == null ? Map.of() : defaults));
    }

    public static Tool createChild(String id, String label, String targetClass, String containmentFeature) {
        return new Tool(id, label, ToolAction.CREATE_CHILD, targetClass, containmentFeature, null, false, Map.of());
    }

    public static Tool createEdge(String id, String label, String targetClass, boolean validateOnDrop) {
        return new Tool(id, label, ToolAction.CREATE_EDGE, targetClass, null, null, validateOnDrop, Map.of());
    }

    /** P3 T5: an edge tool whose element is stored in the diagram root's {@code containmentFeature}. */
    public static Tool createEdge(String id, String label, String targetClass, boolean validateOnDrop, String containmentFeature) {
        return new Tool(id, label, ToolAction.CREATE_EDGE, targetClass, containmentFeature, null, validateOnDrop, Map.of());
    }

    public static Tool externalLink(String id, String label, String linkType) {
        return new Tool(id, label, ToolAction.EXTERNAL_LINK, null, null, linkType, false, Map.of());
    }

    /** This tool with one more default; the generated code chains one call per YAML entry. */
    public Tool withDefault(String feature, String literal) {
        Map<String, String> next = new LinkedHashMap<>(this.defaults);
        next.put(Objects.requireNonNull(feature), Objects.requireNonNull(literal));
        return new Tool(this.id, this.label, this.action, this.targetClass, this.containmentFeature, this.linkType, this.validateOnDrop, next);
    }
}

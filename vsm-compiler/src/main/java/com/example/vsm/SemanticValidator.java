package com.example.vsm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Cross-checks schema-valid VSM files against the metamodel (spec §4.2, T3 step 2): unknown classes, unknown
 * features and invalid reference ends are errors, never warnings. Also enforces id uniqueness and the viewpoint
 * rule: several files may share a viewpoint id only if they declare it identically.
 */
final class SemanticValidator {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]*)}");

    private static final Pattern FEATURE_PATH = Pattern.compile("[a-z][A-Za-z0-9_]*(\\.[a-z][A-Za-z0-9_]*)*");

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final Set<String> FILTER_KEYWORDS = Set.of("not", "and", "or", "true", "false");

    private final Metamodel metamodel;

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    /** viewpoint id -> first declaration. */
    private final Map<String, Declared> viewpoints = new HashMap<>();

    /** representation id -> first declaration (ids are global: they name the generated classes). */
    private final Map<String, Declared> representations = new HashMap<>();

    private String file;

    SemanticValidator(Metamodel metamodel) {
        this.metamodel = metamodel;
    }

    List<Diagnostic> diagnostics() {
        return this.diagnostics;
    }

    void check(String file, YamlNode document) {
        this.file = file;
        this.checkViewpoint(document.get("viewpoint"));
        for (YamlNode representation : document.items("representations")) {
            this.checkRepresentation(representation);
        }
    }

    private void checkViewpoint(YamlNode viewpoint) {
        String uri = viewpoint.text("metamodelUri");
        if (!this.metamodel.nsUri().equals(uri)) {
            this.error(viewpoint.get("metamodelUri").line(),
                    "metamodelUri '" + uri + "' does not match the metamodel nsURI '" + this.metamodel.nsUri() + "'");
        }
        String id = viewpoint.text("id");
        var signature = List.of(Objects.toString(viewpoint.text("label")), Objects.toString(uri),
                Objects.toString(viewpoint.text("priority")));
        Declared first = this.viewpoints.putIfAbsent(id, new Declared(this.file, viewpoint.get("id").line(), signature));
        if (first != null && !first.signature().equals(signature)) {
            this.error(viewpoint.get("id").line(), "viewpoint '" + id + "' conflicts with its declaration at " + first.location()
                    + ": label, metamodelUri and priority must be identical in every file that uses it");
        }
    }

    private void checkRepresentation(YamlNode representation) {
        String id = representation.text("id");
        Declared first = this.representations.putIfAbsent(id, new Declared(this.file, representation.get("id").line(), List.of()));
        if (first != null) {
            this.error(representation.get("id").line(), "duplicate representation id '" + id + "', first declared at " + first.location());
        }
        if (!representation.has("domainClass")) {
            return; // table placeholder without a root type; the schema enforces domainClass for diagrams
        }
        EClass domainClass = this.resolveClass(representation.get("domainClass"));
        if (domainClass == null) {
            return; // everything below is relative to the root type
        }

        for (YamlNode autoExpand : representation.items("autoExpand")) {
            this.checkAutoExpand(domainClass, autoExpand.get("feature"));
        }
        this.checkUniqueIds(representation.items("filters"), "filter");
        for (YamlNode filter : representation.items("filters")) {
            this.checkFilter(domainClass, filter.get("expression"));
        }
        this.checkUniqueIds(representation.items("nodeStyles"), "node style");
        for (YamlNode nodeStyle : representation.items("nodeStyles")) {
            this.checkNodeStyle(nodeStyle);
        }
        this.checkUniqueIds(representation.items("edgeStyles"), "edge style");
        for (YamlNode edgeStyle : representation.items("edgeStyles")) {
            this.checkEdgeStyle(edgeStyle);
        }
        this.checkUniqueIds(representation.items("tools"), "tool");
        for (YamlNode tool : representation.items("tools")) {
            this.checkTool(domainClass, tool);
        }
    }

    /** The feature may be declared on the root type or on one of its subclasses (spec §4.2). */
    private void checkAutoExpand(EClass domainClass, YamlNode feature) {
        String name = feature.scalar();
        boolean found = this.metamodel.selfAndSubclasses(domainClass).stream().anyMatch(c -> Metamodel.reference(c, name) != null);
        if (!found) {
            this.unknownFeature(feature.line(), name, domainClass, " (autoExpand needs a reference)");
        }
    }

    private void checkFilter(EClass domainClass, YamlNode expression) {
        Matcher matcher = IDENTIFIER.matcher(expression.scalar());
        while (matcher.find()) {
            String identifier = matcher.group();
            if (!FILTER_KEYWORDS.contains(identifier) && Metamodel.feature(domainClass, identifier) == null) {
                this.unknownFeature(expression.line(), identifier, domainClass, " in filter expression \"" + expression.scalar() + "\"");
            }
        }
    }

    private void checkNodeStyle(YamlNode nodeStyle) {
        EClass appliesTo = this.resolveClass(nodeStyle.get("appliesTo"));
        if (appliesTo == null) {
            return;
        }
        this.checkTemplate(appliesTo, nodeStyle.get("labelExpression"));
        this.checkTemplate(appliesTo, nodeStyle.get("subLabelExpression"));
        for (YamlNode compartment : nodeStyle.items("compartments")) {
            YamlNode featureNode = compartment.get("feature");
            EReference reference = Metamodel.reference(appliesTo, featureNode.scalar());
            if (reference == null) {
                this.unknownFeature(featureNode.line(), featureNode.scalar(), appliesTo, " (a compartment needs a reference)");
            } else {
                this.checkTemplate(reference.getEReferenceType(), compartment.get("itemExpression"));
            }
        }

        YamlNode affiliation = nodeStyle.get("affiliationFeature");
        if (nodeStyle.bool("isBorderNode")) {
            if (affiliation == null) {
                this.error(nodeStyle.keyLine("isBorderNode"), "border node '" + nodeStyle.text("id")
                        + "' needs affiliationFeature (the container reference to its parent node's element)");
            } else {
                // Spec §4.4 / §11.2 #2: a wrong parent reference makes the port float in the middle of the canvas.
                EReference reference = Metamodel.reference(appliesTo, affiliation.scalar());
                if (reference == null) {
                    this.unknownFeature(affiliation.line(), affiliation.scalar(), appliesTo, "");
                } else if (!reference.isContainer()) {
                    this.error(affiliation.line(), "affiliationFeature '" + affiliation.scalar() + "' on " + this.metamodel.ref(appliesTo)
                            + " is not a container reference (the opposite of a containment)");
                }
            }
        } else if (affiliation != null) {
            this.error(affiliation.line(), "affiliationFeature is only allowed with isBorderNode: true");
        }
    }

    private void checkEdgeStyle(YamlNode edgeStyle) {
        EClass domainClass = this.resolveClass(edgeStyle.get("domainClass"));
        if (domainClass == null) {
            return;
        }
        for (String end : List.of("sourceEnd", "targetEnd")) {
            YamlNode endNode = edgeStyle.get(end);
            String name = endNode.scalar();
            if (!"self".equals(name) && Metamodel.reference(domainClass, name) == null) {
                this.unknownFeature(endNode.line(), name, domainClass, " (" + end + " must be 'self' or a reference)");
            }
        }
        if (edgeStyle.has("label") && edgeStyle.has("labelExpression")) {
            this.error(edgeStyle.keyLine("labelExpression"), "edge style '" + edgeStyle.text("id")
                    + "' sets both label and labelExpression; use one");
        }
        this.checkTemplate(domainClass, edgeStyle.get("label"));
        this.checkTemplate(domainClass, edgeStyle.get("labelExpression"));
    }

    private void checkTool(EClass domainClass, YamlNode tool) {
        EClass targetClass = tool.has("targetClass") ? this.resolveClass(tool.get("targetClass")) : null;
        String action = tool.text("action");
        if ("create-child".equals(action)) {
            YamlNode featureNode = tool.get("containmentFeature");
            EReference reference = Metamodel.reference(domainClass, featureNode.scalar());
            if (reference == null) {
                this.unknownFeature(featureNode.line(), featureNode.scalar(), domainClass, "");
            } else if (!reference.isContainment()) {
                this.error(featureNode.line(), "containmentFeature '" + featureNode.scalar() + "' on " + this.metamodel.ref(domainClass)
                        + " is not a containment reference");
            } else if (targetClass != null && !reference.getEReferenceType().isSuperTypeOf(targetClass)) {
                this.error(tool.get("targetClass").line(), this.metamodel.ref(targetClass) + " cannot be stored in "
                        + this.metamodel.ref(domainClass) + "." + featureNode.scalar() + " (expects "
                        + this.metamodel.ref(reference.getEReferenceType()) + ")");
            }
        } else if ("create-edge".equals(action) && !tool.bool("validateOnDrop")) {
            // Spec §4.4: without validation on drop, AC-5 cannot reject an edge before it is stored.
            this.error(tool.keyLine("validateOnDrop"), "create-edge tool '" + tool.text("id") + "' must set validateOnDrop: true");
        }
    }

    /** Every {@code {path}} in a template must resolve, segment by segment, from {@code eClass}. */
    private void checkTemplate(EClass eClass, YamlNode template) {
        if (template == null) {
            return;
        }
        String text = template.scalar();
        String withoutPlaceholders = PLACEHOLDER.matcher(text).replaceAll("");
        if (withoutPlaceholders.contains("{") || withoutPlaceholders.contains("}")) {
            this.error(template.line(), "unbalanced braces in template \"" + text + "\"");
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            String path = matcher.group(1).trim();
            if (!FEATURE_PATH.matcher(path).matches()) {
                this.error(template.line(), "invalid placeholder {" + matcher.group(1) + "} in template \"" + text + "\"");
                continue;
            }
            this.checkFeaturePath(eClass, path, template, text);
        }
    }

    private void checkFeaturePath(EClass start, String path, YamlNode template, String text) {
        EClass current = start;
        String[] segments = path.split("\\.");
        for (int i = 0; i < segments.length; i++) {
            EStructuralFeature feature = Metamodel.feature(current, segments[i]);
            if (feature == null) {
                this.unknownFeature(template.line(), segments[i], current, " in template \"" + text + "\"");
                return;
            }
            if (i < segments.length - 1) {
                if (!(feature instanceof EReference reference) || reference.isMany()) {
                    this.error(template.line(), "'" + segments[i] + "' on " + this.metamodel.ref(current)
                            + " must be a single-valued reference to be followed in template \"" + text + "\"");
                    return;
                }
                current = reference.getEReferenceType();
            }
        }
    }

    private void checkUniqueIds(List<YamlNode> items, String kind) {
        Set<String> seen = new HashSet<>();
        for (YamlNode item : items) {
            String id = item.text("id");
            if (!seen.add(id)) {
                this.error(item.get("id").line(), "duplicate " + kind + " id '" + id + "' in this representation");
            }
        }
    }

    private EClass resolveClass(YamlNode refNode) {
        EClass eClass = this.metamodel.resolveClass(refNode.scalar());
        if (eClass == null) {
            this.error(refNode.line(), "unknown class '" + refNode.scalar() + "' in metamodel " + this.metamodel.nsUri());
        }
        return eClass;
    }

    private void unknownFeature(int line, String name, EClass eClass, String detail) {
        this.error(line, "unknown feature '" + name + "' on " + this.metamodel.ref(eClass) + detail);
    }

    private void error(int line, String message) {
        this.diagnostics.add(new Diagnostic(this.file, line, message));
    }

    private record Declared(String file, int line, List<String> signature) {
        String location() {
            return this.file + ":" + this.line;
        }
    }
}

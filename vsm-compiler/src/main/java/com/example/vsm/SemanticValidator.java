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

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
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
        List<FilterSyntax.Expr> layerScopes = new ArrayList<>();
        for (YamlNode filter : representation.items("filters")) {
            FilterSyntax.Expr expr = this.checkFilter(domainClass, filter.get("expression"));
            if (expr != null && isLayerScope(expr)) {
                layerScopes.add(expr);
            }
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
            this.checkTool(domainClass, tool, layerScopes);
        }
    }

    /**
     * A filter that names {@code layer} is the view's layer scope (P1 T3, user's decision): always applied, never a
     * toggle. The generated {@code Filter} says so; see {@code ViewModel}.
     */
    static boolean isLayerScope(FilterSyntax.Expr expr) {
        Set<String> fields = new HashSet<>();
        expr.fields(fields);
        return fields.contains("layer");
    }

    /** The feature may be declared on the root type or on one of its subclasses (spec §4.2). */
    private void checkAutoExpand(EClass domainClass, YamlNode feature) {
        String name = feature.scalar();
        boolean found = this.metamodel.selfAndSubclasses(domainClass).stream().anyMatch(c -> Metamodel.reference(c, name) != null);
        if (!found) {
            this.unknownFeature(feature.line(), name, domainClass, " (autoExpand needs a reference)");
        }
    }

    /** Grammar, whitelist (spec P1 §3.5), features of the root type, and enum literals. Returns null on an error. */
    private FilterSyntax.Expr checkFilter(EClass domainClass, YamlNode expression) {
        String text = expression.scalar();
        FilterSyntax.Expr expr;
        try {
            expr = FilterSyntax.parse(text);
        } catch (FilterSyntax.FilterSyntaxException e) {
            this.error(expression.line(), "invalid filter expression \"" + text + "\": " + e.getMessage());
            return null;
        }
        Set<String> fields = new java.util.TreeSet<>();
        expr.fields(fields);
        boolean ok = true;
        for (String field : fields) {
            EStructuralFeature feature = Metamodel.feature(domainClass, field);
            if (feature == null) {
                this.unknownFeature(expression.line(), field, domainClass, " in filter expression \"" + text + "\"");
                ok = false;
            }
        }
        List<FilterSyntax.Comparison> comparisons = new ArrayList<>();
        expr.comparisons(comparisons);
        for (FilterSyntax.Comparison comparison : comparisons) {
            EStructuralFeature feature = Metamodel.feature(domainClass, comparison.field());
            if (feature != null && feature.getEType() instanceof EEnum eEnum && eEnum.getEEnumLiteral(comparison.literal()) == null) {
                this.error(expression.line(), "'" + comparison.literal() + "' is not a literal of " + eEnum.getName() + " in filter expression \""
                        + text + "\" (expected one of " + eEnum.getELiterals().stream().map(l -> l.getName()).toList() + ")");
                ok = false;
            }
        }
        for (FilterSyntax.Expr flag : flags(expr)) {
            EStructuralFeature feature = Metamodel.feature(domainClass, ((FilterSyntax.Flag) flag).field());
            if (feature != null && (feature.isMany() || feature.getEType().getInstanceClass() != boolean.class
                    && feature.getEType().getInstanceClass() != Boolean.class)) {
                this.error(expression.line(), "'" + ((FilterSyntax.Flag) flag).field() + "' is not boolean; compare it, e.g. "
                        + ((FilterSyntax.Flag) flag).field() + " == '...', in filter expression \"" + text + "\"");
                ok = false;
            }
        }
        return ok ? expr : null;
    }

    private static List<FilterSyntax.Expr> flags(FilterSyntax.Expr expr) {
        List<FilterSyntax.Expr> out = new ArrayList<>();
        collectFlags(expr, out);
        return out;
    }

    private static void collectFlags(FilterSyntax.Expr expr, List<FilterSyntax.Expr> out) {
        switch (expr) {
            case FilterSyntax.Flag flag -> out.add(flag);
            case FilterSyntax.Not not -> collectFlags(not.inner(), out);
            case FilterSyntax.Binary binary -> {
                collectFlags(binary.left(), out);
                collectFlags(binary.right(), out);
            }
            default -> {
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

    private void checkTool(EClass domainClass, YamlNode tool, List<FilterSyntax.Expr> layerScopes) {
        EClass targetClass = tool.has("targetClass") ? this.resolveClass(tool.get("targetClass")) : null;
        String action = tool.text("action");
        if (tool.has("defaults")) {
            if (!"create-child".equals(action)) {
                this.error(tool.keyLine("defaults"), "defaults are only allowed on create-child tools ('" + tool.text("id") + "')");
            } else if (targetClass != null) {
                this.checkDefaults(targetClass, tool.get("defaults"));
            }
        }
        if ("create-child".equals(action) && targetClass != null && !layerScopes.isEmpty() && Metamodel.feature(targetClass, "layer") != null) {
            // P1 T3 step 4: what a layer view creates must belong to that layer, or it would vanish from the view at once.
            YamlNode defaults = tool.get("defaults");
            String layer = defaults == null || !defaults.has("layer") ? defaultLiteral(targetClass, "layer") : defaults.text("layer");
            Map<String, Object> created = Map.of("layer", layer, "isAbstract", Boolean.FALSE, "stereotypes", List.of());
            if (layerScopes.stream().anyMatch(scope -> !scope.test(created))) {
                this.error(defaults == null ? tool.get("id").line() : defaults.line(), "create-child tool '" + tool.text("id")
                        + "' creates layer " + layer + ", which this view's layer filter hides; set defaults.layer to the view's layer");
            }
        }
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

    /** Each default names a single-valued attribute of the created class and fits its type. */
    private void checkDefaults(EClass targetClass, YamlNode defaults) {
        for (String key : defaults.keys()) {
            YamlNode value = defaults.get(key);
            EStructuralFeature feature = Metamodel.feature(targetClass, key);
            if (!(feature instanceof EAttribute attribute) || attribute.isMany() || attribute.isID()) {
                this.error(value.line(), feature == null ? "unknown feature '" + key + "' on " + this.metamodel.ref(targetClass) + " in defaults"
                        : "'" + key + "' on " + this.metamodel.ref(targetClass) + " cannot have a default (only single-valued, non-id attributes)");
                continue;
            }
            String text = value.scalar();
            EDataType type = attribute.getEAttributeType();
            Class<?> javaType = type.getInstanceClass();
            boolean fits;
            if (type instanceof EEnum eEnum) {
                fits = eEnum.getEEnumLiteral(text) != null;
            } else if (javaType == boolean.class || javaType == Boolean.class) {
                fits = text.equals("true") || text.equals("false");
            } else if (javaType == int.class || javaType == Integer.class) {
                fits = text.matches("-?[0-9]+");
            } else {
                fits = javaType == String.class;
            }
            if (!fits) {
                this.error(value.line(), "default '" + text + "' does not fit " + key + " : " + type.getName() + " on " + this.metamodel.ref(targetClass)
                        + (type instanceof EEnum eEnum ? " (expected one of " + eEnum.getELiterals().stream().map(l -> l.getName()).toList() + ")" : ""));
            }
        }
    }

    private static String defaultLiteral(EClass eClass, String attribute) {
        EStructuralFeature feature = Metamodel.feature(eClass, attribute);
        return feature == null ? null : feature.getDefaultValueLiteral();
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

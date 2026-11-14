package com.example.vsm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Template data, built from schema- and metamodel-valid YAML. Enum-valued YAML strings are already mapped to
 * the Java constant names of the descriptor API ({@code hollow-triangle} becomes {@code HOLLOW_TRIANGLE}).
 * Absent optional values stay null, so the templates emit only what the YAML says.
 */
public final class ViewModel {

    public record Viewpoint(String id, String label, String metamodelUri, int priority, List<RepresentationRef> representations) {
    }

    /** @param className generated provider class (diagram or table) */
    public record RepresentationRef(String id, String label, String kind, String className) {
    }

    public record Diagram(String sourceFile, String className, String id, String label, String domainType, String synchronization,
            Integer refreshBudgetMs, List<AutoExpand> autoExpands, List<Filter> filters, List<Node> nodes, List<Edge> edges,
            List<Tool> tools, Integer partitionSize, String layoutDirection) {
    }

    public record AutoExpand(String feature, int maxDepth) {
    }

    /** P1 table: rows, columns, cell condition (as the Java that builds it) and actions. */
    public record Table(String sourceFile, String className, String id, String label, String domainType, Axis rows, Axis columns,
            String cellExpression, String cellConditionJava, String markSymbol, String markColor, List<TableAction> actions) {
    }

    public record Axis(String type, String filter, String labelTemplate, String groupBy) {
    }

    /** @param action CREATE_LINK, DELETE_LINK or LINK */
    public record TableAction(String id, String label, String action, String targetClass, boolean validateOnCreate, String feature) {
    }

    /** @param alwaysOn a layer scope (the expression names {@code layer}): always applied, no toggle (P1 T3) */
    public record Filter(String id, String label, String expression, boolean alwaysOn) {
    }

    /** @param affiliationFeature non-null exactly for border nodes */
    public record Node(String id, String appliesTo, String shape, String color, String borderColor, Integer borderWidth, boolean headerBold,
            boolean italic, Integer width, Integer height, String affiliationFeature, String labelPosition, String labelTemplate,
            String subLabelTemplate, List<Compartment> compartments, String when) {
    }

    public record Compartment(String feature, String label, String itemTemplate) {
    }

    public record Edge(String id, String domainType, String sourceEnd, String targetEnd, String routing, String arrow, String lineStyle,
            String color, String labelTemplate, String when) {
    }

    /** @param defaults create-child: attribute values a new element gets (P1 {@code defaults}), in YAML order */
    public record Tool(String id, String label, String action, String targetClass, String containmentFeature, String linkType,
            boolean validateOnDrop, List<Default> defaults) {
    }

    public record Default(String feature, String value) {
    }

    private final Map<String, Viewpoint> viewpoints = new LinkedHashMap<>();

    private final List<Diagram> diagrams = new ArrayList<>();

    private final List<Table> tables = new ArrayList<>();

    List<Viewpoint> viewpoints() {
        return List.copyOf(this.viewpoints.values());
    }

    List<Diagram> diagrams() {
        return List.copyOf(this.diagrams);
    }

    List<Table> tables() {
        return List.copyOf(this.tables);
    }

    /** Adds one validated file. Files and representations keep their order, so output is deterministic. */
    void add(String sourceFile, YamlNode document) {
        YamlNode vp = document.get("viewpoint");
        Viewpoint viewpoint = this.viewpoints.computeIfAbsent(vp.text("id"), id -> new Viewpoint(id, vp.text("label"),
                vp.text("metamodelUri"), vp.has("priority") ? vp.integer("priority") : 0, new ArrayList<>()));
        for (YamlNode representation : document.items("representations")) {
            String id = representation.text("id");
            boolean diagram = "diagram".equals(representation.text("kind"));
            String className = diagram ? className(id) : tableClassName(id);
            viewpoint.representations().add(new RepresentationRef(id, representation.text("label"), diagram ? "DIAGRAM" : "TABLE", className));
            if (diagram) {
                this.diagrams.add(diagram(sourceFile, className, representation));
            } else {
                this.tables.add(table(sourceFile, className, representation));
            }
        }
    }

    /** {@code bdd} becomes {@code BddDiagramDescriptionProvider} (spec §4.5), {@code sa-function-chain} {@code SaFunctionChain...}. */
    static String className(String representationId) {
        return camel(representationId) + "DiagramDescriptionProvider";
    }

    /** {@code allocation-matrix} becomes {@code AllocationMatrixTableDescriptionProvider}. */
    static String tableClassName(String representationId) {
        return camel(representationId) + "TableDescriptionProvider";
    }

    private static String camel(String representationId) {
        StringBuilder name = new StringBuilder();
        for (String part : representationId.split("-")) {
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }

    private static Table table(String sourceFile, String className, YamlNode t) {
        YamlNode cells = t.get("cells");
        String expression = cells.text("existsExpression");
        String java;
        try {
            java = CellSyntax.java(CellSyntax.parse(expression));
        } catch (CellSyntax.CellSyntaxException e) {
            throw new IllegalStateException(e); // validated before the model is built
        }
        return new Table(sourceFile, className, t.text("id"), t.text("label"), Metamodel.domainType(t.text("domainClass")), axis(t.get("rows")),
                axis(t.get("columns")), expression, java, cells.text("markSymbol"), cells.text("color"),
                t.items("actions").stream().map(a -> new TableAction(a.text("id"), a.text("label"), constant(a.text("action")),
                        a.text("targetClass") == null ? null : Metamodel.domainType(a.text("targetClass")), a.bool("validateOnCreate"), a.text("feature"))).toList());
    }

    private static Axis axis(YamlNode a) {
        return new Axis(Metamodel.domainType(a.text("class")), a.text("filter"), a.text("labelExpression"), a.text("groupBy"));
    }

    private static Diagram diagram(String sourceFile, String className, YamlNode r) {
        YamlNode refresh = r.get("refresh");
        return new Diagram(sourceFile, className, r.text("id"), r.text("label"), Metamodel.domainType(r.text("domainClass")),
                constant(r.text("synchronization")), refresh == null ? null : refresh.integer("budgetMs"),
                r.items("autoExpand").stream().map(a -> new AutoExpand(a.text("feature"), a.integer("maxDepth"))).toList(),
                r.items("filters").stream().map(f -> new Filter(f.text("id"), f.text("label"), f.text("expression"), layerScope(f.text("expression")))).toList(),
                java.util.stream.Stream.concat(r.items("nodeStyles").stream(), r.items("nodeStylesExtra").stream()).map(ViewModel::node).toList(), // P3 T3
                r.items("edgeStyles").stream().map(ViewModel::edge).toList(),
                r.items("tools").stream().map(ViewModel::tool).toList(), r.integer("partitionSize"), r.text("layoutDirection"));
    }

    private static Node node(YamlNode n) {
        return new Node(n.text("id"), Metamodel.domainType(n.text("appliesTo")), constant(n.text("shape")), n.text("color"),
                n.text("borderColor"), n.integer("borderWidth"), n.bool("headerBold"), n.bool("italic"), n.integer("width"),
                n.integer("height"), n.bool("isBorderNode") ? n.text("affiliationFeature") : null, constant(n.text("labelPosition")),
                n.text("labelExpression"), n.text("subLabelExpression"),
                n.items("compartments").stream().map(c -> new Compartment(c.text("feature"), c.text("label"), c.text("itemExpression"))).toList(),
                n.text("when"));
    }

    private static Edge edge(YamlNode e) {
        String label = e.has("labelExpression") ? e.text("labelExpression") : e.text("label");
        return new Edge(e.text("id"), Metamodel.domainType(e.text("domainClass")), e.text("sourceEnd"), e.text("targetEnd"),
                constant(e.text("routing")), constant(e.text("arrow")), constant(e.text("lineStyle")), e.text("color"), label, e.text("when"));
    }

    private static Tool tool(YamlNode t) {
        String targetClass = t.text("targetClass");
        return new Tool(t.text("id"), t.text("label"), constant(t.text("action")), targetClass == null ? null : Metamodel.domainType(targetClass),
                t.text("containmentFeature"), t.text("linkType"), t.bool("validateOnDrop"), defaults(t.get("defaults")));
    }

    private static List<Default> defaults(YamlNode defaults) {
        return defaults == null ? List.of() : defaults.keys().stream().map(key -> new Default(key, defaults.text(key))).toList();
    }

    /** Validated before the model is built, so the expression parses. */
    private static boolean layerScope(String expression) {
        try {
            return SemanticValidator.isLayerScope(FilterSyntax.parse(expression));
        } catch (FilterSyntax.FilterSyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String constant(String yamlValue) {
        return yamlValue == null ? null : yamlValue.toUpperCase(Locale.ROOT).replace('-', '_');
    }
}

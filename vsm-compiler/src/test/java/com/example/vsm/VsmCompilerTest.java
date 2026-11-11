package com.example.vsm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.stereotype.Component;

/**
 * T3 acceptance (spec §8): the repository's views compile, the output is deterministic and compiles, and every
 * kind of invalid view fails with {@code views/<file>:<line> <message>} and exit code 1.
 *
 * <p>Error cases edit copies of the real {@code views/*.vsm.yaml}, so the tests follow the spec files.
 */
class VsmCompilerTest {

    private static final Path REPO = Path.of(System.getProperty("sysarch.repoRoot", "..")).toAbsolutePath().normalize();

    private static final String CORE = "views/sysarch-core.vsm.yaml";

    private static final String IBD = "views/sysarch-ibd.vsm.yaml";

    private static final String SA = "views/sa-function-chain.vsm.yaml";

    private static final String LA = "views/la-architecture.vsm.yaml";

    private static final String TEMPLATE = "views/_templates/layer-architecture.vsm.tpl.yaml";

    private static final String LA_PARAMS = "views/_templates/la-architecture.params.yaml";

    private static final String MATRIX = "views/allocation-matrix.vsm.yaml";

    private static final String TRACE = "views/requirement-trace.vsm.yaml";

    /** sha256 of the spec's [COPY] block P1 §3.3. */
    private static final String SPEC_MATRIX_SHA256 = "4dc13a4c3388265e47ddf99f3f1a986af6748dc2930f6f850f214a656a43f8d6";

    /** sha256 of the spec's [COPY] block P1 §3.4. */
    private static final String SPEC_TRACE_SHA256 = "ab5ef534998b9b442f4e643e1b90647b0aabe32e89ab7d3ce5ca5c9f5e615b55";

    /** sha256 of the spec's [COPY] block P1 §3.2: the SA view must stay verbatim although a template now produces it. */
    private static final String SPEC_SA_SHA256 = "810dba23575cea7c942a8d2b098f2b108781ca51d638295ffeaca2a379134e80";

    /** sha256 of the spec's [COPY] block P3 §3.1 (views/parametric.vsm.yaml stays verbatim). */
    private static final String SPEC_PARAMETRIC_SHA256 = "1c1bc4c19dd25e2ac3c58cb0d564a430c12655df73006b3a7eface97dfe328ca";

    private static final String PARAMETRIC = "views/parametric.vsm.yaml";

    @TempDir
    Path temp;

    private Path workspace;

    @BeforeEach
    void copyInputs() throws IOException {
        this.workspace = this.temp.resolve("repo");
        List<String> inputs = new ArrayList<>(List.of("views/schema/vsm.schema.json", "metamodel/sysarch.ecore"));
        try (Stream<Path> views = Files.list(REPO.resolve("views")); Stream<Path> templates = Files.list(REPO.resolve("views/_templates"))) {
            Stream.concat(views, templates).filter(Files::isRegularFile).map(f -> REPO.relativize(f).toString().replace('\\', '/')).forEach(inputs::add);
        }
        for (String file : inputs) {
            Path target = this.workspace.resolve(file);
            Files.createDirectories(target.getParent());
            Files.copy(REPO.resolve(file), target);
        }
    }

    @Test
    void repositoryViewsCompileToProvidersAndRegistry() throws IOException {
        Result result = this.compile(this.output("out"));
        assertEquals(VsmCompiler.OK, result.exitCode(), result.err());
        assertEquals(List.of("ActivityDiagramDescriptionProvider.java", "AllocationMatrixTableDescriptionProvider.java", "BddDiagramDescriptionProvider.java", "IbdDiagramDescriptionProvider.java",
                "LaArchitectureDiagramDescriptionProvider.java",
                "PaArchitectureDiagramDescriptionProvider.java", "ParametricDiagramDescriptionProvider.java", "RequirementTraceTableDescriptionProvider.java",
                "SaFunctionChainDiagramDescriptionProvider.java", "StateMachineDiagramDescriptionProvider.java", "UseCaseDiagramDescriptionProvider.java",
                "ViewpointRegistry.java"),
                fileNames(this.output("out")));
        String bdd = Files.readString(this.output("out").resolve("BddDiagramDescriptionProvider.java"));
        assertTrue(bdd.contains(".domainType(\"sysarch::PartDefinition\")"));
        assertTrue(bdd.contains(".borderColor(\"#1A2B4A\")"));
        assertTrue(bdd.contains(".label(\"块定义图（BDD）\")"));
    }

    /** Spec §4.5: two runs give byte-identical output (CI compares hashes). */
    @Test
    void outputIsByteForByteDeterministic() throws IOException {
        assertEquals(VsmCompiler.OK, this.compile(this.output("first")).exitCode());
        assertEquals(VsmCompiler.OK, this.compile(this.output("second")).exitCode());
        assertEquals(sha256(this.output("first")), sha256(this.output("second")));
        for (String name : fileNames(this.output("first"))) {
            assertFalse(Files.readString(this.output("first").resolve(name)).contains("\r"), name + " must use LF");
        }
    }

    /** The committed generated code must equal a fresh generation, i.e. nobody edited it by hand. */
    @Test
    void committedGeneratedCodeIsUpToDate() throws IOException {
        assertEquals(VsmCompiler.OK, this.compile(this.output("fresh")).exitCode());
        Path committed = VsmCompiler.Options.forRepository(REPO).outputDir();
        assertEquals(fileNames(this.output("fresh")), fileNames(committed));
        for (String name : fileNames(committed)) {
            assertArrayEquals(Files.readAllBytes(this.output("fresh").resolve(name)), Files.readAllBytes(committed.resolve(name)),
                    name + " differs from a fresh generation; run ./gradlew :vsm-compiler:generateViews");
        }
    }

    /** The generated providers compile against the backend's descriptor API (no Sirius Web needed). */
    @Test
    void generatedCodeCompilesAgainstTheDescriptorApi() throws IOException {
        assertEquals(VsmCompiler.OK, this.compile(this.output("out")).exitCode());
        List<String> sources = new ArrayList<>();
        try (Stream<Path> api = Files.list(REPO.resolve("backend/src/main/java/com/example/sysarch/view/api"));
                Stream<Path> generated = Files.list(this.output("out"))) {
            Stream.concat(api, generated).map(Path::toString).filter(p -> p.endsWith(".java")).sorted().forEach(sources::add);
        }
        // The only library generated code may use is spring-context (@Component). Locate its jar from the class
        // itself; java.class.path is not the test classpath under every runner.
        String springContext = Path.of(URI.create(Component.class.getProtectionDomain().getCodeSource().getLocation().toString())).toString();
        List<String> arguments = new ArrayList<>(List.of("--release", "21", "-encoding", "UTF-8", "-Xlint:all", "-Werror",
                "-classpath", springContext, "-d", this.output("classes").toString()));
        arguments.addAll(sources);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        var errors = new ByteArrayOutputStream();
        int exitCode = javac.run(null, null, errors, arguments.toArray(String[]::new));
        assertEquals(0, exitCode, errors.toString(StandardCharsets.UTF_8));
    }

    /** The exact T3 acceptance case: a misspelled appliesTo fails with file name and line. */
    @Test
    void misspelledAppliesToFailsWithFileAndLine() throws IOException {
        this.edit(CORE, "appliesTo: \"sysarch:PartDefinition\"\n        shape: \"rectangle\"\n        color: \"#FFFFFF\"",
                "appliesTo: \"sysarch:PartDefintion\"\n        shape: \"rectangle\"\n        color: \"#FFFFFF\"");
        this.assertFails(CORE + ":28 unknown class 'sysarch:PartDefintion'");
    }

    /** Spec §4.5's own example message. */
    @Test
    void unknownFeatureFailsWithFileAndLine() throws IOException {
        this.edit(CORE, "      - feature: \"ownedPorts\"\n        maxDepth: 1", "      - feature: \"ownedPort\"\n        maxDepth: 1");
        this.assertFails(CORE + ":20 unknown feature 'ownedPort' on sysarch:PartDefinition");
    }

    @Test
    void unknownFeatureInLabelTemplateFails() throws IOException {
        this.edit(IBD, "{typedBy.name}", "{typedBy.nmae}");
        this.assertFails(IBD + ":29 unknown feature 'nmae' on sysarch:PartDefinition");
    }

    @Test
    void edgeEndThatIsNotAReferenceFails() throws IOException {
        this.edit(IBD, "targetEnd: \"target\"\n        routing: \"manhattan\"", "targetEnd: \"kind\"\n        routing: \"manhattan\"");
        this.assertFails(IBD + ":56 unknown feature 'kind' on sysarch:Connection (targetEnd must be 'self' or a reference)");
    }

    @Test
    void fieldOutsideTheClosedSchemaFails() throws IOException {
        this.edit(CORE, "        borderWidth: 1\n", "        borderWidth: 1\n        shadow: true\n");
        this.assertFails(CORE + ":33 schema: /representations/0/nodeStyles/0: property 'shadow' is not defined in the schema");
    }

    @Test
    void wrongValueTypeFails() throws IOException {
        this.edit(CORE, "budgetMs: 2000", "budgetMs: \"fast\"");
        this.assertFails(CORE + ":16 schema: /representations/0/refresh/budgetMs: string found, integer expected");
    }

    @Test
    void borderNodeWithoutAffiliationFails() throws IOException {
        this.edit(IBD, "        affiliationFeature: \"partOwner\"\n", "");
        this.assertFails(IBD + ":37 border node 'portOnPart' needs affiliationFeature");
    }

    @Test
    void createChildIntoNonContainmentFails() throws IOException {
        this.edit(CORE, "containmentFeature: \"ownedPorts\"", "containmentFeature: \"superTypes\"");
        this.assertFails(CORE + ":74 containmentFeature 'superTypes' on sysarch:PartDefinition is not a containment reference");
    }

    @Test
    void createEdgeWithoutValidationOnDropFails() throws IOException {
        this.edit(IBD, "validateOnDrop: true", "validateOnDrop: false");
        this.assertFails(IBD + ":85 create-edge tool 'connect' must set validateOnDrop: true");
    }

    /** Both spec files declare viewpoint sysarch.core identically, which is allowed; a differing copy is a conflict. */
    @Test
    void conflictingViewpointDeclarationFails() throws IOException {
        this.edit(IBD, "  label: \"系统架构建模\"", "  label: \"另一个视点\"");
        this.assertFails(IBD + ":3 viewpoint 'sysarch.core' conflicts with its declaration at views/"); // whichever file declares it first
    }

    @Test
    void duplicateRepresentationIdFails() throws IOException {
        this.edit(IBD, "  - id: \"ibd\"", "  - id: \"bdd\"");
        this.assertFails(IBD + ":9 duplicate representation id 'bdd', first declared at " + CORE + ":9");
    }

    @Test
    void metamodelUriMismatchFails() throws IOException {
        this.edit(CORE, "metamodelUri: \"http://example.com/sysarch/1.0\"", "metamodelUri: \"http://example.com/sysarch/2.0\"");
        this.assertFails(CORE + ":5 metamodelUri 'http://example.com/sysarch/2.0' does not match the metamodel nsURI");
    }

    @Test
    void yamlSyntaxErrorFailsWithLine() throws IOException {
        this.edit(CORE, "    kind: \"diagram\"", "    kind: [\"diagram\"");
        Result result = this.compile(this.output("out"));
        assertEquals(VsmCompiler.INVALID_VIEWS, result.exitCode());
        assertTrue(result.err().contains(CORE + ":"), result.err());
        assertTrue(result.err().contains("YAML syntax error"), result.err());
    }

    /** A failing run must not delete or change the previous output. */
    @Test
    void failedRunLeavesPreviousOutputUntouched() throws IOException {
        assertEquals(VsmCompiler.OK, this.compile(this.output("out")).exitCode());
        String before = sha256(this.output("out"));
        this.edit(CORE, "targetEnd: \"superTypes\"", "targetEnd: \"superType\"");
        assertEquals(VsmCompiler.INVALID_VIEWS, this.compile(this.output("out")).exitCode());
        assertEquals(before, sha256(this.output("out")));
    }

    @Test
    void changingAStyleInYamlOnlyChangesTheGeneratedStyle() throws IOException {
        // AC-3: change the block border colour in YAML, rerun the generator, nothing else changes.
        assertEquals(VsmCompiler.OK, this.compile(this.output("before")).exitCode());
        this.edit(CORE, "borderColor: \"#1A2B4A\"\n        borderWidth: 1", "borderColor: \"#1C3D2E\"\n        borderWidth: 1");
        assertEquals(VsmCompiler.OK, this.compile(this.output("after")).exitCode());
        String before = Files.readString(this.output("before").resolve("BddDiagramDescriptionProvider.java"));
        String after = Files.readString(this.output("after").resolve("BddDiagramDescriptionProvider.java"));
        assertNotEquals(before, after);
        assertEquals(before.replaceFirst("\\.borderColor\\(\"#1A2B4A\"\\)", ".borderColor(\"#1C3D2E\")"), after);
    }

    // =========================================================== P3 T3: the parametric view's DSL additions (user's decisions)

    /**
     * The verbatim YAML compiles: nodeStylesExtra continues nodeStyles, "eContainer" puts the parameters on the block that
     * contains them, a create-child tool without containmentFeature creates a document root.
     */
    @Test
    void theParametricViewIsVerbatimAndCompiles() throws IOException {
        assertEquals(SPEC_PARAMETRIC_SHA256, sha256File(REPO.resolve(PARAMETRIC)));
        Result result = this.compile(this.output("out"));
        assertEquals(VsmCompiler.OK, result.exitCode(), result.err());
        String parametric = Files.readString(this.output("out").resolve("ParametricDiagramDescriptionProvider.java"));
        assertTrue(parametric.contains(".borderNode(\"eContainer\")"), "the extra style is a border node on its container");
        assertTrue(parametric.contains("Tool.createChild(\"createConstraint\", \"新建约束块\", \"sysarch::ConstraintBlock\", null)"), "no containment feature: a root");
        assertTrue(parametric.indexOf("\"constraintBlock\"") < parametric.indexOf("\"parameter\""), "nodeStylesExtra after nodeStyles");
    }

    @Test
    void nodeStyleIdsAreUniqueAcrossNodeStylesAndNodeStylesExtra() throws IOException {
        this.edit(PARAMETRIC, "      - id: \"parameter\"", "      - id: \"constraintBlock\"");
        this.assertFails(PARAMETRIC + ":32 duplicate node style id 'constraintBlock'");
    }

    @Test
    void eContainerNeedsAClassSomethingCanContain() throws IOException {
        this.edit(PARAMETRIC, "        appliesTo: \"sysarch:Parameter\"", "        appliesTo: \"sysarch:BindingConnector\"");
        this.assertFails(PARAMETRIC + ":38 affiliationFeature 'eContainer': no containment reference can hold sysarch:BindingConnector");
    }

    // =========================================================== P3 T5: state machine (behaviour template, when, circles)

    private static final String STATE_MACHINE = "views/state-machine.vsm.yaml";

    /** The state machine comes from the behaviour template; when, the circle shapes and the edge containment are generated. */
    @Test
    void theStateMachineViewCompilesFromTheBehaviourTemplate() throws IOException {
        Result result = this.compile(this.output("out"));
        assertEquals(VsmCompiler.OK, result.exitCode(), result.err());
        String generated = Files.readString(this.output("out").resolve("StateMachineDiagramDescriptionProvider.java"));
        assertTrue(generated.contains(".shape(Shape.FILLED_CIRCLE)") && generated.contains(".shape(Shape.DOUBLE_CIRCLE)"), generated);
        assertTrue(generated.contains(".when(\"isInitial\")") && generated.contains(".when(\"isFinal\")"));
        assertTrue(generated.contains(".labelTemplate(\"{trigger}[{guard}]/{effect}\")"));
        assertTrue(generated.contains("Tool.createEdge(\"connect\", \"新建迁移\", \"sysarch::Transition\", true, \"transitions\")"), "stored in the machine");
        assertTrue(generated.contains(".withDefault(\"isInitial\", \"true\")"));
    }

    /**
     * P3 T6: the three behaviour views share one template; a parameter alone on its line is a YAML block indented like the
     * placeholder (more styles, more tools), and an empty one removes the line (use cases and actors have no containment).
     */
    @Test
    void theBehaviourViewsShareOneTemplate() throws IOException {
        Result result = this.compile(this.output("out"));
        assertEquals(VsmCompiler.OK, result.exitCode(), result.err());
        String activity = Files.readString(this.output("out").resolve("ActivityDiagramDescriptionProvider.java"));
        assertTrue(activity.contains(".shape(Shape.DIAMOND)") && activity.contains(".when(\"kind == 'DECIDE'\")"), activity);
        assertTrue(activity.contains(".lineStyle(LineStyle.DASHED)") && activity.contains(".when(\"isObjectFlow\")"));
        assertTrue(activity.contains(".withDefault(\"kind\", \"DECIDE\")"));
        assertTrue(activity.contains(".partitionSize(200)"), "spec §9: at most 200 nodes at once");
        String useCase = Files.readString(this.output("out").resolve("UseCaseDiagramDescriptionProvider.java"));
        assertTrue(useCase.contains(".shape(Shape.ELLIPSE)") && useCase.contains(".shape(Shape.STICK_FIGURE)") && useCase.contains(".arrow(Arrow.NONE)"));
        assertTrue(useCase.contains("Tool.createChild(\"createActor\", \"新建参与者\", \"sysarch::Actor\", null)"), "a root: no containment line");
        assertTrue(useCase.contains("Tool.createEdge(\"associate\", \"关联参与者\", \"sysarch::UseCase\", true))"));
        for (String view : List.of("state-machine", "activity", "use-case")) {
            assertTrue(Files.readString(this.workspace.resolve("views/_templates/" + view + ".params.yaml")).contains("template: \"behavior.vsm.tpl.yaml\""));
        }
    }

    @Test
    void aWhenMayOnlyNameAttributesOfItsClass() throws IOException {
        this.edit(STATE_MACHINE, "        when: \"isInitial\"", "        when: \"layer == 'SYSTEM' and source\"");
        this.assertFails(STATE_MACHINE + ":31 invalid when \"layer == 'SYSTEM' and source\": 'source' is not allowed: it is not an attribute of the element");
    }

    @Test
    void aWhenFlagMustBeBoolean() throws IOException {
        this.edit(STATE_MACHINE, "        when: \"isInitial\"", "        when: \"doBehavior\"");
        this.assertFails(STATE_MACHINE + ":31 'doBehavior' is not boolean");
    }

    @Test
    void anEdgeContainmentMustHoldTheEdge() throws IOException {
        this.edit(STATE_MACHINE, "        containmentFeature: \"transitions\"", "        containmentFeature: \"states\"");
        this.assertFails(STATE_MACHINE + ":79 sysarch:Transition cannot be stored in sysarch:StateMachine.states");
    }

    // =========================================================== P1 T3: layer views, filters, defaults, templates

    /** The SA view is the spec's [COPY] text byte for byte, and the template reproduces it (no "changed" on expansion). */
    @Test
    void theSaViewIsVerbatimAndEqualsItsTemplateExpansion() throws IOException {
        assertEquals(SPEC_SA_SHA256, sha256File(REPO.resolve(SA)));
        assertEquals(VsmCompiler.OK, this.compile(this.output("out")).exitCode(), "committed views equal their expansions");
    }

    /** LA and PA differ from SA only in parameters: layer, filter, colours and labels (spec P1 §3.1). */
    @Test
    void layerViewsShareOneStructure() throws IOException {
        String sa = Files.readString(REPO.resolve(SA));
        String la = Files.readString(REPO.resolve(LA));
        assertEquals(sa.lines().count(), la.lines().count());
        assertTrue(la.contains("expression: \"layer == 'LOGICAL'\""));
        assertTrue(la.contains("          layer: \"LOGICAL\""));
    }

    @Test
    void aLayerFilterIsGeneratedAsAlwaysOnAndOtherFiltersAreToggles() throws IOException {
        assertEquals(VsmCompiler.OK, this.compile(this.output("out")).exitCode());
        String la = Files.readString(this.output("out").resolve("LaArchitectureDiagramDescriptionProvider.java"));
        String bdd = Files.readString(this.output("out").resolve("BddDiagramDescriptionProvider.java"));
        assertTrue(la.contains(".addFilter(Filter.alwaysOn(\"onlyLogical\", \"仅逻辑层\", \"layer == 'LOGICAL'\"))"), la);
        assertTrue(la.contains(".withDefault(\"layer\", \"LOGICAL\")"), la);
        assertTrue(bdd.contains(".addFilter(new Filter(\"hideAbstract\", \"隐藏抽象块\", \"not isAbstract\"))"), bdd);
    }

    /** Spec P1 §3.5: an expression may only name layer, isAbstract and stereotypes. */
    @Test
    void filterExpressionsOutsideTheWhitelistFail() throws IOException {
        this.edit(SA, "expression: \"layer == 'SYSTEM'\"", "expression: \"self.getOwner().getLayer() == 'SYSTEM'\"");
        this.assertFails(SA + ":20 invalid filter expression");
    }

    @Test
    void aFeatureThatIsNotWhitelistedFailsEvenIfItExists() throws IOException {
        this.edit(CORE, "expression: \"not isAbstract\"", "expression: \"not name\"");
        this.assertFails(CORE + ":25 invalid filter expression \"not name\": 'name' is not allowed");
    }

    @Test
    void anUnknownLayerLiteralFails() throws IOException {
        this.edit(SA, "expression: \"layer == 'SYSTEM'\"", "expression: \"layer == 'SYSTM'\"");
        this.assertFails(SA + ":20 'SYSTM' is not a literal of Layer");
    }

    @Test
    void stereotypesAreComparedNotUsedAsAFlag() throws IOException {
        this.edit(CORE, "expression: \"not isAbstract\"", "expression: \"stereotypes\"");
        this.assertFails(CORE + ":25 'stereotypes' is not boolean");
    }

    @Test
    void aDefaultMustFitItsAttribute() throws IOException {
        this.edit(SA, "          layer: \"SYSTEM\"", "          layer: \"SYS\"");
        this.assertFails(SA + ":56 default 'SYS' does not fit layer : Layer");
    }

    @Test
    void aDefaultOnAnUnknownFeatureFails() throws IOException {
        this.edit(SA, "          layer: \"SYSTEM\"", "          layer: \"SYSTEM\"\n          colour: \"red\"");
        this.assertFails(SA + ":57 unknown feature 'colour' on sysarch:Part in defaults");
    }

    /** T3 step 4: a layer view must not create elements its own layer filter hides. */
    @Test
    void aLayerViewToolMustCreateItsOwnLayer() throws IOException {
        this.edit(SA, "          layer: \"SYSTEM\"", "          layer: \"PHYSICAL\"");
        this.assertFails(SA + ":56 create-child tool 'createFunction' creates layer PHYSICAL, which this view's layer filter hides");
    }

    @Test
    void aHandEditedGeneratedViewFails() throws IOException {
        this.edit(LA, "borderColor: \"#2F5D4A\"", "borderColor: \"#000000\"");
        this.assertFails(LA + ":1 differs from its template expansion (" + LA_PARAMS + ")");
    }

    @Test
    void anUnknownTemplateParameterFails() throws IOException {
        this.edit(TEMPLATE, "label: \"${createLabel}\"", "label: \"${createLable}\"");
        this.assertFails(TEMPLATE + ":51 unknown parameter 'createLable'");
    }

    @Test
    void anUnusedTemplateParameterFails() throws IOException {
        this.edit(LA_PARAMS, "  connectLabel: \"建立逻辑交互\"", "  connectLabel: \"建立逻辑交互\"\n  arrowColor: \"#000000\"");
        this.assertFails(LA_PARAMS + ":17 parameter 'arrowColor' is not used");
    }

    /** Templates substitute parameters and nothing else: no conditions (T3 step 1). */
    @Test
    void templateLogicFails() throws IOException {
        this.edit(TEMPLATE, "        shape: \"rectangle\"", "        shape: \"${layer == 'SYSTEM' ? 'rectangle' : 'square'}\"");
        this.assertFails(TEMPLATE + ":36 '${layer == 'SYSTEM' ? 'rectangle' : 'square'}' is not a parameter");
    }

    // =========================================================== P1 T4: tables

    @Test
    void theAllocationMatrixIsVerbatimAndCompilesToATableProvider() throws IOException {
        assertEquals(SPEC_MATRIX_SHA256, sha256File(REPO.resolve(MATRIX)));
        assertEquals(VsmCompiler.OK, this.compile(this.output("out")).exitCode());
        String table = Files.readString(this.output("out").resolve("AllocationMatrixTableDescriptionProvider.java"));
        assertTrue(table.contains(".cells(CellCondition.and(CellCondition.equalTo(\"from\", CellCondition.Side.ROW), "
                + "CellCondition.equalTo(\"to\", CellCondition.Side.COLUMN)),"), table);
        assertTrue(table.contains(".rows(Axis.of(\"sysarch::ArchElement\", \"{name}\").filter(\"layer == 'SYSTEM' or layer == 'LOGICAL'\").groupBy(\"layer\"))"), table);
        assertTrue(table.contains(".addAction(TableAction.createLink(\"createAllocate\", \"建立分配\", \"sysarch::Allocate\", true))"), table);
        String registry = Files.readString(this.output("out").resolve("ViewpointRegistry.java"));
        assertTrue(registry.contains("tablesById.put(\"allocation-matrix\", allocationMatrixTableProvider);"), registry);
    }

    /** T5's [COPY] view uses the other half of the cell grammar (self, ->includes, link actions) and compiles as is. */
    @Test
    void theRequirementTraceIsVerbatimAndCompiles() throws IOException {
        assertEquals(SPEC_TRACE_SHA256, sha256File(REPO.resolve(TRACE)));
        Result result = this.compile(this.output("out"));
        assertEquals(VsmCompiler.OK, result.exitCode(), result.err());
        String trace = Files.readString(this.output("out").resolve("RequirementTraceTableDescriptionProvider.java"));
        assertTrue(trace.contains("CellCondition.or(CellCondition.includes(\"satisfiedBy\", CellCondition.Side.COLUMN), "
                + "CellCondition.includes(\"verifiedBy\", CellCondition.Side.COLUMN))"), trace);
        assertTrue(trace.contains(".addAction(TableAction.link(\"satisfy\", \"建立满足关系\", \"satisfiedBy\"))"), trace);
    }

    @Test
    void anUnknownFeatureInACellExpressionFails() throws IOException {
        this.edit(MATRIX, "from == row and to == column", "form == row and to == column");
        this.assertFails(MATRIX + ":24 unknown feature 'form' on sysarch:Allocate");
    }

    @Test
    void aMalformedCellExpressionFails() throws IOException {
        this.edit(MATRIX, "from == row and to == column", "from = row");
        this.assertFails(MATRIX + ":24 invalid existsExpression");
    }

    /** Spec P1 §3.3: the table must go through the constraints like the canvas. */
    @Test
    void createLinkWithoutValidationFails() throws IOException {
        this.edit(MATRIX, "        validateOnCreate: true", "        validateOnCreate: false");
        this.assertFails(MATRIX + ":32 create-link 'createAllocate' must set validateOnCreate: true");
    }

    @Test
    void createLinkNeedsAnExpressionItCanSatisfy() throws IOException {
        this.edit(MATRIX, "from == row and to == column", "from == row or to == column");
        this.assertFails(MATRIX + ":30 create-link 'createAllocate' can only create what existsExpression describes");
    }

    @Test
    void groupingByAnythingButLayerFails() throws IOException {
        this.edit(MATRIX, "      groupBy: \"layer\"\n    columns:", "      groupBy: \"name\"\n    columns:");
        this.assertFails(MATRIX + ":17 groupBy 'name' is not supported");
    }

    @Test
    void axisFiltersUseTheWhitelist() throws IOException {
        this.edit(MATRIX, "filter: \"layer == 'SYSTEM' or layer == 'LOGICAL'\"", "filter: \"name == 'x'\"");
        this.assertFails(MATRIX + ":15 invalid filter expression \"name == 'x'\": 'name' is not allowed");
    }

    @Test
    void diagramFieldsAreNotAllowedOnTables() throws IOException {
        this.edit(MATRIX, "    kind: \"table\"\n", "    kind: \"table\"\n    synchronization: \"auto\"\n");
        this.assertFails(MATRIX + ":12 schema");
    }

    private void assertFails(String expectedLinePrefix) throws IOException {
        Path out = this.output("rejected");
        Result result = this.compile(out);
        assertEquals(VsmCompiler.INVALID_VIEWS, result.exitCode(), result.err());
        assertTrue(result.err().lines().anyMatch(line -> line.startsWith(expectedLinePrefix)),
                "expected a line starting with:\n  " + expectedLinePrefix + "\nbut got:\n" + result.err());
        assertFalse(Files.exists(out), "nothing may be generated when the views are invalid");
    }

    private void edit(String file, String from, String to) throws IOException {
        Path path = this.workspace.resolve(file);
        String content = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(content.contains(from), "test setup: '" + from + "' not found in " + file);
        Files.writeString(path, content.replaceFirst(java.util.regex.Pattern.quote(from), java.util.regex.Matcher.quoteReplacement(to)),
                StandardCharsets.UTF_8);
    }

    private Path output(String name) {
        return this.temp.resolve(name);
    }

    private Result compile(Path outputDir) {
        Path root = this.workspace;
        var options = new VsmCompiler.Options(root.resolve("views"), root.resolve("views/schema/vsm.schema.json"),
                root.resolve("metamodel/sysarch.ecore"), outputDir, root);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int exitCode = VsmCompiler.run(options, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(exitCode, err.toString(StandardCharsets.UTF_8));
    }

    private static List<String> fileNames(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private static String sha256File(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(Path dir) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String name : fileNames(dir)) {
                digest.update(name.getBytes(StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(dir.resolve(name)));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Result(int exitCode, String err) {
    }
}

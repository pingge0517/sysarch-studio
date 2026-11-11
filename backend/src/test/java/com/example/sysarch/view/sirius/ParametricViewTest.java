package com.example.sysarch.view.sirius;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.model.BindingConnector;
import com.example.sysarch.model.ConstraintBlock;
import com.example.sysarch.model.ConstraintExpr;
import com.example.sysarch.model.Parameter;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.validation.ChangeGuard;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.validation.ValidationError;
import com.example.sysarch.view.generated.ParametricDiagramDescriptionProvider;
import com.example.sysarch.view.projection.DiagramProjector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.components.diagrams.components.DiagramComponent;
import org.eclipse.sirius.components.diagrams.components.DiagramComponentProps;
import org.eclipse.sirius.components.diagrams.description.DiagramDescription;
import org.eclipse.sirius.components.diagrams.description.EdgeDescription;
import org.eclipse.sirius.components.diagrams.renderer.DiagramRenderer;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.components.representations.Element;
import org.eclipse.sirius.components.representations.IStatus;
import org.eclipse.sirius.components.representations.Success;
import org.eclipse.sirius.components.representations.VariableManager;
import org.eclipse.sirius.components.diagrams.tools.SingleClickOnDiagramElementTool;
import org.eclipse.sirius.components.diagrams.tools.SingleClickOnTwoDiagramElementsTool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * P3 T3 (spec §3.1 verbatim YAML): the parametric view. Acceptance: "3 个约束块、6 个参数、4 条绑定可完整绘制并保存";
 * parameters are border nodes, the compartment shows direction/name/unit; binding to a parameter of another unit is
 * refused on drop and rolled back on save (user's decision, like port connections).
 */
class ParametricViewTest {

    private static final SysarchFactory F = SysarchFactory.eINSTANCE;

    private static final ConstraintValidator VALIDATOR = new ConstraintValidator();

    private static final IIdentityService IDS = new IIdentityService() {
        @Override
        public String getId(Object object) {
            return object instanceof EObject element ? DiagramProjector.elementId(element) : null;
        }

        @Override
        @SuppressWarnings("removal")
        public String getKind(Object object) {
            return object instanceof EObject element ? "sysarch::" + element.eClass().getName() : "";
        }
    };

    @AfterAll
    static void dispose() {
        VALIDATOR.close();
    }

    private final SiriusDiagramDescriptionFactory factory = new SiriusDiagramDescriptionFactory(SysarchPackage.eINSTANCE, IDS, new ProjectionCache(),
            new DiagramFilterState(), VALIDATOR);

    private final DiagramDescription parametric = this.factory.create(new ParametricDiagramDescriptionProvider().build());

    private ResourceSet set;

    private Resource model;

    private ConstraintBlock power;

    @BeforeEach
    void fixture() {
        this.set = new ResourceSetImpl();
        this.model = new org.eclipse.emf.ecore.resource.impl.ResourceImpl(URI.createURI("memory:/parametric.json"));
        this.set.getResources().add(this.model);
        // 3 constraint blocks, 6 parameters, 4 bindings (spec T3 acceptance)
        this.power = block("CB-1", "电功率", "P = U * I", param("P-1", "IN", "U", "V"), param("P-2", "OUT", "P", "W"));
        ConstraintBlock loss = block("CB-2", "损耗", "L = P * k", param("P-3", "IN", "P", "W"), param("P-4", "OUT", "L", "W"));
        ConstraintBlock heat = block("CB-3", "温升", "T = L * r", param("P-5", "IN", "L", "W"), param("P-6", "OUT", "dT", "K"));
        this.model.getContents().addAll(List.of(this.power, loss, heat));
        this.model.getContents().addAll(List.of(bind("B-1", "P-2", "P-3"), bind("B-2", "P-4", "P-5"), bind("B-3", "P-2", "P-5"), bind("B-4", "P-3", "P-5")));
    }

    @Test
    void threeBlocksSixParametersFourBindingsAreDrawn() {
        Diagram diagram = this.render(this.power);
        List<Node> blocks = diagram.getNodes();
        assertEquals(List.of("电功率", "损耗", "温升"), blocks.stream().map(ParametricViewTest::firstLine).toList(), "every constraint block of the document");
        assertEquals(6, blocks.stream().mapToInt(b -> b.getBorderNodes().size()).sum(), "parameters are border nodes");
        assertEquals(List.of("U", "P"), blocks.get(0).getBorderNodes().stream().map(ParametricViewTest::firstLine).toList(), "on their own block");
        assertEquals(4, diagram.getEdges().size(), "the four bindings, parameter to parameter");
        String label = blocks.get(0).getInsideLabel().getText();
        assertTrue(label.contains("IN U : V") && label.contains("OUT P : W"), "compartment: {direction} {name} : {unit} — " + label);
    }

    /** P3 T4: a solved value (written to defaultValue) is shown after the parameter's name; no value, no suffix. */
    @Test
    void aParametersValueIsShownOnTheDiagram() {
        this.param("P-2").setDefaultValue(90.0);
        this.param("P-6").setDefaultValue(0.0001234567);
        List<String> labels = this.render(this.power).getNodes().stream().flatMap(b -> b.getBorderNodes().stream()).map(ParametricViewTest::firstLine).toList();
        assertEquals(List.of("U", "P = 90", "P", "L", "L", "dT = 0.0001235"), labels);
    }

    @Test
    void theDiagramSurvivesSaveAndReload() throws Exception {
        Resource reloaded = this.saveAndReload();
        ConstraintBlock root = (ConstraintBlock) reloaded.getContents().get(0);
        Diagram diagram = this.render(root);
        assertEquals(3, diagram.getNodes().size());
        assertEquals(6, diagram.getNodes().stream().mapToInt(b -> b.getBorderNodes().size()).sum());
        assertEquals(4, diagram.getEdges().size());
    }

    @Test
    void bindingParametersOfTheSameUnitCreatesABinding() {
        Parameter p = this.param("P-4"); // L [W] -> P [W]: a pair not bound yet (a second binding of the same pair is drawn once, like connections)
        Parameter l = this.param("P-3");
        IStatus status = this.bind(p, l);
        assertTrue(status instanceof Success);
        assertTrue(((Success) status).getMessages().isEmpty(), "no refusal");
        BindingConnector created = (BindingConnector) this.model.getContents().get(this.model.getContents().size() - 1);
        assertEquals(p, created.getSource());
        assertEquals(l, created.getTarget());
        assertEquals("L → P", created.getName());
        assertEquals(5, this.render(this.power).getEdges().size());
    }

    @Test
    void bindingParametersOfDifferentUnitsIsRefusedBeforeAnythingIsCreated() {
        int before = this.model.getContents().size();
        Parameter voltage = this.param("P-1");
        Parameter heat = this.param("P-6");
        IStatus status = this.bind(voltage, heat);
        assertEquals(before, this.model.getContents().size(), "nothing created");
        assertTrue(status instanceof Success, "a Success carrying the reasons, so Sirius refreshes and the two ends turn red");
        assertEquals(List.of("单位不一致：U [V] → dT [K]；只能绑定单位相同的参数（不做单位换算）"),
                ((Success) status).getMessages().stream().map(m -> m.body()).toList());
        List<Node> borders = this.render(this.power).getNodes().stream().flatMap(b -> b.getBorderNodes().stream()).toList();
        long red = borders.stream().filter(n -> n.getStyle() instanceof org.eclipse.sirius.components.diagrams.RectangularNodeStyle style
                && DropRejections.REJECTED_BORDER_COLOR.equals(style.getBorderColor())).count();
        assertEquals(2, red, "both ends marked");
    }

    @Test
    void changingAUnitSoABindingNoLongerMatchesIsVetoedOnSave() {
        ChangeRecorder recorder = new ChangeRecorder(this.set);
        this.param("P-3").setUnit("kW"); // B-1 (P-2 [W] → P-3) would now mix units
        ChangeDescription change = recorder.endRecording();
        recorder.dispose();
        List<ValidationError> vetoed = new ChangeGuard(VALIDATOR).check(this.set, change);
        assertEquals(List.of("UnitsMatch"), vetoed.stream().map(ValidationError::code).distinct().toList());
        assertTrue(vetoed.stream().anyMatch(e -> e.message().equals("单位不一致：P [W] → P [kW]；只能绑定单位相同的参数（不做单位换算）")),
                vetoed.toString());
    }

    @Test
    void theCreateToolAddsAConstraintBlockToTheDocument() {
        SingleClickOnDiagramElementTool create = SysarchPaletteProvider.allTools(this.parametric).filter(t -> t.getId().endsWith("/tool/createConstraint"))
                .map(SingleClickOnDiagramElementTool.class::cast).findFirst().orElseThrow();
        assertTrue(create.isAppliesToDiagramRoot(), "offered on the diagram background");
        VariableManager vm = new VariableManager();
        vm.put(VariableManager.SELF, this.power);
        assertTrue(create.getHandler().apply(vm) instanceof Success);
        EObject created = this.model.getContents().get(this.model.getContents().size() - 1);
        assertTrue(created instanceof ConstraintBlock && ((ConstraintBlock) created).getName().equals("constraintBlock4"), String.valueOf(created));
        assertEquals(4, this.render(this.power).getNodes().size());
    }

    // ------------------------------------------------------------------ helpers

    private IStatus bind(Parameter source, Parameter target) {
        SingleClickOnTwoDiagramElementsTool tool = SysarchPaletteProvider.allTools(this.parametric).filter(SingleClickOnTwoDiagramElementsTool.class::isInstance)
                .map(SingleClickOnTwoDiagramElementsTool.class::cast).findFirst().orElseThrow();
        VariableManager vm = new VariableManager();
        vm.put(EdgeDescription.SEMANTIC_EDGE_SOURCE, source);
        vm.put(EdgeDescription.SEMANTIC_EDGE_TARGET, target);
        return tool.getHandler().apply(vm);
    }

    private Diagram render(EObject root) {
        VariableManager vm = new VariableManager();
        vm.put(VariableManager.SELF, root);
        vm.put(DiagramContext.DIAGRAM_CONTEXT, null);
        DiagramComponentProps props = DiagramComponentProps.newDiagramComponentProps().variableManager(vm).diagramDescription(this.parametric)
                .allDiagramDescriptions(List.of(this.parametric)).operationValidator((operation, variables) -> { }).viewCreationRequests(List.of())
                .viewDeletionRequests(List.of()).previousDiagram(Optional.empty()).diagramEvents(List.of()).nodeAppearanceHandlers(List.of())
                .edgeAppearanceHandlers(List.of()).build();
        return new DiagramRenderer().render(new Element(DiagramComponent.class, props));
    }

    private Resource saveAndReload() throws Exception {
        ResourceSet out = new ResourceSetImpl();
        out.getPackageRegistry().put(SysarchPackage.eNS_URI, SysarchPackage.eINSTANCE);
        Resource json = new JSONResourceFactory().createResourceFromPath("parametric");
        out.getResources().add(json);
        json.getContents().addAll(this.model.getContents());
        var bytes = new ByteArrayOutputStream();
        json.save(bytes, Map.of());
        ResourceSet in = new ResourceSetImpl();
        in.getPackageRegistry().put(SysarchPackage.eNS_URI, SysarchPackage.eINSTANCE);
        Resource loaded = new JSONResourceFactory().createResourceFromPath("loaded");
        in.getResources().add(loaded);
        loaded.load(new ByteArrayInputStream(bytes.toString(StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8)), Map.of());
        return loaded;
    }

    private Parameter param(String id) {
        return (Parameter) Stream.of(this.model).flatMap(r -> java.util.stream.StreamSupport.stream(
                java.util.Spliterators.spliteratorUnknownSize(r.getAllContents(), 0), false))
                .filter(e -> e instanceof Parameter p && id.equals(p.getId())).findFirst().orElseThrow();
    }

    private BindingConnector bind(String id, String source, String target) {
        BindingConnector binding = F.createBindingConnector();
        binding.setId(id);
        binding.setName(id);
        binding.setSource(this.find(source));
        binding.setTarget(this.find(target));
        return binding;
    }

    private Parameter find(String id) {
        return this.model.getContents().stream().filter(ConstraintBlock.class::isInstance).map(ConstraintBlock.class::cast)
                .flatMap(b -> b.getParameters().stream()).filter(p -> id.equals(p.getId())).findFirst().orElseThrow();
    }

    private static ConstraintBlock block(String id, String name, String body, Parameter... parameters) {
        ConstraintBlock block = F.createConstraintBlock();
        block.setId(id);
        block.setName(name);
        ConstraintExpr expression = F.createConstraintExpr();
        expression.setBody(body);
        block.setExpression(expression);
        block.getParameters().addAll(List.of(parameters));
        return block;
    }

    private static Parameter param(String id, String direction, String name, String unit) {
        Parameter p = F.createParameter();
        p.setId(id);
        p.setName(name);
        p.setDirection(direction);
        p.setUnit(unit);
        return p;
    }

    private static String firstLine(Node node) {
        return node.getInsideLabel() != null ? node.getInsideLabel().getText().split("\n")[0]
                : node.getOutsideLabels().isEmpty() ? "" : node.getOutsideLabels().get(0).text();
    }
}

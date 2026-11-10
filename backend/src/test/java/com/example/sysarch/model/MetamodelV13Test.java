package com.example.sysarch.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.baseline.ModelSnapshot;
import com.example.sysarch.baseline.SnapshotModels;
import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.export.SysmlV2Exporter;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.validation.ValidationError;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * P3 T1 (metamodel v1.3, spec §2): "旧模型文件加载后元素数不变；新类各建一个实例并可保存". The old file is the demo as the
 * v1.2 metamodel saved it through Sirius' own JSON resource ({@code fixtures/metamodel/v1.2-demo.json}, written before
 * the change).
 */
class MetamodelV13Test {

    private static final SysarchFactory F = SysarchFactory.eINSTANCE;

    private static final ConstraintValidator VALIDATOR = new ConstraintValidator();

    @AfterAll
    static void close() {
        VALIDATOR.close();
    }

    @Test
    void aV12DocumentLoadsUnchanged() throws Exception {
        Resource old = load(Files.readString(Path.of("src/test/resources/fixtures/metamodel/v1.2-demo.json"), StandardCharsets.UTF_8));
        Map<String, ModelSnapshot.ElementState> loaded = ModelSnapshot.capture(List.of(old));
        Resource demo = new org.eclipse.emf.ecore.resource.impl.ResourceImpl();
        demo.getContents().addAll(DomainControllerDemo.build());
        Map<String, ModelSnapshot.ElementState> expected = ModelSnapshot.capture(List.of(demo));
        assertEquals(expected.size(), loaded.size(), "element count unchanged");
        assertEquals(expected, loaded, "every element and every field as before");
        assertEquals(0, old.getErrors().size(), old.getErrors().toString());
    }

    @Test
    void theNewClassesAreThere() {
        Set<String> names = new TreeSet<>();
        for (EClassifier classifier : SysarchPackage.eINSTANCE.getEClassifiers()) {
            names.add(classifier.getName());
        }
        assertTrue(names.containsAll(Set.of("ConstraintBlock", "Parameter", "ConstraintExpr", "BindingConnector", "StateMachine", "State", "Transition",
                "Activity", "Action", "ActionKind", "ActivityFlow", "UseCase", "Actor")), names.toString());
        assertEquals("http://example.com/sysarch/1.0", SysarchPackage.eNS_URI, "the namespace stays, so stored documents keep loading");
        assertEquals("IN", F.createParameter().getDirection(), "the snippet's default");
        assertEquals(ActionKind.CALL, F.createAction().getKind());
    }

    @Test
    void oneOfEachNewClassSavesAndLoadsBack() throws Exception {
        List<EObject> roots = newElements();
        Resource saved = load(save(roots));
        assertEquals(0, saved.getErrors().size(), saved.getErrors().toString());
        Resource original = new org.eclipse.emf.ecore.resource.impl.ResourceImpl();
        original.getContents().addAll(newElements());
        Map<String, ModelSnapshot.ElementState> before = ModelSnapshot.capture(List.of(original));
        Map<String, ModelSnapshot.ElementState> after = ModelSnapshot.capture(List.of(saved));
        assertEquals(before, after);
        assertTrue(before.keySet().containsAll(List.of("CB-001", "PA-001", "PA-002", "BC-001", "SM-001", "ST-001", "ST-002", "TR-001", "AC-001", "AN-001",
                "AN-002", "AF-001", "UC-001", "AR-001")), before.keySet().toString());
        ModelSnapshot.ElementState state = after.get("ST-001");
        assertEquals("进入时自检", state.attributes().get("entryBehavior"));
        assertEquals("停止输出", state.attributes().get("exitBehavior"));
        assertEquals("DECIDE", after.get("AN-002").attributes().get("kind"));
        // the snapshot rebuild branches and merges use (P2 T3) handles the new classes too
        Resource rebuilt = new org.eclipse.emf.ecore.resource.impl.ResourceImpl();
        rebuilt.getContents().addAll(SnapshotModels.build(after));
        assertEquals(after, ModelSnapshot.capture(List.of(rebuilt)));
    }

    @Test
    void theNewConstraintsSpeakChinese() {
        ConstraintBlock empty = F.createConstraintBlock();
        empty.setId("CB-X");
        empty.setName("功率");
        Parameter noUnit = parameter("PA-X", "P", null, "SIDEWAYS");
        empty.getParameters().add(noUnit);
        List<String> messages = VALIDATOR.validate(empty).stream().map(ValidationError::message).toList();
        assertTrue(messages.contains("约束块「功率」没有表达式"), messages.toString());
        assertTrue(messages.contains("约束块「功率」有参数没有单位：P"), messages.toString());
        assertEquals(List.of("参数「P」的方向只能是 IN、OUT 或 INOUT，现在是 SIDEWAYS"), VALIDATOR.validate(noUnit).stream().map(ValidationError::message).toList());
        ConstraintBlock fine = (ConstraintBlock) newElements().get(0);
        assertEquals(List.of(), VALIDATOR.validateAll(List.of(fine)), "a complete constraint block is clean");
    }

    @Test
    void theSysmlExportIgnoresWhatItCannotExpressYet() {
        Resource resource = new org.eclipse.emf.ecore.resource.impl.ResourceImpl();
        resource.getContents().addAll(DomainControllerDemo.build());
        resource.getContents().addAll(newElements());
        String text = assertDoesNotThrow(() -> SysmlV2Exporter.export("demo", List.of(resource), SysmlV2Exporter.Scope.PROJECT, null, null));
        assertTrue(text.contains("<'PD-001'>"));
    }

    /** One instance of every new class, with every new feature set. */
    static List<EObject> newElements() {
        List<EObject> roots = new ArrayList<>();
        ConstraintBlock power = named(F.createConstraintBlock(), "CB-001", "功率约束");
        Parameter in = parameter("PA-001", "P_in", "W", "IN");
        in.setDefaultValue(120.0);
        Parameter out = parameter("PA-002", "P_out", "W", "OUT");
        power.getParameters().add(in);
        power.getParameters().add(out);
        ConstraintExpr expression = F.createConstraintExpr();
        expression.setLanguage("PYTHON");
        expression.setBody("P_out = P_in * eff");
        expression.getDeclaredVars().addAll(List.of("P_in", "P_out", "eff"));
        power.setExpression(expression);
        roots.add(power);
        BindingConnector binding = named(F.createBindingConnector(), "BC-001", "输入绑定");
        binding.setSource(in);
        binding.setTarget(out);
        roots.add(binding);

        StateMachine machine = named(F.createStateMachine(), "SM-001", "工作模式");
        State idle = named(F.createState(), "ST-001", "待机");
        idle.setIsInitial(true);
        idle.setEntryBehavior("进入时自检");
        idle.setDoBehavior("等待指令");
        idle.setExitBehavior("停止输出");
        State done = named(F.createState(), "ST-002", "关闭");
        done.setIsFinal(true);
        Transition off = named(F.createTransition(), "TR-001", "关机");
        off.setSource(idle);
        off.setTarget(done);
        off.setTrigger("powerOff");
        off.setGuard("speed == 0");
        off.setEffect("log()");
        machine.getStates().addAll(List.of(idle, done));
        machine.getTransitions().add(off);
        roots.add(machine);

        Activity activity = named(F.createActivity(), "AC-001", "启动流程");
        Action check = named(F.createAction(), "AN-001", "自检");
        check.setKind(ActionKind.CALL);
        check.setExpression("selfTest()");
        Action decide = named(F.createAction(), "AN-002", "是否通过");
        decide.setKind(ActionKind.DECIDE);
        ActivityFlow flow = named(F.createActivityFlow(), "AF-001", "结果");
        flow.setSource(check);
        flow.setTarget(decide);
        flow.setIsObjectFlow(true);
        activity.getActions().addAll(List.of(check, decide));
        activity.getFlows().add(flow);
        roots.add(activity);

        Actor driver = named(F.createActor(), "AR-001", "驾驶员");
        PartDefinition subject = named(F.createPartDefinition(), "PD-UC", "车辆");
        UseCase drive = named(F.createUseCase(), "UC-001", "启动车辆");
        drive.getActors().add(driver);
        drive.setSubject(subject);
        roots.addAll(List.of(driver, subject, drive));
        return roots;
    }

    private static Parameter parameter(String id, String name, String unit, String direction) {
        Parameter parameter = named(F.createParameter(), id, name);
        parameter.setUnit(unit);
        parameter.setDirection(direction);
        return parameter;
    }

    private static <T extends ArchElement> T named(T element, String id, String name) {
        element.setId(id);
        element.setName(name);
        element.setLayer(Layer.LOGICAL);
        return element;
    }

    private static String save(List<EObject> roots) throws Exception {
        ResourceSet set = new ResourceSetImpl();
        set.getPackageRegistry().put(SysarchPackage.eNS_URI, SysarchPackage.eINSTANCE);
        Resource resource = new JSONResourceFactory().createResourceFromPath("v13");
        set.getResources().add(resource);
        resource.getContents().addAll(roots);
        var out = new ByteArrayOutputStream();
        resource.save(out, Map.of());
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Resource load(String json) throws Exception {
        ResourceSet set = new ResourceSetImpl();
        set.getPackageRegistry().put(SysarchPackage.eNS_URI, SysarchPackage.eINSTANCE);
        Resource resource = new JSONResourceFactory().createResourceFromPath("loaded");
        set.getResources().add(resource);
        resource.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), Map.of());
        return resource;
    }
}

package com.example.sysarch.sysmlimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.baseline.ModelDiffs;
import com.example.sysarch.baseline.ModelSnapshot;
import com.example.sysarch.baseline.ModelSnapshot.ElementState;
import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.export.SysmlV2Exporter;
import com.example.sysarch.model.Allocate;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.InterfaceDef;
import com.example.sysarch.model.ItemFlow;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.PortDirection;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.model.SysarchFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

/**
 * P2 T6 acceptance: "用 P1 导出的文本回灌，元素数与导出前一致；故意插入参数语法，导入失败并定位到行" (P2-AC-6), the round trip
 * lossless by the user's decision, and plain SysML from elsewhere with the default layer.
 */
class SysmlImportTest {

    private static final SysarchFactory F = SysarchFactory.eINSTANCE;

    /** P2-AC-6: the demo exported and imported again: the same elements, ids, names, layers and every field. */
    @Test
    void theDemoExportedAndImportedIsTheSameModel() {
        Resource demo = resource(DomainControllerDemo.build());
        String text = SysmlV2Exporter.export("域控演示", List.of(demo), SysmlV2Exporter.Scope.PROJECT, null, null);

        SysmlParser.Parsed parsed = SysmlParser.parse(text);
        assertEquals(List.of(), parsed.problems());
        SysmlMapper.Mapped mapped = SysmlMapper.map(parsed.root(), Layer.LOGICAL);

        Map<String, ElementState> before = ModelSnapshot.capture(List.of(demo));
        Map<String, ElementState> after = ModelSnapshot.capture(List.of(resource(mapped.roots())));
        assertEquals(before.size(), mapped.total(), "element count: " + mapped.counts());
        assertEquals(28, mapped.total());
        assertEquals("域控演示", mapped.packageName());
        assertEquals(List.of(), mapped.unmapped());
        assertEquals(List.of(), mapped.defaultedLayer(), "every layer came from the text");
        assertEquals(List.of(), mapped.derivedPorts());
        assertEquals(diff(before, after), "", "no field differs");
        assertEquals(before, after);
    }

    /** Every SysArch convention the exporter writes comes back (P2 T6, "无损往返"). */
    @Test
    void aRicherModelRoundTripsToo() throws IOException {
        Resource model = resource(DomainControllerDemo.build());
        PartDefinition controller = (PartDefinition) find(model, "PD-001");
        PartDefinition soc = (PartDefinition) find(model, "PD-005");
        Part fusion = (Part) find(model, "PT-001");
        Port objects = (Port) find(model, "PO-001");
        Connection co1 = (Connection) find(model, "CO-001");
        InterfaceDef can = (InterfaceDef) find(model, "IF-001");
        Requirement rq1 = (Requirement) find(model, "RQ-001");
        Allocate al1 = (Allocate) find(model, "AL-001");

        objects.setDescription("目标列表输出");
        objects.setIsConjugated(true);
        fusion.setLayer(Layer.SYSTEM);
        fusion.getStereotypes().add("function");
        fusion.setDescription("感知融合部件\n第二行");
        co1.setDescription("主数据流");
        co1.getStereotypes().add("bus");
        rq1.setDescription("来源：客户需求 A-12");
        al1.setName("融合上 SoC");
        al1.setLayer(Layer.LOGICAL);
        al1.setDescription("评审确认");
        soc.setIsAbstract(true);
        PartDefinition ecu = F.createPartDefinition();
        ecu.setId("PD-ECU");
        ecu.setName("通用 ECU");
        ecu.setLayer(Layer.PHYSICAL);
        model.getContents().add(ecu);
        soc.getSuperTypes().add(ecu);
        InterfaceDef base = F.createInterfaceDef();
        base.setId("IF-BASE");
        base.setName("总线");
        base.setLayer(Layer.LOGICAL);
        base.setDescription("所有总线接口的父接口");
        model.getContents().add(base);
        can.getSuperTypes().add(base);
        Port port = F.createPort();
        port.setId("PO-BLK");
        port.setName("诊断");
        port.setLayer(Layer.LOGICAL);
        port.setDirection(PortDirection.INOUT);
        port.setInterfaceType(can);
        controller.getOwnedPorts().add(port);
        ItemFlow flow = F.createItemFlow();
        flow.setId("IFL-1");
        flow.setName("目标");
        flow.setItemName("objects");
        flow.setItemType("ObjectList");
        flow.setLayer(Layer.LOGICAL);
        flow.setConnection(co1);
        model.getContents().add(flow);

        String text = SysmlV2Exporter.export("域控演示", List.of(model), SysmlV2Exporter.Scope.PROJECT, null, null);
        Files.createDirectories(Path.of("build", "sysml-export"));
        Files.writeString(Path.of("build", "sysml-export", "rich-roundtrip.sysml"), text, StandardCharsets.UTF_8);
        SysmlParser.Parsed parsed = SysmlParser.parse(text);
        assertEquals(List.of(), parsed.problems());
        SysmlMapper.Mapped mapped = SysmlMapper.map(parsed.root(), Layer.LOGICAL);

        Map<String, ElementState> before = ModelSnapshot.capture(List.of(model));
        Map<String, ElementState> after = ModelSnapshot.capture(List.of(resource(mapped.roots())));
        assertEquals("", diff(before, after));
        assertEquals(List.of(), mapped.unmapped());
    }

    /** T6 step 2: unsupported syntax fails the whole import, every place with its line. */
    @Test
    void parametersAndBehaviourAreRefusedWithTheirLines() {
        String text = """
                package '测试' {
                    part def <'PD-1'> '车辆' {
                        attribute mass : Real = 1500.0;
                        part <'PT-1'> '发动机' : '动力单元';
                    }
                    part def <'PD-2'> '动力单元';
                    action def '启动';
                    state def '运行' {
                        state 'idle';
                    }
                }
                """;
        SysmlParser.Parsed parsed = SysmlParser.parse(text);
        assertEquals(List.of(3, 7, 8), parsed.problems().stream().map(SysmlParser.Problem::line).toList(), String.valueOf(parsed.problems()));
        assertEquals("第 3 行：不支持的语法：属性/参数（attribute）", parsed.problems().get(0).toString());
        assertTrue(parsed.problems().get(1).message().contains("动作"));
        assertTrue(parsed.problems().get(2).message().contains("状态机"));
    }

    @Test
    void aSyntaxErrorIsLocated() {
        SysmlParser.Parsed parsed = SysmlParser.parse("package p {\n  part def 'A' {\n    part 'x' : 'A'\n  }\n");
        assertTrue(parsed.problems().stream().anyMatch(p -> p.line() == 4), String.valueOf(parsed.problems()));
        assertTrue(SysmlParser.parse("package p { part def 'A; }").problems().get(0).message().contains("没有结束"));
    }

    /** Plain SysML from elsewhere: structure only, generated ids, the default layer (listed), references by name. */
    @Test
    void foreignSysmlTakesTheDefaultLayer() {
        String text = """
                package Vehicle {
                    private import ScalarValues::*;
                    port def FuelPort;
                    part def Engine {
                        doc /* The engine. */
                        out port fuelOut : FuelPort;
                    }
                    part def Tank {
                        in port fuelIn : ~FuelPort;
                    }
                    part def Car {
                        part engine : Engine[1];
                        part tank : Tank;
                        connect engine.fuelOut to tank.fuelIn;
                        part wheel : Wheel[4];
                    }
                    requirement '续航' {
                        doc /* 续航不少于 500 km。 */
                    }
                }
                """;
        SysmlParser.Parsed parsed = SysmlParser.parse(text);
        assertEquals(List.of(), parsed.problems());
        SysmlMapper.Mapped mapped = SysmlMapper.map(parsed.root(), Layer.PHYSICAL);

        assertEquals("Vehicle", mapped.packageName());
        assertEquals(Map.of("part def", 3, "port def", 1, "part", 3, "port", 4, "connection", 1, "requirement", 1), mapped.counts());
        assertEquals(2, mapped.derivedPorts().size(), "engine.fuelOut and tank.fuelIn made explicit on the parts: " + mapped.derivedPorts());
        assertEquals(5, mapped.defaultedLayer().size(), "the top-level definitions and the requirement; the rest inherit: " + mapped.defaultedLayer());
        Resource imported = resource(mapped.roots());
        PartDefinition engine = (PartDefinition) imported.getContents().stream().filter(e -> e instanceof PartDefinition p && "Engine".equals(p.getName()))
                .findFirst().orElseThrow();
        assertEquals(Layer.PHYSICAL, engine.getLayer());
        assertEquals("The engine.", engine.getDescription());
        Connection connection = (Connection) imported.getContents().stream().filter(Connection.class::isInstance).findFirst().orElseThrow();
        assertEquals("fuelOut", connection.getSource().getName());
        assertEquals("FuelPort", connection.getInterfaceType().getName(), "taken from the source port, as the connect tool does");
        Port fuelIn = connection.getTarget();
        assertTrue(fuelIn.isIsConjugated());
        assertEquals(PortDirection.IN, fuelIn.getDirection());
        Part wheel = engine.eContainer() == null ? find(imported, "Car", "wheel") : null;
        assertNotNull(wheel);
        assertNull(wheel.getTypedBy());
        assertTrue(mapped.unmapped().stream().anyMatch(u -> u.contains("「Wheel」在文本里没有定义")), String.valueOf(mapped.unmapped()));
        assertEquals("续航不少于 500 km。", ((Requirement) imported.getContents().stream().filter(Requirement.class::isInstance).findFirst().orElseThrow()).getText());
    }

    // ---------------------------------------------------------------- helpers

    private static Resource resource(List<? extends EObject> contents) {
        Resource resource = new ResourceImpl(URI.createURI("memory:/m" + System.nanoTime() + ".json"));
        resource.getContents().addAll(contents);
        return resource;
    }

    private static EObject find(Resource model, String id) {
        for (var it = model.getAllContents(); it.hasNext();) {
            EObject e = it.next();
            if (id.equals(EcoreUtil.getID(e))) {
                return e;
            }
        }
        throw new IllegalArgumentException(id);
    }

    private static Part find(Resource model, String block, String part) {
        return model.getContents().stream().filter(e -> e instanceof PartDefinition p && block.equals(p.getName())).map(PartDefinition.class::cast)
                .flatMap(b -> b.getOwnedParts().stream()).filter(p -> part.equals(p.getName())).findFirst().orElse(null);
    }

    private static String diff(Map<String, ElementState> before, Map<String, ElementState> after) {
        var d = ModelDiffs.diff(before, after);
        return d.getAdded().isEmpty() && d.getRemoved().isEmpty() && d.getModified().isEmpty() ? ""
                : "added " + d.getAdded() + " removed " + d.getRemoved() + " modified " + d.getModified().stream().map(m -> m.getElementId() + m.getFields()).toList();
    }
}

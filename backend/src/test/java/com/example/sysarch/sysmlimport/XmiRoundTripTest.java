package com.example.sysarch.sysmlimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.example.sysarch.baseline.ModelSnapshot;
import com.example.sysarch.export.XmiExporter;
import com.example.sysarch.model.Allocate;
import com.example.sysarch.model.ArchElement;
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
import com.example.sysarch.model.VerifyMethod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.Test;

/**
 * P3 T8 acceptance (spec §5.3): three golden cases under {@code src/test/resources/fixtures/xmi/roundtrip/}. Each model's
 * export must equal its committed golden file, and export → import → export must give the same text after normalizing
 * ids (ignored as the spec says; there are no time stamps). The imported model must also equal the original element by
 * element (snapshot), so "元素与关系不丢". To refresh the golden files after a deliberate exporter change:
 * {@code XMI_GOLDEN_UPDATE=1 ./gradlew :backend:test --tests "*XmiRoundTripTest*"}, then review the diff.
 */
class XmiRoundTripTest {

    private static final SysarchFactory F = SysarchFactory.eINSTANCE;

    private static final Path GOLDEN = Path.of("src/test/resources/fixtures/xmi/roundtrip");

    /** case-01: 3 blocks, 4 ports, 2 connections (ports on parts, an interface, an item flow, multiplicities, a description). */
    static List<EObject> basic() {
        InterfaceDef can = interfaceDef("IF-1", "CAN", "CAN 2.0B");
        PartDefinition system = block("PD-1", "整车控制", Layer.LOGICAL);
        system.setDescription("顶层块：两个控制器经 CAN 互联");
        system.getStereotypes().add("system");
        PartDefinition a = block("PD-2", "动力控制器", Layer.LOGICAL);
        PartDefinition b = block("PD-3", "车身控制器", Layer.LOGICAL);
        Part pa = part("PT-1", "动力", a, "1");
        Part pb = part("PT-2", "车身", b, "0..1");
        system.getOwnedParts().addAll(List.of(pa, pb));
        Port p1 = port("PO-1", "torqueOut", PortDirection.OUT, can);
        Port p2 = port("PO-2", "statusIn", PortDirection.IN, can);
        Port p3 = port("PO-3", "torqueIn", PortDirection.IN, can);
        Port p4 = port("PO-4", "statusOut", PortDirection.OUT, can);
        pa.getPorts().addAll(List.of(p1, p2));
        pb.getPorts().addAll(List.of(p3, p4));
        Connection c1 = connection("CO-1", "扭矩请求", p1, p3, can);
        Connection c2 = connection("CO-2", "车身状态", p4, p2, can);
        ItemFlow flow = F.createItemFlow();
        flow.setId("IFL-1");
        flow.setName("扭矩帧");
        flow.setItemName("torque");
        flow.setItemType("Torque");
        flow.setLayer(Layer.LOGICAL);
        flow.setConnection(c1);
        return List.of(can, system, a, b, c1, c2, flow);
    }

    /** case-02: three layers, two elements each, allocated down the layers; an abstract block and a specialization. */
    static List<EObject> layers() {
        PartDefinition f1 = block("SA-1", "感知功能", Layer.SYSTEM);
        PartDefinition f2 = block("SA-2", "规划功能", Layer.SYSTEM);
        f2.getStereotypes().addAll(List.of("function", "safety"));
        PartDefinition l1 = block("LA-1", "计算单元", Layer.LOGICAL);
        l1.setIsAbstract(true);
        PartDefinition l2 = block("LA-2", "规划单元", Layer.LOGICAL);
        l2.getSuperTypes().add(l1);
        PartDefinition h1 = block("PA-1", "域控 SoC", Layer.PHYSICAL);
        PartDefinition h2 = block("PA-2", "安全 MCU", Layer.PHYSICAL);
        return List.of(f1, f2, l1, l2, h1, h2, allocate("AL-1", f1, l1, "感知算法在计算单元上"), allocate("AL-2", f2, l2, null), allocate("AL-3", l1, h1, "算力"),
                allocate("AL-4", l2, h2, "功能安全"));
    }

    /** case-03: five requirements and satisfy / verify relations. */
    static List<EObject> requirements() {
        PartDefinition b1 = block("PD-1", "感知融合", Layer.LOGICAL);
        PartDefinition b2 = block("PD-2", "决策规划", Layer.LOGICAL);
        PartDefinition b3 = block("PD-3", "执行控制", Layer.LOGICAL);
        Requirement r1 = requirement("RQ-1", "目标周期", "REQ-001", "每 50 ms 发布一次目标列表。", VerifyMethod.TEST, 1);
        Requirement r2 = requirement("RQ-2", "轨迹时延", "REQ-002", "100 ms 内输出轨迹。", VerifyMethod.ANALYSIS, 2);
        Requirement r3 = requirement("RQ-3", "状态反馈", "REQ-003", "反馈自车状态。", VerifyMethod.INSPECTION, 3);
        Requirement r4 = requirement("RQ-4", "算力余量", "REQ-004", "保留 30% 算力。", VerifyMethod.DEMONSTRATION, 2);
        Requirement r5 = requirement("RQ-5", "功能安全", "REQ-005", "满足 ASIL-D。", VerifyMethod.TEST, 1);
        r5.setDescription("来自安全目标 SG-01");
        r1.getSatisfiedBy().add(b1);
        r2.getSatisfiedBy().add(b2);
        r3.getSatisfiedBy().addAll(List.of(b1, b3));
        r1.getVerifiedBy().add(b2);
        return List.of(b1, b2, b3, r1, r2, r3, r4, r5);
    }

    /**
     * case-04 (v1.4): an interaction: lifelines representing a block (a typed property of the interaction) and a part (the
     * extension), a third with its own layer, stereotype and description; every message kind, a self-message, time order.
     */
    static List<EObject> interaction() {
        PartDefinition system = block("PD-1", "整车控制", Layer.LOGICAL);
        PartDefinition ecu = block("PD-2", "门控", Layer.LOGICAL);
        Part door = part("PT-1", "前门", ecu, "1");
        system.getOwnedParts().add(door);
        com.example.sysarch.model.Interaction sq = F.createInteraction();
        named(sq, "SQ-1", "开门", Layer.LOGICAL);
        sq.setDescription("驾驶员开门的交互");
        com.example.sysarch.model.Lifeline driver = lifeline("LL-1", "门控单元", ecu);
        com.example.sysarch.model.Lifeline front = lifeline("LL-2", "前门实例", door);
        com.example.sysarch.model.Lifeline log = lifeline("LL-3", "记录器", null);
        log.setLayer(Layer.PHYSICAL);
        log.getStereotypes().add("monitor");
        log.setDescription("只记录");
        sq.getLifelines().addAll(List.of(driver, front, log));
        sq.getMessages().add(message("MS-1", "开门请求", com.example.sysarch.model.MessageKind.ASYNC, driver, front));
        sq.getMessages().add(message("MS-2", "解锁()", com.example.sysarch.model.MessageKind.SYNC, front, driver));
        sq.getMessages().add(message("MS-3", "已解锁", com.example.sysarch.model.MessageKind.REPLY, driver, front));
        com.example.sysarch.model.Message self = message("MS-4", "自检", com.example.sysarch.model.MessageKind.SYNC, front, front);
        self.setDescription("每次开门前");
        sq.getMessages().add(self);
        sq.getMessages().add(message("MS-5", "记一笔", com.example.sysarch.model.MessageKind.ASYNC, front, log));
        return List.of(system, ecu, sq);
    }

    private static com.example.sysarch.model.Lifeline lifeline(String id, String name, ArchElement represents) {
        com.example.sysarch.model.Lifeline l = F.createLifeline();
        named(l, id, name, Layer.LOGICAL);
        l.setRepresents(represents);
        return l;
    }

    private static com.example.sysarch.model.Message message(String id, String name, com.example.sysarch.model.MessageKind kind,
            com.example.sysarch.model.Lifeline from, com.example.sysarch.model.Lifeline to) {
        com.example.sysarch.model.Message m = F.createMessage();
        named(m, id, name, Layer.LOGICAL);
        m.setKind(kind);
        m.setSender(from);
        m.setReceiver(to);
        return m;
    }

    @Test
    void case04Interaction() throws IOException {
        this.roundTrip("case-04-interaction", XmiRoundTripTest::interaction);
        // for tools/uml2-check: the same model in the Papyrus flavour
        Files.createDirectories(Path.of("build/xmi-export"));
        Files.writeString(Path.of("build/xmi-export/case-04-interaction.uml"),
                XmiExporter.export("case-04-interaction", List.of(resource(interaction())), null, XmiExporter.Flavour.PAPYRUS), StandardCharsets.UTF_8);
    }

    @Test
    void case01Basic() throws IOException {
        this.roundTrip("case-01-basic", XmiRoundTripTest::basic);
    }

    @Test
    void case02Layers() throws IOException {
        this.roundTrip("case-02-layers", XmiRoundTripTest::layers);
    }

    @Test
    void case03Requirement() throws IOException {
        this.roundTrip("case-03-requirement", XmiRoundTripTest::requirements);
    }

    /**
     * A part without a type (allowed while modelling, created by the IBD's 新建部件, marked red) is exported as a composite
     * property without a type; our own export imports it again (found by the end-to-end test, 2026-11-13), while the
     * same element in another tool's file stays refused.
     */
    @Test
    void anUntypedPartRoundTripsInOurOwnExportOnly() {
        PartDefinition system = block("PD-1", "系统", Layer.LOGICAL);
        Part untyped = F.createPart();
        untyped.setId("PT-1");
        untyped.setName("part4");
        system.getOwnedParts().add(untyped);
        Resource original = resource(List.of(system));
        String xmi = XmiExporter.export("untyped", List.of(original));

        XmiImporter.Result result = XmiImporter.read(xmi, Layer.LOGICAL);
        assertEquals(List.of(), result.problems(), "our own export is accepted");
        Part back = ((PartDefinition) result.mapped().roots().get(0)).getOwnedParts().get(0);
        assertEquals("part4", back.getName());
        assertEquals(null, back.getTypedBy(), "still without a type");
        assertEquals(ModelSnapshot.capture(List.of(original)), ModelSnapshot.capture(List.of(resource(result.mapped().roots()))), "nothing else changed");

        String foreign = xmi.replaceAll("<xmi:Documentation[^>]*>[\\s\\S]*?</xmi:Documentation>", "<xmi:Documentation exporter=\"Other\"/>")
                .replaceAll("<xmi:Documentation[^>]*/>", "<xmi:Documentation exporter=\"Other\"/>");
        XmiImporter.Result refused = XmiImporter.read(foreign, Layer.LOGICAL);
        assertTrue(String.join("\n", refused.problems()).contains("它没有类型"), "another tool's untyped property is refused: " + refused.problems());
    }

    private void roundTrip(String name, Supplier<List<EObject>> model) throws IOException {
        Resource original = resource(model.get());
        String first = XmiExporter.export(name, List.of(original));
        Path golden = GOLDEN.resolve(name).resolve("export.xmi");
        if ("1".equals(System.getenv("XMI_GOLDEN_UPDATE"))) {
            Files.createDirectories(golden.getParent());
            Files.writeString(golden, first, StandardCharsets.UTF_8);
        }
        if (!Files.isRegularFile(golden)) {
            fail("golden file missing: " + golden + " (create it with XMI_GOLDEN_UPDATE=1)");
        }
        assertSame("export vs golden " + golden, normalize(Files.readString(golden, StandardCharsets.UTF_8)), normalize(first));

        XmiImporter.Result result = XmiImporter.read(first, Layer.LOGICAL);
        assertEquals(List.of(), result.problems(), "our own export is accepted");
        assertEquals(List.of(), result.mapped().defaultedLayer(), "every layer comes back from the extension");
        Resource imported = resource(result.mapped().roots());
        String second = XmiExporter.export(name, List.of(imported));
        assertSame("export -> import -> export", normalize(first), normalize(second));

        Map<String, ModelSnapshot.ElementState> before = ModelSnapshot.capture(List.of(original));
        Map<String, ModelSnapshot.ElementState> after = ModelSnapshot.capture(List.of(imported));
        assertEquals(before.keySet(), after.keySet(), "the same elements");
        before.forEach((id, state) -> assertEquals(state, after.get(id), "element " + id + " unchanged"));
    }

    // ------------------------------------------------------------------ normalization: ids renumbered in document order

    private static final Pattern ID = Pattern.compile("xmi:id=\"([^\"]*)\"");

    private static final Pattern REFERENCE = Pattern.compile("(\\b(?:xmi:id|type|general|role|partWithPort|client|supplier|base_Class|base_Abstraction|annotatedElement))=\"([^\"]*)\"");

    static String normalize(String xmi) {
        Map<String, String> renamed = new LinkedHashMap<>();
        Matcher ids = ID.matcher(xmi);
        while (ids.find()) {
            renamed.putIfAbsent(ids.group(1), "id" + (renamed.size() + 1));
        }
        Matcher m = REFERENCE.matcher(xmi);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            List<String> tokens = new ArrayList<>();
            for (String token : m.group(2).split(" ")) {
                tokens.add(renamed.getOrDefault(token, token));
            }
            m.appendReplacement(out, Matcher.quoteReplacement(m.group(1) + "=\"" + String.join(" ", tokens) + "\""));
        }
        m.appendTail(out);
        return out.toString().replace("\r\n", "\n");
    }

    private static void assertSame(String what, String expected, String actual) {
        if (expected.equals(actual)) {
            return;
        }
        String[] a = expected.split("\n");
        String[] b = actual.split("\n");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            String x = i < a.length ? a[i] : "<end>";
            String y = i < b.length ? b[i] : "<end>";
            if (!x.equals(y)) {
                fail(what + ": first difference at line " + (i + 1) + "\n- " + x + "\n+ " + y);
            }
        }
    }

    // ------------------------------------------------------------------ builders

    static Resource resource(List<EObject> roots) {
        Resource r = new ResourceImpl(URI.createURI("memory:/roundtrip.json"));
        r.getContents().addAll(roots);
        return r;
    }

    private static PartDefinition block(String id, String name, Layer layer) {
        PartDefinition b = F.createPartDefinition();
        named(b, id, name, layer);
        return b;
    }

    private static Part part(String id, String name, PartDefinition type, String multiplicity) {
        Part p = F.createPart();
        named(p, id, name, Layer.LOGICAL);
        p.setTypedBy(type);
        p.setMultiplicity(multiplicity);
        return p;
    }

    private static Port port(String id, String name, PortDirection direction, InterfaceDef type) {
        Port p = F.createPort();
        named(p, id, name, Layer.LOGICAL);
        p.setDirection(direction);
        p.setInterfaceType(type);
        return p;
    }

    private static InterfaceDef interfaceDef(String id, String name, String protocol) {
        InterfaceDef i = F.createInterfaceDef();
        named(i, id, name, Layer.LOGICAL);
        i.setProtocol(protocol);
        i.setDataType("CanFrame");
        return i;
    }

    private static Connection connection(String id, String name, Port source, Port target, InterfaceDef type) {
        Connection c = F.createConnection();
        named(c, id, name, Layer.LOGICAL);
        c.setSource(source);
        c.setTarget(target);
        c.setInterfaceType(type);
        return c;
    }

    private static Requirement requirement(String id, String name, String reqId, String text, VerifyMethod method, int priority) {
        Requirement r = F.createRequirement();
        named(r, id, name, Layer.SYSTEM);
        r.setReqId(reqId);
        r.setText(text);
        r.setVerifyMethod(method);
        r.setPriority(priority);
        return r;
    }

    private static Allocate allocate(String id, PartDefinition from, PartDefinition to, String rationale) {
        Allocate a = F.createAllocate();
        named(a, id, from.getName() + " → " + to.getName(), to.getLayer());
        a.setFrom(from);
        a.setTo(to);
        a.setRationale(rationale);
        return a;
    }

    private static void named(ArchElement e, String id, String name, Layer layer) {
        e.setId(id);
        e.setName(name);
        e.setLayer(layer);
    }

    /** Sanity: the cases have the sizes spec §5.3 names. */
    @Test
    void theCasesHaveTheSpecifiedSizes() {
        List<EObject> basic = new ArrayList<>();
        resource(basic()).getAllContents().forEachRemaining(basic::add);
        assertEquals(3, basic.stream().filter(PartDefinition.class::isInstance).count());
        assertEquals(4, basic.stream().filter(Port.class::isInstance).count());
        assertEquals(2, basic.stream().filter(Connection.class::isInstance).count());
        List<EObject> layered = layers();
        for (Layer layer : List.of(Layer.SYSTEM, Layer.LOGICAL, Layer.PHYSICAL)) {
            assertEquals(2, layered.stream().filter(e -> e instanceof PartDefinition b && b.getLayer() == layer).count(), layer.getName());
        }
        List<EObject> reqs = requirements();
        assertEquals(5, reqs.stream().filter(Requirement.class::isInstance).count());
        assertTrue(reqs.stream().filter(Requirement.class::isInstance).mapToInt(r -> ((Requirement) r).getSatisfiedBy().size()).sum() >= 3, "with satisfy relations");
    }
}

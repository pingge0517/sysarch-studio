package com.example.sysarch.demo;

import com.example.sysarch.model.Allocate;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.ConnectionKind;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.PortDirection;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.model.VerifyMethod;

import java.util.List;

import org.eclipse.emf.ecore.EObject;

/**
 * Demo data of spec T9 step 2: a "域控制器" (domain controller) with 3 logical parts, 6 ports, 4 connections and 5
 * requirements, across the logical (LA) and physical (PA) layers. Available in Sirius Web as the "系统架构模型（域控制器
 * 演示）" model template; {@code DomainControllerDemoTest} checks it.
 *
 * <ul>
 * <li>LA: {@code PD-001 域控制器} owns the parts {@code 感知融合}, {@code 决策规划} and {@code 执行控制}, typed by
 * {@code PD-002..PD-004}. Each part has one IN and one OUT port on the in-vehicle bus ({@code CAN-FD}); the
 * connections carry {@code ObjectList}, {@code Trajectory} and {@code VehicleState} (the connection's interface
 * type, shown as the IBD edge label).</li>
 * <li>PA: the SoC {@code PD-005} and the safety MCU {@code PD-006}; three Allocates map the logical blocks onto them
 * (LA → PA, adjacent layers, so {@code LayerAdjacentOnly} holds).</li>
 * </ul>
 *
 * <p>Ids are fixed (spec §9.2 binds {@code PD-001}), so create the demo once per project.
 */
public final class DomainControllerDemo {

    public static final String SYSTEM_ID = "PD-001";

    private static final SysarchFactory FACTORY = SysarchFactory.eINSTANCE;

    private DomainControllerDemo() {
    }

    /** The demo's resource roots, in document order. */
    public static List<EObject> build() {
        PartDefinition system = block("PD-001", "域控制器", Layer.LOGICAL, "system");
        system.setDescription("集中式域控制器：感知融合、决策规划与执行控制三个逻辑部件");
        PartDefinition fusionType = block("PD-002", "感知融合单元", Layer.LOGICAL, "logicalComponent");
        PartDefinition plannerType = block("PD-003", "决策规划单元", Layer.LOGICAL, "logicalComponent");
        PartDefinition controlType = block("PD-004", "执行控制单元", Layer.LOGICAL, "logicalComponent");
        PartDefinition soc = block("PD-005", "域控 SoC", Layer.PHYSICAL, "ecu");
        PartDefinition mcu = block("PD-006", "安全 MCU", Layer.PHYSICAL, "ecu");

        Part fusion = part(system, "PT-001", "感知融合", fusionType);
        Part planner = part(system, "PT-002", "决策规划", plannerType);
        Part control = part(system, "PT-003", "执行控制", controlType);

        Port fusionOut = port(fusion, "PO-001", "objects", PortDirection.OUT);
        Port fusionIn = port(fusion, "PO-002", "egoState", PortDirection.IN);
        Port plannerIn = port(planner, "PO-003", "perception", PortDirection.IN);
        Port plannerOut = port(planner, "PO-004", "trajectory", PortDirection.OUT);
        Port controlIn = port(control, "PO-005", "trajectory", PortDirection.IN);
        Port controlOut = port(control, "PO-006", "egoState", PortDirection.OUT);

        List<EObject> roots = new java.util.ArrayList<>(List.of(system, fusionType, plannerType, controlType, soc, mcu));
        roots.add(connection("CO-001", "目标列表", fusionOut, plannerIn, "ObjectList"));
        roots.add(connection("CO-002", "轨迹", plannerOut, controlIn, "Trajectory"));
        roots.add(connection("CO-003", "自车状态（感知）", controlOut, fusionIn, "VehicleState"));
        roots.add(connection("CO-004", "自车状态（规划）", controlOut, plannerIn, "VehicleState"));

        roots.add(requirement("RQ-001", "DC-REQ-001", "目标列表周期", Layer.LOGICAL, VerifyMethod.TEST, 1,
                "感知融合应每 50 ms 向决策规划发布一次目标列表。"));
        roots.add(requirement("RQ-002", "DC-REQ-002", "轨迹时延", Layer.LOGICAL, VerifyMethod.TEST, 1,
                "决策规划自收到目标列表起 100 ms 内输出轨迹。"));
        roots.add(requirement("RQ-003", "DC-REQ-003", "状态反馈", Layer.LOGICAL, VerifyMethod.ANALYSIS, 2,
                "执行控制应向感知融合与决策规划反馈自车状态。"));
        roots.add(requirement("RQ-004", "DC-REQ-004", "算力余量", Layer.PHYSICAL, VerifyMethod.ANALYSIS, 2,
                "域控 SoC 在满负载下应保留不少于 30% 的算力余量。"));
        roots.add(requirement("RQ-005", "DC-REQ-005", "功能安全", Layer.PHYSICAL, VerifyMethod.INSPECTION, 1,
                "执行控制应部署在满足 ASIL-D 的安全 MCU 上。"));

        roots.add(allocate("AL-001", fusionType, soc, "感知融合需要 SoC 的 NPU"));
        roots.add(allocate("AL-002", plannerType, soc, "规划与感知共享 SoC 内存"));
        roots.add(allocate("AL-003", controlType, mcu, "执行控制需要 ASIL-D"));
        return roots;
    }

    private static PartDefinition block(String id, String name, Layer layer, String stereotype) {
        PartDefinition block = FACTORY.createPartDefinition();
        init(block, id, name, layer);
        block.getStereotypes().add(stereotype);
        return block;
    }

    private static Part part(PartDefinition owner, String id, String name, PartDefinition type) {
        Part part = FACTORY.createPart();
        init(part, id, name, Layer.LOGICAL);
        part.setTypedBy(type);
        owner.getOwnedParts().add(part);
        return part;
    }

    private static Port port(Part owner, String id, String name, PortDirection direction) {
        Port port = FACTORY.createPort();
        init(port, id, name, Layer.LOGICAL);
        port.setDirection(direction);
        port.setInterfaceType("CAN-FD");
        owner.getPorts().add(port);
        return port;
    }

    private static Connection connection(String id, String name, Port source, Port target, String payload) {
        Connection connection = FACTORY.createConnection();
        init(connection, id, name, Layer.LOGICAL);
        connection.setKind(ConnectionKind.ASSEMBLY);
        connection.setSource(source);
        connection.setTarget(target);
        connection.setInterfaceType(payload);
        return connection;
    }

    private static Requirement requirement(String id, String reqId, String name, Layer layer, VerifyMethod method, int priority, String text) {
        Requirement requirement = FACTORY.createRequirement();
        init(requirement, id, name, layer);
        requirement.setReqId(reqId);
        requirement.setText(text);
        requirement.setVerifyMethod(method);
        requirement.setPriority(priority);
        return requirement;
    }

    private static Allocate allocate(String id, ArchElement from, ArchElement to, String rationale) {
        Allocate allocate = FACTORY.createAllocate();
        init(allocate, id, from.getName() + " → " + to.getName(), Layer.PHYSICAL);
        allocate.setFrom(from);
        allocate.setTo(to);
        allocate.setRationale(rationale);
        return allocate;
    }

    private static void init(ArchElement element, String id, String name, Layer layer) {
        element.setId(id);
        element.setName(name);
        element.setLayer(layer);
    }
}

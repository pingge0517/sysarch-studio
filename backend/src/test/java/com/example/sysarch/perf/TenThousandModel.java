package com.example.sysarch.perf;

import com.example.sysarch.model.Allocate;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.ConnectionKind;
import com.example.sysarch.model.InterfaceDef;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.PortDirection;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.model.VerifyMethod;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EObject;

/**
 * The P2 T7 benchmark fixture: a system model of about 10,000 elements, shaped like a real one rather than flat.
 *
 * <p>Per layer LOGICAL and PHYSICAL: a root block with 50 parts typed by 50 subsystem blocks; every subsystem block has
 * 20 parts typed from a pool of 100 component blocks, each part with an in and an out port, chained by 19
 * connections; the root's parts have 2 ports each, chained by 49 connections (about 4,400 elements per layer). SYSTEM:
 * 590 function blocks. Plus 10 interface definitions, 500 requirements (half satisfied by a subsystem, a fifth also
 * verified) and 300 allocations LOGICAL → PHYSICAL. {@link #root} is the LOGICAL root, the IBD to open.
 */
public final class TenThousandModel {

    private static final SysarchFactory F = SysarchFactory.eINSTANCE;

    public final List<EObject> contents = new ArrayList<>();

    public PartDefinition root;

    public PartDefinition subsystem;

    public final List<PartDefinition> logicalSubsystems = new ArrayList<>();

    public final List<PartDefinition> physicalSubsystems = new ArrayList<>();

    public int elements;

    private final List<InterfaceDef> interfaces = new ArrayList<>();

    public static TenThousandModel build() {
        TenThousandModel model = new TenThousandModel();
        model.make();
        return model;
    }

    private void make() {
        for (int i = 0; i < 10; i++) {
            InterfaceDef def = this.named(F.createInterfaceDef(), "IF-" + i, "总线 " + i, Layer.LOGICAL);
            def.setProtocol("CAN FD");
            def.setDataType("frame " + i);
            this.interfaces.add(def);
            this.contents.add(def);
        }
        for (int i = 0; i < 590; i++) {
            this.contents.add(this.named(F.createPartDefinition(), "SA-" + i, "系统功能 " + i, Layer.SYSTEM));
        }
        this.root = this.layer(Layer.LOGICAL, "L", this.logicalSubsystems);
        this.subsystem = this.logicalSubsystems.get(0);
        this.layer(Layer.PHYSICAL, "P", this.physicalSubsystems);
        for (int i = 0; i < 500; i++) {
            Requirement requirement = this.named(F.createRequirement(), "RQ-" + i, "需求 " + i, i % 2 == 0 ? Layer.LOGICAL : Layer.PHYSICAL);
            requirement.setReqId("REQ-" + i);
            requirement.setText("系统应满足第 " + i + " 条性能要求，在规定条件下保持响应时间与可用性。");
            requirement.setVerifyMethod(VerifyMethod.TEST);
            requirement.setPriority(1 + i % 3);
            if (i % 2 == 0) {
                requirement.getSatisfiedBy().add(this.logicalSubsystems.get(i % 50));
            }
            if (i % 5 == 0) {
                requirement.getVerifiedBy().add(this.logicalSubsystems.get(i % 50));
            }
            this.contents.add(requirement);
        }
        for (int i = 0; i < 300; i++) {
            Allocate allocate = this.named(F.createAllocate(), "AL-" + i, "分配 " + i, Layer.PHYSICAL);
            allocate.setFrom(this.logicalSubsystems.get(i % 50));
            allocate.setTo(this.physicalSubsystems.get((i * 7) % 50));
            this.contents.add(allocate);
        }
    }

    private PartDefinition layer(Layer layer, String p, List<PartDefinition> subsystems) {
        List<PartDefinition> components = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            PartDefinition component = this.named(F.createPartDefinition(), p + "-CMP-" + i, layer + " 组件 " + i, layer);
            components.add(component);
            this.contents.add(component);
        }
        PartDefinition root = this.named(F.createPartDefinition(), p + "-ROOT", layer + " 系统", layer);
        root.getStereotypes().add("system");
        this.contents.add(root);
        Port previousOut = null;
        for (int s = 0; s < 50; s++) {
            PartDefinition sub = this.named(F.createPartDefinition(), p + "-SUB-" + s, layer + " 子系统 " + s, layer);
            sub.setDescription("第 " + s + " 个子系统");
            subsystems.add(sub);
            this.contents.add(sub);
            Port subOut = null;
            for (int c = 0; c < 20; c++) {
                Part part = this.named(F.createPart(), p + "-PT-" + s + "-" + c, "部件 " + s + "." + c, layer);
                part.setTypedBy(components.get((s * 20 + c) % 100));
                sub.getOwnedParts().add(part);
                Port in = this.port(part, p + "-IN-" + s + "-" + c, PortDirection.IN, layer);
                Port out = this.port(part, p + "-OUT-" + s + "-" + c, PortDirection.OUT, layer);
                if (subOut != null) {
                    this.connect(p + "-CO-" + s + "-" + c, subOut, in, layer);
                }
                subOut = out;
            }
            Part usage = this.named(F.createPart(), p + "-USE-" + s, "子系统 " + s, layer);
            usage.setTypedBy(sub);
            root.getOwnedParts().add(usage);
            Port in = this.port(usage, p + "-RIN-" + s, PortDirection.IN, layer);
            Port out = this.port(usage, p + "-ROUT-" + s, PortDirection.OUT, layer);
            if (previousOut != null) {
                this.connect(p + "-RCO-" + s, previousOut, in, layer);
            }
            previousOut = out;
        }
        return root;
    }

    private Port port(Part part, String id, PortDirection direction, Layer layer) {
        Port port = this.named(F.createPort(), id, id.toLowerCase(java.util.Locale.ROOT), layer);
        port.setDirection(direction);
        port.setInterfaceType(this.interfaces.get(Math.abs(id.hashCode()) % 1));
        part.getPorts().add(port);
        return port;
    }

    private void connect(String id, Port source, Port target, Layer layer) {
        Connection connection = this.named(F.createConnection(), id, "连接 " + id, layer);
        connection.setKind(ConnectionKind.ASSEMBLY);
        connection.setSource(source);
        connection.setTarget(target);
        connection.setInterfaceType(source.getInterfaceType());
        this.contents.add(connection);
    }

    private <T extends ArchElement> T named(T element, String id, String name, Layer layer) {
        element.setId(id);
        element.setName(name);
        element.setLayer(layer);
        this.elements++;
        return element;
    }
}

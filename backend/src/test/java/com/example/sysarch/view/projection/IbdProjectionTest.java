package com.example.sysarch.view.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.sysarch.model.Connection;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.view.api.DiagramDescription;
import com.example.sysarch.view.generated.IbdDiagramDescriptionProvider;

import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.Test;

/**
 * Projection rules that only the IBD exercises (border nodes, root frame, alternative edge styles). Full IBD
 * behaviour and auto-sync are T5.
 */
class IbdProjectionTest {

    private static final SysarchFactory FACTORY = SysarchFactory.eINSTANCE;

    private final DiagramDescription ibd = new IbdDiagramDescriptionProvider().build();

    @Test
    void partsOfTheRootWithPortsOnTheirBorders() {
        Resource document = new ResourceImpl(URI.createURI("memory:/ibd-fixture.xmi"));
        PartDefinition controller = definition("PD-1", "Controller");
        PartDefinition vehicle = definition("PD-2", "Vehicle");
        PartDefinition other = definition("PD-3", "Other");
        document.getContents().addAll(List.of(controller, vehicle, other));

        Port external = port("PO-1", "ext", "CAN");
        vehicle.getOwnedPorts().add(external); // on the diagram frame
        Part ctrl = FACTORY.createPart();
        ctrl.setId("PA-1");
        ctrl.setName("ctrl");
        ctrl.setTypedBy(controller);
        vehicle.getOwnedParts().add(ctrl);
        Port ctrlPort = port("PO-2", "can0", "CAN");
        ctrl.getPorts().add(ctrlPort);
        Part elsewhere = FACTORY.createPart();
        elsewhere.setId("PA-2");
        elsewhere.setName("notInThisDiagram");
        elsewhere.setTypedBy(controller);
        other.getOwnedParts().add(elsewhere);

        Connection connection = FACTORY.createConnection();
        connection.setId("CO-1");
        connection.setSource(external);
        connection.setTarget(ctrlPort);
        connection.setInterfaceType("CAN");
        document.getContents().add(connection);

        DiagramSnapshot snapshot = DiagramProjector.project(this.ibd, vehicle);

        assertEquals("""
                node PA-1 [part] "ctrl" / "Controller [1]"
                node PO-2 [portOnPart] on PA-1 "can0"
                node PO-1 [portOnDefinition] on PD-2 "ext"
                edge PO-1 -> PO-2 [assembly] "CAN"
                """, snapshot.describe());
    }

    private static PartDefinition definition(String id, String name) {
        PartDefinition definition = FACTORY.createPartDefinition();
        definition.setId(id);
        definition.setName(name);
        return definition;
    }

    private static Port port(String id, String name, String interfaceType) {
        Port port = FACTORY.createPort();
        port.setId(id);
        port.setName(name);
        port.setInterfaceType(com.example.sysarch.TestInterfaces.named(interfaceType));
        return port;
    }
}

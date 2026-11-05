package com.example.sysarch.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.model.provider.SysarchItemProviderAdapterFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.edit.provider.IItemLabelProvider;
import org.junit.jupiter.api.Test;

/**
 * T2 acceptance: every concrete sysarch class can be created through the EMF factory, with layer and
 * stereotypes set, and survives an XMI save/load round trip (spec §3.3, AC-2).
 */
class ModelSmokeTest {

    private static final SysarchFactory FACTORY = SysarchFactory.eINSTANCE;

    private static final SysarchPackage PACKAGE = SysarchPackage.eINSTANCE;

    @Test
    void sevenSpecClassesAreInstantiableAndArchElementIsAbstract() {
        List<EClass> concrete = List.of(PACKAGE.getPartDefinition(), PACKAGE.getPart(), PACKAGE.getPort(),
                PACKAGE.getConnection(), PACKAGE.getItemFlow(), PACKAGE.getRequirement(), PACKAGE.getAllocate());
        for (EClass eClass : concrete) {
            EObject instance = FACTORY.create(eClass);
            assertInstanceOf(ArchElement.class, instance, eClass.getName());
            assertFalse(eClass.isAbstract(), eClass.getName());
        }
        assertTrue(PACKAGE.getArchElement().isAbstract());
        assertThrows(IllegalArgumentException.class, () -> FACTORY.create(PACKAGE.getArchElement()));
    }

    @Test
    void fourEnumsHaveTheirLiterals() {
        assertEnum(PACKAGE.getLayer(), "OPERATIONAL", "SYSTEM", "LOGICAL", "PHYSICAL", "EPBS");
        assertEnum(PACKAGE.getPortDirection(), "IN", "OUT", "INOUT");
        assertEnum(PACKAGE.getConnectionKind(), "ASSEMBLY", "DELEGATION", "ITEM_FLOW");
        assertEnum(PACKAGE.getVerifyMethod(), "ANALYSIS", "INSPECTION", "TEST", "DEMONSTRATION");
    }

    @Test
    void defaultsComeFromTheEcore() {
        assertEquals(Layer.LOGICAL, FACTORY.createPart().getLayer());
        assertEquals(PortDirection.INOUT, FACTORY.createPort().getDirection());
        assertEquals(ConnectionKind.ASSEMBLY, FACTORY.createConnection().getKind());
        assertEquals(VerifyMethod.TEST, FACTORY.createRequirement().getVerifyMethod());
        assertEquals(3, FACTORY.createRequirement().getPriority());
        assertEquals("1", FACTORY.createPart().getMultiplicity());
    }

    @Test
    void modelRoundTripsThroughXmi() throws IOException {
        PartDefinition controller = element(FACTORY.createPartDefinition(), "PD-1", "DomainController", Layer.LOGICAL, "LogicalComponent");
        Port canPort = element(FACTORY.createPort(), "PO-1", "can0", Layer.LOGICAL, "LogicalPort");
        canPort.setDirection(PortDirection.OUT);
        canPort.setInterfaceType("CAN");
        controller.getOwnedPorts().add(canPort);

        PartDefinition vehicle = element(FACTORY.createPartDefinition(), "PD-2", "Vehicle", Layer.LOGICAL);
        Part controllerPart = element(FACTORY.createPart(), "PA-1", "ctrl", Layer.LOGICAL);
        controllerPart.setTypedBy(controller);
        vehicle.getOwnedParts().add(controllerPart);
        Port partPort = element(FACTORY.createPort(), "PO-2", "can0", Layer.LOGICAL);
        partPort.setDirection(PortDirection.IN);
        partPort.setInterfaceType("CAN");
        controllerPart.getPorts().add(partPort);

        Connection connection = element(FACTORY.createConnection(), "CO-1", "bus", Layer.LOGICAL);
        connection.setSource(canPort);
        connection.setTarget(partPort);
        ItemFlow flow = element(FACTORY.createItemFlow(), "IF-1", "speed", Layer.LOGICAL);
        flow.setConnection(connection);
        flow.setItemName("vehicleSpeed");

        Requirement requirement = element(FACTORY.createRequirement(), "RQ-1", "Speed broadcast", Layer.SYSTEM);
        requirement.setText("The controller shall broadcast vehicle speed every 10 ms.");
        PartDefinition ecu = element(FACTORY.createPartDefinition(), "PD-3", "ECU", Layer.PHYSICAL, "PhysicalComponent");
        Allocate allocate = element(FACTORY.createAllocate(), "AL-1", "ctrl-to-ecu", Layer.LOGICAL);
        allocate.setFrom(controller);
        allocate.setTo(ecu);

        List<EObject> roots = List.of(controller, vehicle, ecu, connection, flow, requirement, allocate);
        Resource loaded = load(save(roots));

        assertEquals(roots.size(), loaded.getContents().size());
        PartDefinition loadedController = (PartDefinition) loaded.getEObject("PD-1");
        assertEquals("DomainController", loadedController.getName());
        assertEquals(Layer.LOGICAL, loadedController.getLayer());
        assertEquals(List.of("LogicalComponent"), loadedController.getStereotypes());
        Port loadedCanPort = loadedController.getOwnedPorts().get(0);
        assertSame(loadedController, loadedCanPort.getOwner());
        assertEquals(PortDirection.OUT, loadedCanPort.getDirection());
        assertEquals(List.of("LogicalPort"), loadedCanPort.getStereotypes());

        Part loadedPart = (Part) loaded.getEObject("PA-1");
        assertSame(loadedController, loadedPart.getTypedBy());
        assertSame(loadedPart, loadedPart.getPorts().get(0).getPartOwner());

        Connection loadedConnection = (Connection) loaded.getEObject("CO-1");
        assertSame(loadedCanPort, loadedConnection.getSource());
        assertSame(loadedPart.getPorts().get(0), loadedConnection.getTarget());
        assertSame(loadedConnection, ((ItemFlow) loaded.getEObject("IF-1")).getConnection());

        Requirement loadedRequirement = (Requirement) loaded.getEObject("RQ-1");
        assertEquals(Layer.SYSTEM, loadedRequirement.getLayer());
        assertEquals(requirement.getText(), loadedRequirement.getText());

        Allocate loadedAllocate = (Allocate) loaded.getEObject("AL-1");
        assertSame(loadedController, loadedAllocate.getFrom());
        assertEquals(Layer.PHYSICAL, loadedAllocate.getTo().getLayer());
        assertEquals(List.of("PhysicalComponent"), loadedAllocate.getTo().getStereotypes());
    }

    /** Sirius Web shows labels through the edit layer, so plugin.properties must be found on the classpath. */
    @Test
    void editLayerProvidesLabels() {
        PartDefinition block = FACTORY.createPartDefinition();
        block.setName("DomainController");
        var adapterFactory = new SysarchItemProviderAdapterFactory();
        var labelProvider = (IItemLabelProvider) adapterFactory.adapt(block, IItemLabelProvider.class);
        assertEquals("Part Definition DomainController", labelProvider.getText(block));
        adapterFactory.dispose();
    }

    private static <T extends ArchElement> T element(T element, String id, String name, Layer layer, String... stereotypes) {
        element.setId(id);
        element.setName(name);
        element.setLayer(layer);
        element.getStereotypes().addAll(List.of(stereotypes));
        return element;
    }

    private static void assertEnum(EEnum eEnum, String... literals) {
        assertEquals(List.of(literals), eEnum.getELiterals().stream().map(EEnumLiteral::getLiteral).toList(), eEnum.getName());
    }

    private static ResourceSet resourceSet() {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        resourceSet.getPackageRegistry().put(SysarchPackage.eNS_URI, PACKAGE);
        return resourceSet;
    }

    private static byte[] save(List<EObject> roots) throws IOException {
        Resource resource = resourceSet().createResource(URI.createURI("memory:/model.xmi"));
        resource.getContents().addAll(roots);
        var out = new ByteArrayOutputStream();
        resource.save(out, Map.of());
        return out.toByteArray();
    }

    private static Resource load(byte[] xmi) throws IOException {
        Resource resource = resourceSet().createResource(URI.createURI("memory:/model.xmi"));
        resource.load(new ByteArrayInputStream(xmi), Map.of());
        return resource;
    }
}

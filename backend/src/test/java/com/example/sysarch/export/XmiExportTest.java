package com.example.sysarch.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.Requirement;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * P3 T7 (spec §5.1): the demo as XMI 2.5.1 / UML 2.5.1 / SysML 1.6. Checked as XML (no UML tool here; Cameo is the
 * user's P3-AC-6): namespaces, the mapping table, every reference resolves, the head notice, layers in extensions,
 * deterministic. Writes build/xmi-export/domain-controller.xmi for the manual check.
 */
class XmiExportTest {

    private Resource demo;

    private String xmi;

    private Document document;

    @BeforeEach
    void export() throws Exception {
        this.demo = new ResourceImpl(URI.createURI("memory:/demo.json"));
        this.demo.getContents().addAll(DomainControllerDemo.build());
        this.xmi = XmiExporter.export("域控制器演示", List.of(this.demo));
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        this.document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(this.xmi.getBytes(StandardCharsets.UTF_8)));
        Path out = Path.of("build/xmi-export/domain-controller.xmi");
        Files.createDirectories(out.getParent());
        Files.writeString(out, this.xmi, StandardCharsets.UTF_8);
    }

    @Test
    void theRootIsXmi251WithUmlAndSysmlNamespaces() {
        Element root = this.document.getDocumentElement();
        assertEquals(XmiExporter.XMI_NS, root.getNamespaceURI());
        assertEquals("XMI", root.getLocalName());
        assertTrue(this.xmi.contains("xmlns:uml=\"" + XmiExporter.UML_NS + "\"") && this.xmi.contains("xmlns:sysml=\"" + XmiExporter.SYSML_NS + "\""));
        assertFalse(this.xmi.contains("http://www.omg.org/XMI\""), "not EMF's XMI 2.0 namespace");
        assertTrue(this.xmi.contains("href=\"" + XmiExporter.SYSML_PROFILE + "\""), "the SysML profile is applied");
        assertEquals(1, elements(XmiExporter.UML_NS, "Model").size());
    }

    @Test
    void elementsMapAsTheSpecTableSays() {
        long blocks = this.demo.getContents().stream().filter(PartDefinition.class::isInstance).count();
        long requirements = this.demo.getContents().stream().filter(Requirement.class::isInstance).count();
        assertEquals(blocks, elements(XmiExporter.SYSML_NS, "Block").size(), "every block has «Block»");
        assertEquals(requirements, elements(XmiExporter.SYSML_NS, "Requirement").size(), "every requirement has «Requirement»");
        List<Element> classes = this.typed("uml:Class");
        long interfaces = elements(XmiExporter.SYSML_NS, "InterfaceBlock").size();
        assertEquals(blocks + requirements + interfaces, classes.size(), "blocks, interfaces and requirements are uml:Class");
        List<EObject> all = new ArrayList<>();
        this.demo.getAllContents().forEachRemaining(all::add);
        assertEquals(all.stream().filter(Part.class::isInstance).count(), this.typed("uml:Property").size(), "parts are uml:Property");
        assertEquals(all.stream().filter(Port.class::isInstance).count(), this.typed("uml:Port").size(), "ports are uml:Port");
        assertEquals(all.stream().filter(Connection.class::isInstance).count(), this.typed("uml:Connector").size(), "connections are uml:Connector");
        for (Element connector : this.typed("uml:Connector")) {
            assertEquals(2, children(connector, "end").size(), "two ends");
        }
        for (Element property : this.typed("uml:Property")) {
            assertEquals("composite", property.getAttribute("aggregation"), "a part is a composite property");
            assertFalse(property.getAttribute("type").isEmpty(), "typed by its block");
        }
        Element requirement = elements(XmiExporter.SYSML_NS, "Requirement").get(0);
        assertFalse(requirement.getAttribute("text").isEmpty(), "requirement text on the stereotype");
        assertEquals(this.demo.getContents().stream().filter(Requirement.class::isInstance).mapToLong(r -> ((Requirement) r).getSatisfiedBy().size()).sum(),
                elements(XmiExporter.SYSML_NS, "Satisfy").size(), "satisfiedBy = «Satisfy» abstractions");
    }

    @Test
    void everyReferenceResolvesAndIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        List<String> refs = new ArrayList<>();
        walk(this.document.getDocumentElement(), ids, refs);
        for (String ref : refs) {
            for (String id : ref.trim().split("\\s+")) {
                assertTrue(ids.contains(id), "unresolved reference " + id);
            }
        }
        assertTrue(refs.size() > 30, "references were checked: " + refs.size());
        assertTrue(ids.contains(DomainControllerDemo.SYSTEM_ID), "element ids are the xmi:ids");
    }

    @Test
    void theHeadSaysLayersAreExtensionsAndElementsCarryThem() {
        Element documentation = elements(XmiExporter.XMI_NS, "Documentation").get(0);
        assertEquals(documentation, firstElement(this.document.getDocumentElement()), "the first thing in the file");
        assertTrue(documentation.getTextContent().contains("分层信息为扩展注释，标准工具不可见"));
        assertTrue(this.xmi.indexOf("<!--") < this.xmi.indexOf("<xmi:Documentation"), "and an XML comment above it");
        Element system = this.byId(DomainControllerDemo.SYSTEM_ID);
        Element extension = firstElement(system);
        assertEquals("Extension", extension.getLocalName());
        assertEquals(XmiExporter.EXTENDER, extension.getAttribute("extender"));
        assertEquals("LOGICAL", children(extension, "layer").get(0).getAttribute("value"));
        assertEquals("system", children(extension, "stereotype").get(0).getAttribute("value"));
    }

    @Test
    void theOutputIsDeterministic() {
        Resource again = new ResourceImpl(URI.createURI("memory:/demo2.json"));
        again.getContents().addAll(DomainControllerDemo.build());
        assertEquals(this.xmi, XmiExporter.export("域控制器演示", List.of(again)));
    }

    /** A port of a part lives on the part's type (UML), naming the part; cross-block connections, item flows and odd ids. */
    @Test
    void edgeCasesKeepTheirDataInTheExtension() throws Exception {
        var f = com.example.sysarch.model.SysarchFactory.eINSTANCE;
        PartDefinition car = f.createPartDefinition();
        car.setId("CAR");
        car.setName("车");
        PartDefinition ecu = f.createPartDefinition();
        ecu.setId("ECU 1"); // not an XML name
        ecu.setName("控制器");
        PartDefinition other = f.createPartDefinition();
        other.setId("OTHER");
        other.setName("别的块");
        Part part = f.createPart();
        part.setId("P1");
        part.setName("ecu");
        part.setTypedBy(ecu);
        part.setMultiplicity("2..*");
        car.getOwnedParts().add(part);
        Port onPart = f.createPort();
        onPart.setId("PP");
        onPart.setName("can");
        onPart.setDirection(com.example.sysarch.model.PortDirection.OUT);
        part.getPorts().add(onPart);
        Port onOther = f.createPort();
        onOther.setId("PO");
        onOther.setName("in");
        other.getOwnedPorts().add(onOther);
        Connection cross = f.createConnection();
        cross.setId("C1");
        cross.setName("跨块");
        cross.setSource(onPart);
        cross.setTarget(onOther);
        com.example.sysarch.model.ItemFlow flow = f.createItemFlow();
        flow.setId("IF1");
        flow.setName("帧");
        flow.setItemName("frame");
        flow.setItemType("CanFrame");
        flow.setConnection(cross);
        com.example.sysarch.model.StateMachine machine = f.createStateMachine();
        machine.setId("SM");
        machine.setName("状态机");
        Resource r = new ResourceImpl(URI.createURI("memory:/edge.json"));
        r.getContents().addAll(List.of(car, ecu, other, cross, flow, machine));
        String text = XmiExporter.export("边界", List.of(r));
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        this.document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        Element ecuClass = this.byId("_ECU_1");
        Element ext = firstElement(ecuClass);
        assertEquals("ECU 1", children(ext, "id").get(0).getAttribute("value"), "the original id survives a sanitized xmi:id");
        Element port = this.byId("PP");
        assertEquals(ecuClass, port.getParentNode(), "a part's port is a port of the part's type");
        Element portExt = firstElement(port);
        assertEquals("P1", children(portExt, "partOwner").get(0).getAttribute("value"));
        assertEquals("OUT", children(portExt, "direction").get(0).getAttribute("value"));
        Element connector = this.byId("C1");
        assertEquals("CAR", ((Element) connector.getParentNode()).getAttributeNS(XmiExporter.XMI_NS, "id"), "cross-block: on the source's block");
        Element connectorExt = firstElement(connector);
        assertEquals("true", children(connectorExt, "crossBlock").get(0).getAttribute("value"));
        assertEquals("IF1|帧|frame|CanFrame|LOGICAL", children(connectorExt, "itemFlow").get(0).getAttribute("value"));
        assertEquals("P1", children(connector, "end").get(0).getAttribute("partWithPort"));
        Element part1 = this.byId("P1");
        assertEquals("2", children(part1, "lowerValue").get(0).getAttribute("value"));
        assertEquals("*", children(part1, "upperValue").get(0).getAttribute("value"));
        assertTrue(text.contains("未导出") && text.contains("StateMachine 1"), "behaviour elements are listed as not exported");
    }

    // ------------------------------------------------------------------ helpers

    private List<Element> typed(String type) {
        List<Element> out = new ArrayList<>();
        collectTyped(this.document.getDocumentElement(), type, out);
        return out;
    }

    private static void collectTyped(Element element, String type, List<Element> out) {
        if (type.equals(element.getAttributeNS(XmiExporter.XMI_NS, "type"))) {
            out.add(element);
        }
        for (Node c = element.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e) {
                collectTyped(e, type, out);
            }
        }
    }

    private Element byId(String id) {
        List<Element> out = new ArrayList<>();
        find(this.document.getDocumentElement(), id, out);
        return out.get(0);
    }

    private static void find(Element element, String id, List<Element> out) {
        if (id.equals(element.getAttributeNS(XmiExporter.XMI_NS, "id"))) {
            out.add(element);
        }
        for (Node c = element.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e) {
                find(e, id, out);
            }
        }
    }

    /** ids, and the values of every reference attribute (type, general, role, partWithPort, client, supplier, base_*, annotatedElement). */
    private static void walk(Element element, Set<String> ids, List<String> refs) {
        String id = element.getAttributeNS(XmiExporter.XMI_NS, "id");
        if (!id.isEmpty()) {
            assertTrue(ids.add(id), "duplicate xmi:id " + id);
        }
        for (String attribute : List.of("type", "general", "role", "partWithPort", "client", "supplier", "base_Class", "base_Abstraction", "annotatedElement")) {
            if (element.hasAttribute(attribute) && !(attribute.equals("type") && element.getNamespaceURI() == null && element.getLocalName().equals("x"))) {
                refs.add(element.getAttribute(attribute));
            }
        }
        for (Node c = element.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e && !"Extension".equals(e.getLocalName())) {
                walk(e, ids, refs);
            }
        }
    }

    private List<Element> elements(String ns, String name) {
        var list = this.document.getElementsByTagNameNS(ns, name);
        List<Element> out = new ArrayList<>();
        for (int i = 0; i < list.getLength(); i++) {
            out.add((Element) list.item(i));
        }
        return out;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> out = new ArrayList<>();
        for (Node c = parent.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e && name.equals(e.getLocalName())) {
                out.add(e);
            }
        }
        return out;
    }

    private static Element firstElement(Element parent) {
        for (Node c = parent.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e) {
                return e;
            }
        }
        return null;
    }
}

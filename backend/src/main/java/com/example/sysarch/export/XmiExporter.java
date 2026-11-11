package com.example.sysarch.export;

import com.example.sysarch.ElementIds;
import com.example.sysarch.model.Allocate;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.InterfaceDef;
import com.example.sysarch.model.ItemFlow;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.Requirement;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * P3 T7 (spec §5.1): the model as OMG XMI 2.5.1 with UML 2.5.1 elements and SysML 1.6 stereotype applications, written
 * by EMF's own {@link XMIResourceImpl} (a minimal UML/SysML subset is built as dynamic EPackages, so no UML2 dependency).
 *
 * <table>
 * <tr><th>SysArch</th><th>XMI</th></tr>
 * <tr><td>PartDefinition</td><td>{@code uml:Class} + {@code sysml:Block}; superTypes = {@code uml:Generalization}</td></tr>
 * <tr><td>Part</td><td>{@code uml:Property} of its block, typed by the part's type, aggregation composite, multiplicity as
 * lower/upper value (SysML 1.6 has no «part» stereotype: Cameo shows a composite block-typed property as a part)</td></tr>
 * <tr><td>Port</td><td>{@code uml:Port} typed by its interface; a port of a part belongs to the part's type in UML, so it
 * is written there (else on the part's block) and the extension names the part</td></tr>
 * <tr><td>Connection</td><td>{@code uml:Connector} of the block holding both ends, ends = port + partWithPort</td></tr>
 * <tr><td>InterfaceDef</td><td>{@code uml:Class} + {@code sysml:InterfaceBlock}</td></tr>
 * <tr><td>Requirement</td><td>{@code uml:Class} + {@code sysml:Requirement} (id = reqId, text)</td></tr>
 * <tr><td>satisfiedBy / verifiedBy / Allocate</td><td>{@code uml:Abstraction} + {@code sysml:Satisfy} / {@code Verify} /
 * {@code Allocate}</td></tr>
 * <tr><td>description</td><td>{@code uml:Comment} owned by the element</td></tr>
 * </table>
 *
 * <p><b>Not standard</b> (spec: "必须承认的损失"): {@code layer}, {@code stereotypes} and the fields UML has no place for
 * (port direction, interface protocol and data type, requirement verify method and priority, connection kind, interface
 * and item flows, allocation rationale) go into the element's {@code xmi:Extension extender="SysArch Studio"}, which
 * standard tools skip; the file head says so in {@code xmi:Documentation}. Parametric and behaviour elements are not
 * exported (the head lists how many). Deterministic: ids are the element ids, elements in document order, no time stamp.
 */
public final class XmiExporter {

    public static final String XMI_NS = "http://www.omg.org/spec/XMI/20131001";

    public static final String UML_NS = "http://www.omg.org/spec/UML/20161101";

    public static final String SYSML_NS = "http://www.omg.org/spec/SysML/20181001/SysML";

    /** The OMG SysML 1.6 profile, applied by reference (tools resolve the standard URI). */
    public static final String SYSML_PROFILE = "http://www.omg.org/spec/SysML/20181001/SysML.xmi#SysML";

    public static final String EXTENDER = "SysArch Studio";

    public static final String NOTICE = "分层信息为扩展注释，标准工具不可见：layer、stereotypes 及 UML 没有对应项的字段写在各元素的 xmi:Extension（extender = SysArch Studio）中，"
            + "标准工具（Cameo、Papyrus）会忽略它们；元素与关系用标准 UML 2.5.1 / SysML 1.6 表达。XMI 往返不保证分层无损，需要无损时请用 SysML v2 文本导出。";

    private static final Pattern NC_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");

    private static final Pattern MULTIPLICITY = Pattern.compile("(\\d+)(?:\\.\\.(\\d+|\\*))?|\\*");

    // ------------------------------------------------------------------ the UML / SysML subset as dynamic EPackages

    static final EPackage UML = EcoreFactory.eINSTANCE.createEPackage();

    static final EPackage SYSML = EcoreFactory.eINSTANCE.createEPackage();

    private static final EClass ELEMENT = eClass(UML, "Element", true);

    private static final EClass COMMENT = eClass(UML, "Comment", false);

    private static final EClass MODEL = eClass(UML, "Model", false);

    private static final EClass PROFILE_APPLICATION = eClass(UML, "ProfileApplication", false);

    private static final EClass PROFILE = eClass(UML, "Profile", false);

    private static final EClass CLASS = eClass(UML, "Class", false);

    private static final EClass GENERALIZATION = eClass(UML, "Generalization", false);

    private static final EClass PROPERTY = eClass(UML, "Property", false);

    private static final EClass PORT = eClass(UML, "Port", false);

    private static final EClass LITERAL_INTEGER = eClass(UML, "LiteralInteger", false);

    private static final EClass LITERAL_UNLIMITED = eClass(UML, "LiteralUnlimitedNatural", false);

    private static final EClass CONNECTOR = eClass(UML, "Connector", false);

    private static final EClass CONNECTOR_END = eClass(UML, "ConnectorEnd", false);

    private static final EClass ABSTRACTION = eClass(UML, "Abstraction", false);

    static {
        UML.setName("uml");
        UML.setNsPrefix("uml");
        UML.setNsURI(UML_NS);
        SYSML.setName("sysml");
        SYSML.setNsPrefix("sysml");
        SYSML.setNsURI(SYSML_NS);
        for (EClass c : List.of(COMMENT, MODEL, PROFILE_APPLICATION, PROFILE, CLASS, GENERALIZATION, PROPERTY, LITERAL_INTEGER, LITERAL_UNLIMITED,
                CONNECTOR, CONNECTOR_END, ABSTRACTION)) {
            c.getESuperTypes().add(ELEMENT);
        }
        PORT.getESuperTypes().add(PROPERTY);
        // every containment is typed Element, so xmi:type is always written (canonical XMI)
        containment(ELEMENT, "ownedComment");
        attribute(COMMENT, "body");
        reference(COMMENT, "annotatedElement", true);
        for (EClass named : List.of(MODEL, CLASS, PROPERTY, CONNECTOR, ABSTRACTION)) {
            if (named.getEStructuralFeature("name") == null) {
                attribute(named, "name");
            }
        }
        containment(MODEL, "profileApplication");
        containment(MODEL, "packagedElement");
        reference(PROFILE_APPLICATION, "appliedProfile", false);
        bool(CLASS, "isAbstract");
        containment(CLASS, "generalization");
        containment(CLASS, "ownedAttribute");
        containment(CLASS, "ownedConnector");
        reference(GENERALIZATION, "general", false);
        reference(PROPERTY, "type", false);
        attribute(PROPERTY, "aggregation");
        containment(PROPERTY, "lowerValue");
        containment(PROPERTY, "upperValue");
        bool(PORT, "isConjugated");
        attribute(LITERAL_INTEGER, "value");
        attribute(LITERAL_UNLIMITED, "value");
        containment(CONNECTOR, "end");
        reference(CONNECTOR_END, "role", false);
        reference(CONNECTOR_END, "partWithPort", false);
        reference(ABSTRACTION, "client", true);
        reference(ABSTRACTION, "supplier", true);
        for (String name : List.of("Block", "InterfaceBlock", "Requirement")) {
            reference(eClass(SYSML, name, false), "base_Class", false);
        }
        attribute((EClass) SYSML.getEClassifier("Requirement"), "id");
        attribute((EClass) SYSML.getEClassifier("Requirement"), "text");
        for (String name : List.of("Satisfy", "Verify", "Allocate")) {
            reference(eClass(SYSML, name, false), "base_Abstraction", false);
        }
    }

    private static EClass eClass(EPackage pkg, String name, boolean isAbstract) {
        EClass c = EcoreFactory.eINSTANCE.createEClass();
        c.setName(name);
        c.setAbstract(isAbstract);
        pkg.getEClassifiers().add(c);
        return c;
    }

    private static void attribute(EClass owner, String name) {
        EAttribute a = EcoreFactory.eINSTANCE.createEAttribute();
        a.setName(name);
        a.setEType(EcorePackage.Literals.ESTRING);
        owner.getEStructuralFeatures().add(a);
    }

    private static void bool(EClass owner, String name) {
        EAttribute a = EcoreFactory.eINSTANCE.createEAttribute();
        a.setName(name);
        a.setEType(EcorePackage.Literals.EBOOLEAN);
        owner.getEStructuralFeatures().add(a);
    }

    private static void containment(EClass owner, String name) {
        EReference r = EcoreFactory.eINSTANCE.createEReference();
        r.setName(name);
        r.setEType(ELEMENT);
        r.setContainment(true);
        r.setUpperBound(-1);
        owner.getEStructuralFeatures().add(r);
    }

    private static void reference(EClass owner, String name, boolean many) {
        EReference r = EcoreFactory.eINSTANCE.createEReference();
        r.setName(name);
        r.setEType(ELEMENT == null ? EcorePackage.Literals.EOBJECT : ELEMENT);
        r.setUpperBound(many ? -1 : 1);
        owner.getEStructuralFeatures().add(r);
    }

    // ------------------------------------------------------------------ export

    /** Writes XMI 2.5.1: the XMI namespace and no xmi:version (as Cameo and Papyrus write it). */
    private static final class Xmi251Resource extends XMIResourceImpl {
        Xmi251Resource() {
            super(URI.createURI("sysarch-export.xmi"));
            this.setXMIVersion("20131001"); // removed after saving: XMI 2.5.1 files carry no xmi:version
        }

        @Override
        protected boolean useUUIDs() {
            return false;
        }

        @Override
        public String getXMINamespace() {
            return XmiExporter.XMI_NS; // not the inherited XMIResource.XMI_NS (= "xmi")
        }
    }

    private final Xmi251Resource resource = new Xmi251Resource();

    private final EObject model = EcoreUtil.create(MODEL);

    private final Map<EObject, String> ids = new HashMap<>();

    /** extension data per exported xmi:id: name -> values, in order */
    private final Map<String, Map<String, List<String>>> extensions = new LinkedHashMap<>();

    private final List<EObject> applications = new ArrayList<>();

    private final Map<String, Integer> exported = new TreeMap<>();

    private final Map<String, Integer> skipped = new TreeMap<>();

    private final Map<ArchElement, EObject> umlOf = new HashMap<>();

    private final Map<String, Integer> usedIds = new HashMap<>();

    /** Item flows (document roots) by the connection they ride on. */
    private final Map<Connection, List<ItemFlow>> itemFlows = new HashMap<>();

    private XmiExporter() {
    }

    public static String export(String modelName, Collection<Resource> resources) {
        return new XmiExporter().run(modelName, resources);
    }

    /**
     * Which UML namespace the file declares. SysML 1.6 is defined on UML 2.5.1 (the default); some importers only know
     * UML 2.5 (Eclipse UML2 5.5 does, found by the P3-AC-6 proxy check). Everything SysArch writes is the same in both,
     * so only the {@code xmlns:uml} declaration (and the head notice) differ.
     */
    public enum UmlVersion {
        V2_5_1("2.5.1", UML_NS), V2_5("2.5", "http://www.omg.org/spec/UML/20131001");

        private final String label;

        private final String namespace;

        UmlVersion(String label, String namespace) {
            this.label = label;
            this.namespace = namespace;
        }

        public String label() {
            return this.label;
        }

        public String namespace() {
            return this.namespace;
        }

        /** "2.5.1" or "2.5" (null or blank = 2.5.1); anything else null. */
        public static UmlVersion of(String label) {
            if (label == null || label.isBlank()) {
                return V2_5_1;
            }
            for (UmlVersion v : values()) {
                if (v.label.equals(label.strip())) {
                    return v;
                }
            }
            return null;
        }
    }

    /**
     * Who reads the file. {@code OMG} (default): canonical XMI 2.5.1 with the OMG SysML namespace, as Cameo reads and
     * writes it. {@code PAPYRUS}: the form Eclipse Papyrus (SysML 1.6) stores, found by letting Papyrus' own API save a
     * model: Eclipse UML2's UML namespace, the SysML stereotypes in Papyrus' per-package namespaces with the extra base
     * references Papyrus' static profile has, and one profile application per SysML sub-profile pointing at Papyrus'
     * profile ({@code pathmap://SysML16_PROFILES/…}). Same elements, ids and extensions; Papyrus opens it as a model.
     */
    public enum Flavour {
        OMG, PAPYRUS;

        /** "omg" or "papyrus" (null or blank = omg); anything else null. */
        public static Flavour of(String name) {
            if (name == null || name.isBlank()) {
                return OMG;
            }
            for (Flavour f : values()) {
                if (f.name().equalsIgnoreCase(name.strip())) {
                    return f;
                }
            }
            return null;
        }
    }

    static final String ECLIPSE_UML_NS = "http://www.eclipse.org/uml2/5.0.0/UML";

    static final String PAPYRUS_SYSML = "http://www.eclipse.org/papyrus/sysml/1.6/SysML";

    /** Papyrus' sub-profiles we use: prefix, namespace suffix, profile id in SysML.profile.uml, EPackage fragment. */
    private static final String[][] PAPYRUS_PROFILES = { { "Allocations", "/Allocations", "SysML.package_packagedElement_Allocations", "//allocations" },
            { "Blocks", "/Blocks", "SysML.package_packagedElement_Blocks", "//blocks" },
            { "PortsAndFlows", "/PortsAndFlows", "SysML.package_packagedElement_Ports_u0026Flows", "//portsandflows" },
            { "Requirements", "/Requirements", "SysML.package_packagedElement_Requirements", "//requirements" } };

    public static String export(String modelName, Collection<Resource> resources, UmlVersion version, Flavour flavour) {
        if (flavour == Flavour.PAPYRUS) {
            return papyrus(export(modelName, resources));
        }
        return export(modelName, resources, version);
    }

    /** Our canonical output (fixed format, attributes in alphabetical order) rewritten into Papyrus' form. */
    static String papyrus(String xmi) {
        StringBuilder namespaces = new StringBuilder("xmlns:ecore=\"http://www.eclipse.org/emf/2002/Ecore\"");
        StringBuilder schemaLocation = new StringBuilder();
        for (String[] p : PAPYRUS_PROFILES) {
            namespaces.append(" xmlns:").append(p[0]).append("=\"").append(PAPYRUS_SYSML).append(p[1]).append('"');
            schemaLocation.append(schemaLocation.isEmpty() ? "" : " ").append(PAPYRUS_SYSML).append(p[1]).append(' ').append(PAPYRUS_SYSML).append('#').append(p[3]);
        }
        String out = xmi
                .replace("<xmi:XMI xmlns:xmi=\"" + XMI_NS + "\"", "<xmi:XMI xmi:version=\"20131001\" xmlns:xmi=\"" + XMI_NS + "\"")
                .replace("xmlns:sysml=\"" + SYSML_NS + "\"", namespaces + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
                .replace("xmlns:uml=\"" + UML_NS + "\"", "xmlns:uml=\"" + ECLIPSE_UML_NS + "\" xsi:schemaLocation=\"" + schemaLocation + "\"")
                .replace("UML 2.5.1 / SysML 1.6", "Eclipse UML2 / Papyrus SysML 1.6")
                .replaceAll("<sysml:Block ", "<Blocks:Block ")
                .replaceAll("<sysml:InterfaceBlock ", "<PortsAndFlows:InterfaceBlock ")
                .replaceAll("<sysml:Requirement base_Class=\"([^\"]*)\"", "<Requirements:Requirement base_Class=\"$1\" base_NamedElement=\"$1\"")
                .replaceAll("<sysml:(Satisfy|Verify) base_Abstraction=\"([^\"]*)\"", "<Requirements:$1 base_Abstraction=\"$2\" base_DirectedRelationship=\"$2\"")
                .replaceAll("<sysml:Allocate base_Abstraction=\"([^\"]*)\"", "<Allocations:Allocate base_Abstraction=\"$1\" base_DirectedRelationship=\"$1\"");
        // one profile application per profile, as Papyrus writes them (the root SysML profile plus the sub-profiles used)
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("( *)<profileApplication xmi:id=\"([^\"]*)\" xmi:type=\"uml:ProfileApplication\">\\n[\\s\\S]*?</profileApplication>\\n").matcher(out);
        if (m.find()) {
            String indent = m.group(1);
            String id = m.group(2);
            StringBuilder applications = new StringBuilder();
            applications.append(profileApplication(indent, id, "SysML", "/"));
            for (String[] p : PAPYRUS_PROFILES) {
                applications.append(profileApplication(indent, id + "_" + p[0], p[2], p[3]));
            }
            out = out.substring(0, m.start()) + applications + out.substring(m.end());
        }
        return out;
    }

    private static String profileApplication(String indent, String id, String profileId, String ePackageFragment) {
        return indent + "<profileApplication xmi:id=\"" + id + "\" xmi:type=\"uml:ProfileApplication\">\n"
                + indent + "  <eAnnotations source=\"http://www.eclipse.org/uml2/2.0.0/UML\" xmi:id=\"" + id + "_annotation\">\n"
                + indent + "    <references href=\"" + PAPYRUS_SYSML + "#" + ePackageFragment + "\" xmi:type=\"ecore:EPackage\"/>\n"
                + indent + "  </eAnnotations>\n"
                + indent + "  <appliedProfile href=\"pathmap://SysML16_PROFILES/SysML.profile.uml#" + profileId + "\"/>\n"
                + indent + "</profileApplication>\n";
    }

    public static String export(String modelName, Collection<Resource> resources, UmlVersion version) {
        String xmi = export(modelName, resources);
        if (version == null || version == UmlVersion.V2_5_1) {
            return xmi;
        }
        return xmi.replace("xmlns:uml=\"" + UML_NS + "\"", "xmlns:uml=\"" + version.namespace() + "\"")
                .replace("UML 2.5.1 / SysML 1.6", "UML " + version.label() + " / SysML 1.6");
    }

    private String run(String modelName, Collection<Resource> resources) {
        List<EObject> all = new ArrayList<>();
        resources.forEach(r -> r.getAllContents().forEachRemaining(all::add));
        this.model.eSet(MODEL.getEStructuralFeature("name"), modelName);
        this.id(this.model, "model");
        EObject application = this.create(PROFILE_APPLICATION, "model_profileApplication");
        InternalEObject profile = (InternalEObject) EcoreUtil.create(PROFILE);
        profile.eSetProxyURI(URI.createURI(SYSML_PROFILE));
        application.eSet(PROFILE_APPLICATION.getEStructuralFeature("appliedProfile"), profile);
        this.list(this.model, "profileApplication").add(application);

        all.stream().filter(ItemFlow.class::isInstance).map(ItemFlow.class::cast).filter(f -> f.getConnection() != null)
                .forEach(f -> this.itemFlows.computeIfAbsent(f.getConnection(), c -> new ArrayList<>()).add(f));
        // declarations first (so references resolve), then their contents and the relations
        for (EObject o : all) {
            switch (o) {
                case PartDefinition block -> this.classifier(block, "Block");
                case InterfaceDef interfaceDef -> this.classifier(interfaceDef, "InterfaceBlock");
                case Requirement requirement -> this.classifier(requirement, "Requirement");
                default -> {
                }
            }
        }
        for (EObject o : all) {
            if (o instanceof PartDefinition block) {
                this.blockContents(block);
            } else if (o instanceof InterfaceDef interfaceDef) {
                this.generalizations(interfaceDef, interfaceDef.getSuperTypes());
                this.extra(interfaceDef, "protocol", interfaceDef.getProtocol());
                this.extra(interfaceDef, "dataType", interfaceDef.getDataType());
            }
        }
        for (EObject o : all) {
            if (o instanceof PartDefinition block) {
                block.getOwnedParts().forEach(part -> part.getPorts().forEach(port -> this.port(port, part)));
            }
        }
        for (EObject o : all) {
            switch (o) {
                case Connection connection -> this.connector(connection);
                case Requirement r -> {
                }
                case Allocate a -> {
                }
                case ItemFlow flow -> {
                    if (flow.getConnection() == null) {
                        this.skipped.merge("ItemFlow", 1, Integer::sum);
                    }
                }
                case PartDefinition b -> {
                }
                case InterfaceDef i -> {
                }
                case Part p -> {
                }
                case Port p -> {
                }
                case ArchElement other -> {
                    if (!(other instanceof com.example.sysarch.model.DiagramLayout)) {
                        this.skipped.merge(other.eClass().getName(), 1, Integer::sum);
                    }
                }
                default -> {
                }
            }
        }
        // relations in a fixed order (P3 T8: export -> import -> export gives the same text): every requirement's
        // satisfy/verify in requirement order, then the allocations in document order
        all.stream().filter(Requirement.class::isInstance).map(Requirement.class::cast).forEach(this::requirementRelations);
        all.stream().filter(Allocate.class::isInstance).map(Allocate.class::cast).forEach(this::allocate);
        this.resource.getContents().add(this.model);
        this.resource.getContents().addAll(this.applications);
        this.ids.forEach(this.resource::setID);
        return this.serialize();
    }

    // ---- classifiers

    private void classifier(ArchElement element, String stereotype) {
        EObject cls = this.create(CLASS, this.xmiId(element));
        this.umlOf.put(element, cls);
        this.named(cls, element);
        if (element instanceof PartDefinition block && block.isIsAbstract()) {
            cls.eSet(CLASS.getEStructuralFeature("isAbstract"), true);
        }
        this.list(this.model, "packagedElement").add(cls);
        EObject application = this.apply(stereotype, "base_Class", cls);
        if (element instanceof Requirement requirement) {
            EClass req = (EClass) SYSML.getEClassifier("Requirement");
            if (requirement.getReqId() != null) {
                application.eSet(req.getEStructuralFeature("id"), requirement.getReqId());
            }
            if (requirement.getText() != null) {
                application.eSet(req.getEStructuralFeature("text"), requirement.getText());
            }
            this.extra(requirement, "verifyMethod", requirement.getVerifyMethod() == null ? null : requirement.getVerifyMethod().getLiteral());
            this.extra(requirement, "priority", requirement.eIsSet(com.example.sysarch.model.SysarchPackage.Literals.REQUIREMENT__PRIORITY) ? String.valueOf(requirement.getPriority()) : null);
        }
        this.common(element, cls);
        this.exported.merge(element.eClass().getName(), 1, Integer::sum);
    }

    private void blockContents(PartDefinition block) {
        EObject cls = this.umlOf.get(block);
        this.generalizations(block, block.getSuperTypes());
        for (Part part : block.getOwnedParts()) {
            EObject property = this.create(PROPERTY, this.xmiId(part));
            this.umlOf.put(part, property);
            this.named(property, part);
            property.eSet(PROPERTY.getEStructuralFeature("aggregation"), "composite");
            if (part.getTypedBy() != null) {
                property.eSet(PROPERTY.getEStructuralFeature("type"), this.umlOf.get(part.getTypedBy()));
            }
            this.multiplicity(property, part);
            this.list(cls, "ownedAttribute").add(property);
            this.common(part, property);
            this.exported.merge("Part", 1, Integer::sum);
        }
        for (Port port : block.getOwnedPorts()) {
            this.port(port, null);
        }
    }

    private void generalizations(ArchElement element, List<? extends ArchElement> superTypes) {
        EObject cls = this.umlOf.get(element);
        int n = 0;
        for (ArchElement superType : superTypes) {
            EObject generalization = this.create(GENERALIZATION, this.xmiId(element) + "_generalization" + (++n));
            generalization.eSet(GENERALIZATION.getEStructuralFeature("general"), this.umlOf.get(superType));
            this.list(cls, "generalization").add(generalization);
        }
    }

    private void multiplicity(EObject property, Part part) {
        String text = part.getMultiplicity() == null ? null : part.getMultiplicity().replace(" ", "");
        var m = text == null ? null : MULTIPLICITY.matcher(text);
        if (m == null || !m.matches()) {
            this.extra(part, "multiplicity", text);
            return;
        }
        String lower = text.equals("*") ? "0" : m.group(1);
        String upper = text.equals("*") ? "*" : m.group(2) != null ? m.group(2) : m.group(1);
        EObject low = this.create(LITERAL_INTEGER, this.ids.get(property) + "_lower");
        low.eSet(LITERAL_INTEGER.getEStructuralFeature("value"), lower);
        EObject up = this.create(LITERAL_UNLIMITED, this.ids.get(property) + "_upper");
        up.eSet(LITERAL_UNLIMITED.getEStructuralFeature("value"), upper);
        property.eSet(PROPERTY.getEStructuralFeature("lowerValue"), List.of(low));
        property.eSet(PROPERTY.getEStructuralFeature("upperValue"), List.of(up));
    }

    /** A port of a block on that block; a port of a part on the part's type (else the part's block), naming the part. */
    private void port(Port port, Part part) {
        PartDefinition home = part == null ? port.getOwner() : part.getTypedBy() != null ? part.getTypedBy() : part.getOwner();
        EObject owner = this.umlOf.get(home);
        if (owner == null) {
            this.skipped.merge("Port", 1, Integer::sum);
            return;
        }
        EObject uml = this.create(PORT, this.xmiId(port));
        this.umlOf.put(port, uml);
        this.named(uml, port);
        uml.eSet(PROPERTY.getEStructuralFeature("aggregation"), "composite");
        if (port.getInterfaceType() != null) {
            uml.eSet(PROPERTY.getEStructuralFeature("type"), this.umlOf.get(port.getInterfaceType()));
        }
        if (port.isIsConjugated()) {
            uml.eSet(PORT.getEStructuralFeature("isConjugated"), true);
        }
        this.list(owner, "ownedAttribute").add(uml);
        this.extra(port, "direction", port.getDirection() == null ? null : port.getDirection().getLiteral());
        if (part != null) {
            this.extra(port, "partOwner", this.xmiId(part));
        }
        this.common(port, uml);
        this.exported.merge("Port", 1, Integer::sum);
    }

    private void connector(Connection connection) {
        PartDefinition block = SysmlV2Exporter.commonBlock(connection);
        boolean crossBlock = block == null;
        if (crossBlock) {
            block = SysmlV2Exporter.blockOf(connection.getSource());
        }
        EObject owner = this.umlOf.get(block);
        if (owner == null || this.umlOf.get(connection.getSource()) == null || this.umlOf.get(connection.getTarget()) == null) {
            this.skipped.merge("Connection", 1, Integer::sum);
            return;
        }
        EObject connector = this.create(CONNECTOR, this.xmiId(connection));
        this.named(connector, connection);
        int n = 0;
        for (Port end : List.of(connection.getSource(), connection.getTarget())) {
            EObject connectorEnd = this.create(CONNECTOR_END, this.xmiId(connection) + "_end" + (++n));
            connectorEnd.eSet(CONNECTOR_END.getEStructuralFeature("role"), this.umlOf.get(end));
            if (end.getPartOwner() != null) {
                connectorEnd.eSet(CONNECTOR_END.getEStructuralFeature("partWithPort"), this.umlOf.get(end.getPartOwner()));
            }
            this.list(connector, "end").add(connectorEnd);
        }
        this.list(owner, "ownedConnector").add(connector);
        this.extra(connection, "kind", connection.getKind() == null ? null : connection.getKind().getLiteral());
        this.extra(connection, "interfaceType", connection.getInterfaceType() == null ? null : this.xmiId(connection.getInterfaceType()));
        if (crossBlock) {
            this.extra(connection, "crossBlock", "true");
        }
        for (ItemFlow flow : this.itemFlows.getOrDefault(connection, List.of())) {
            this.extra(connection, "itemFlow", String.join("|", this.xmiId(flow), nullToEmpty(flow.getName()), nullToEmpty(flow.getItemName()),
                    nullToEmpty(flow.getItemType()), flow.getLayer() == null ? "" : flow.getLayer().getLiteral()));
            this.exported.merge("ItemFlow", 1, Integer::sum);
        }
        this.common(connection, connector);
        this.exported.merge("Connection", 1, Integer::sum);
    }

    private void requirementRelations(Requirement requirement) {
        for (ArchElement by : requirement.getSatisfiedBy()) {
            this.abstraction(this.xmiId(requirement) + "_satisfy_" + this.xmiId(by), by, requirement, "Satisfy");
        }
        for (ArchElement by : requirement.getVerifiedBy()) {
            this.abstraction(this.xmiId(requirement) + "_verify_" + this.xmiId(by), by, requirement, "Verify");
        }
    }

    private void allocate(Allocate allocate) {
        EObject abstraction = this.abstraction(this.xmiId(allocate), allocate.getFrom(), allocate.getTo(), "Allocate");
        if (abstraction != null) {
            this.named(abstraction, allocate);
            this.extra(allocate, "rationale", allocate.getRationale());
            this.common(allocate, abstraction);
        }
    }

    private EObject abstraction(String id, ArchElement client, ArchElement supplier, String stereotype) {
        if (this.umlOf.get(client) == null || this.umlOf.get(supplier) == null) {
            this.skipped.merge(stereotype, 1, Integer::sum);
            return null;
        }
        EObject abstraction = this.create(ABSTRACTION, id);
        this.list(abstraction, "client").add(this.umlOf.get(client));
        this.list(abstraction, "supplier").add(this.umlOf.get(supplier));
        this.list(this.model, "packagedElement").add(abstraction);
        this.apply(stereotype, "base_Abstraction", abstraction);
        this.exported.merge(stereotype, 1, Integer::sum);
        return abstraction;
    }

    // ---- shared

    /** Name, description as an owned comment, and layer / stereotypes / original id into the extension. */
    private void common(ArchElement element, EObject uml) {
        if (element.getDescription() != null && !element.getDescription().isBlank()) {
            EObject comment = this.create(COMMENT, this.ids.get(uml) + "_comment");
            comment.eSet(COMMENT.getEStructuralFeature("body"), element.getDescription());
            this.list(comment, "annotatedElement").add(uml);
            this.list(uml, "ownedComment").add(comment);
        }
        if (!this.ids.get(uml).equals(ElementIds.of(element))) {
            this.extra(element, "id", ElementIds.of(element));
        }
        this.extra(element, "layer", element.getLayer() == null ? null : element.getLayer().getLiteral());
        element.getStereotypes().forEach(s -> this.extra(element, "stereotype", s));
    }

    private void named(EObject uml, ArchElement element) {
        if (element.getName() != null) {
            uml.eSet(uml.eClass().getEStructuralFeature("name"), element.getName());
        }
    }

    private EObject apply(String stereotype, String base, EObject target) {
        EClass type = (EClass) SYSML.getEClassifier(stereotype);
        EObject application = EcoreUtil.create(type);
        application.eSet(type.getEStructuralFeature(base), target);
        this.id(application, this.ids.get(target) + "_" + stereotype);
        this.applications.add(application);
        return application;
    }

    private void extra(ArchElement element, String name, String value) {
        if (value != null && !value.isEmpty()) {
            this.extensions.computeIfAbsent(this.xmiId(element), k -> new LinkedHashMap<>()).computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }
    }

    private EObject create(EClass type, String id) {
        EObject o = EcoreUtil.create(type);
        this.id(o, id);
        return o;
    }

    private void id(EObject o, String id) {
        this.ids.put(o, id);
    }

    /** The element id as an xmi:id (an XML NCName); sanitized ids get a suffix when two collide. */
    private final Map<ArchElement, String> xmiIds = new HashMap<>();

    private String xmiId(ArchElement element) {
        return this.xmiIds.computeIfAbsent(element, e -> {
            String raw = ElementIds.of(e);
            String id = NC_NAME.matcher(raw).matches() ? raw : "_" + raw.replaceAll("[^A-Za-z0-9_.-]", "_");
            int seen = this.usedIds.merge(id, 1, Integer::sum);
            return seen == 1 ? id : id + "_" + seen;
        });
    }

    @SuppressWarnings("unchecked")
    private List<EObject> list(EObject owner, String feature) {
        return (List<EObject>) owner.eGet(owner.eClass().getEStructuralFeature(feature));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // ---- writing

    private String serialize() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance(); // the JDK's own JAXP: the output must not depend on what else the classpath brings (docx4j)
            factory.setNamespaceAware(true);
            Map<Object, Object> options = new HashMap<>();
            options.put(XMLResource.OPTION_ENCODING, "UTF-8");
            options.put(XMLResource.OPTION_USE_ENCODED_ATTRIBUTE_STYLE, Boolean.FALSE);
            options.put(org.eclipse.emf.ecore.xmi.XMIResource.OPTION_USE_XMI_TYPE, Boolean.TRUE); // xmi:type, not xsi:type
            // EMF writes the XMI (its stream path honours the XMI 2.5.1 namespace); the XMI-level extras are added on its DOM
            var bytes = new java.io.ByteArrayOutputStream();
            this.resource.save(bytes, options);
            Document document = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes.toByteArray()));
            Element root = document.getDocumentElement();
            root.removeAttributeNS(XMI_NS, "version");
            Element documentation = document.createElementNS(XMI_NS, "xmi:Documentation");
            documentation.setAttribute("exporter", EXTENDER);
            documentation.setAttribute("exporterVersion", "P3");
            Element longDescription = document.createElementNS(XMI_NS, "xmi:longDescription");
            longDescription.setTextContent(NOTICE + this.counts());
            documentation.appendChild(longDescription);
            root.insertBefore(documentation, root.getFirstChild());
            root.insertBefore(document.createComment(" " + NOTICE + " "), documentation);
            this.addExtensions(root);
            dropWhitespace(root); // EMF's own indentation; the transformer indents again
            document.setXmlStandalone(true); // no standalone="no" in the declaration
            var transformer = TransformerFactory.newDefaultInstance().newTransformer(); // JDK's own, as above
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter out = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(out));
            // LF on every platform (the JDK transformer writes the system line separator), declaration on its own line
            return out.toString().replace("\r\n", "\n").replace("?><", "?>\n<");
        } catch (java.io.IOException | org.xml.sax.SAXException | ParserConfigurationException | TransformerException e) {
            throw new IllegalStateException("XMI export failed: " + e.getMessage(), e);
        }
    }

    /** Removes whitespace-only text nodes (indentation), keeping text content and comments. */
    private static void dropWhitespace(Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            Node next = child.getNextSibling();
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank()) {
                element.removeChild(child);
            } else if (child instanceof Element e) {
                dropWhitespace(e);
            }
            child = next;
        }
    }

    private String counts() {
        StringBuilder text = new StringBuilder("\n导出：");
        this.exported.forEach((k, v) -> text.append(k).append(' ').append(v).append("；"));
        if (!this.skipped.isEmpty()) {
            text.append("\n未导出（XMI 导出不含参数图与行为元素，或缺少一端）：");
            this.skipped.forEach((k, v) -> text.append(k).append(' ').append(v).append("；"));
        }
        return text.toString();
    }

    /** {@code <xmi:Extension extender="SysArch Studio"><layer value="…"/>…</xmi:Extension>} as the first child of each element. */
    private void addExtensions(Element root) {
        Map<String, Element> byId = new HashMap<>();
        collect(root, byId);
        this.extensions.forEach((id, values) -> {
            Element element = byId.get(id);
            if (element == null) {
                return;
            }
            Element extension = root.getOwnerDocument().createElementNS(XMI_NS, "xmi:Extension");
            extension.setAttribute("extender", EXTENDER);
            values.forEach((name, list) -> list.forEach(value -> {
                Element entry = root.getOwnerDocument().createElementNS(XMLConstants.NULL_NS_URI, name);
                entry.setAttribute("value", value);
                extension.appendChild(entry);
            }));
            element.insertBefore(extension, element.getFirstChild());
        });
    }

    private static void collect(Element element, Map<String, Element> byId) {
        String id = element.getAttributeNS(XMI_NS, "id");
        if (!id.isEmpty()) {
            byId.put(id, element);
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element e) {
                collect(e, byId);
            }
        }
    }
}

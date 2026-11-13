package com.example.sysarch.sysmlimport;

import com.example.sysarch.export.XmiExporter;
import com.example.sysarch.model.Allocate;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.ConnectionKind;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.emf.ecore.EObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * P3 T8 (spec §5.2): reads XMI back into the model. <b>Only two sources are accepted</b>: what {@link XmiExporter}
 * writes, and a minimal Cameo export (UML classes with «Block», their properties, optionally ports, connectors,
 * requirements and the three relations, possibly in packages). Anything else, any other element type or SysML
 * stereotype, fails the whole import and every such element is listed with its type, name and id ("其余元素类型整批失败
 * 并列出"). Other tools' {@code xmi:Extension}s and applications of other profiles are ignored and reported.
 *
 * <p>The extension {@code extender="SysArch Studio"} restores what UML cannot carry (layer, stereotypes, port direction,
 * a port's part, …), so export → import → export gives the same text (the golden test). Without it (Cameo) an element
 * gets the default layer, listed in {@code defaultedLayer}. Element ids: the extension's original id, else the xmi:id.
 *
 * <p>XML is parsed without DTDs or external entities.
 */
public final class XmiImporter {

    private static final SysarchFactory F = SysarchFactory.eINSTANCE;

    /** What came out: problems (non-empty = refused, nothing is created) or the mapped elements, and what was ignored. */
    public record Result(List<String> problems, SysmlMapper.Mapped mapped, List<String> ignored) {
    }

    private final Document document;

    private final Layer defaultLayer;

    private final Map<String, Element> byId = new HashMap<>();

    /** xmi:id -> SysML stereotype local name applied to it (Block, Requirement, Satisfy …), and the application. */
    private final Map<String, String> stereotypeOf = new HashMap<>();

    /** The file says it was written by SysArch Studio ({@code xmi:Documentation exporter}). */
    private boolean ownExport;

    private final Map<String, Element> applicationOf = new HashMap<>();

    private final Map<String, EObject> imported = new HashMap<>();

    private final List<String> problems = new ArrayList<>();

    private final List<String> ignored = new ArrayList<>();

    private final List<String> defaulted = new ArrayList<>();

    private final List<EObject> roots = new ArrayList<>();

    private final List<EObject> relationRoots = new ArrayList<>();

    private final List<EObject> connectionRoots = new ArrayList<>();

    private final Map<String, Integer> counts = new TreeMap<>();

    /** Ports to move onto a part once all parts exist: port -> part xmi:id. */
    private final Map<Port, String> portsOfParts = new LinkedHashMap<>();

    private final List<Runnable> afterwards = new ArrayList<>();

    private final List<Element> interactions = new ArrayList<>();

    private final List<EObject> interactionRoots = new ArrayList<>();

    private String modelName;

    private XmiImporter(Document document, Layer defaultLayer) {
        this.document = document;
        this.defaultLayer = defaultLayer;
    }

    public static Result read(String text, Layer defaultLayer) {
        Document document;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return new Result(List.of("不是可读的 XML：" + e.getMessage()), null, List.of());
        }
        return new XmiImporter(document, defaultLayer).run();
    }

    private Result run() {
        Element root = this.document.getDocumentElement();
        if (!isXmi(root.getNamespaceURI()) || !"XMI".equals(root.getLocalName())) {
            return new Result(List.of("不是 XMI 文件：根元素应为 xmi:XMI（XMI 2.5.1）"), null, List.of());
        }
        this.index(root);
        Element model = null;
        for (Element child : children(root)) {
            String ns = nullToEmpty(child.getNamespaceURI());
            if (isUml(ns) && "Model".equals(child.getLocalName())) {
                if (model != null) {
                    this.problems.add("文件里有多个 uml:Model，只支持一个");
                }
                model = child;
            } else if (isSysml(ns)) {
                this.stereotypeApplication(child);
            } else if (isXmi(ns) && List.of("Documentation", "Extension").contains(child.getLocalName())) {
                // our head notice, or another tool's extension
                if ("Documentation".equals(child.getLocalName()) && XmiExporter.EXTENDER.equals(child.getAttribute("exporter"))) {
                    this.ownExport = true;
                }
            } else {
                this.ignored.add("已忽略：" + child.getTagName() + "（其他配置文件的构造型应用或工具数据）");
            }
        }
        if (model == null) {
            return new Result(List.of("文件里没有 uml:Model"), null, this.ignored);
        }
        this.modelName = model.getAttribute("name").isEmpty() ? null : model.getAttribute("name");
        List<Element> classes = new ArrayList<>();
        List<Element> abstractions = new ArrayList<>();
        this.packaged(model, classes, abstractions);
        for (Element cls : classes) {
            this.declare(cls);
        }
        for (Element cls : classes) {
            this.contents(cls);
        }
        this.portsOfParts.forEach((port, partId) -> {
            if (this.imported.get(partId) instanceof Part part) {
                ((PartDefinition) port.eContainer()).getOwnedPorts().remove(port);
                part.getPorts().add(port);
            } else {
                this.problems.add("端口「" + port.getName() + "」的扩展指向不存在的部件 " + partId);
            }
        });
        this.afterwards.forEach(Runnable::run);
        for (Element abstraction : abstractions) {
            this.relation(abstraction);
        }
        for (Element interaction : this.interactions) {
            this.interaction(interaction);
        }
        if (!this.problems.isEmpty()) {
            return new Result(List.copyOf(this.problems), null, List.copyOf(this.ignored));
        }
        List<EObject> all = new ArrayList<>(this.roots);
        all.addAll(this.connectionRoots);
        all.addAll(this.relationRoots);
        all.addAll(this.interactionRoots);
        return new Result(List.of(), new SysmlMapper.Mapped(all, this.counts, this.defaulted, this.ignored, List.of(), this.modelName), List.copyOf(this.ignored));
    }

    // ---- structure

    private void index(Element element) {
        String id = this.xmiAttr(element, "id");
        if (!id.isEmpty()) {
            this.byId.put(id, element);
        }
        for (Element child : children(element)) {
            this.index(child);
        }
    }

    private void stereotypeApplication(Element application) {
        String name = application.getLocalName();
        String base = this.reference(application, name.equals("Satisfy") || name.equals("Verify") || name.equals("Allocate") ? "base_Abstraction" : "base_Class");
        if (base == null) {
            base = firstNonEmpty(this.reference(application, "base_Class"), this.reference(application, "base_Abstraction"), this.reference(application, "base_Property"),
                    this.reference(application, "base_Port"), this.reference(application, "base_Element"));
        }
        if (base == null) {
            this.ignored.add("已忽略：没有基元素的构造型应用 sysml:" + name);
            return;
        }
        this.stereotypeOf.merge(base, name, (a, b) -> a + "," + b);
        this.applicationOf.put(base, application);
    }

    /** Model and packages: classes and abstractions are collected, packages flattened, everything else refused. */
    private void packaged(Element owner, List<Element> classes, List<Element> abstractions) {
        for (Element child : children(owner)) {
            String local = child.getLocalName();
            if (this.isExtension(child) || local.equals("profileApplication") || local.equals("ownedComment") || local.equals("packageImport")) {
                continue;
            }
            if (!local.equals("packagedElement")) {
                this.refuse(child);
                continue;
            }
            switch (this.type(child)) {
                case "Package" -> this.packaged(child, classes, abstractions);
                case "Class" -> classes.add(child);
                case "Abstraction" -> abstractions.add(child);
                case "Interaction" -> this.interactions.add(child); // v1.4
                default -> this.refuse(child);
            }
        }
    }

    private void declare(Element cls) {
        String stereotype = this.stereotypeOf.getOrDefault(this.xmiAttr(cls, "id"), "");
        ArchElement element = switch (stereotype) {
            case "Block" -> F.createPartDefinition();
            case "InterfaceBlock" -> F.createInterfaceDef();
            case "Requirement" -> F.createRequirement();
            default -> null;
        };
        if (element == null) {
            this.problems.add("不支持的类「" + cls.getAttribute("name") + "」（" + this.xmiAttr(cls, "id") + "）：" + (stereotype.isEmpty()
                    ? "uml:Class 必须带 «Block»、«InterfaceBlock» 或 «Requirement»" : "构造型 «" + stereotype + "» 不在导入范围内"));
            return;
        }
        this.common(cls, element);
        if (element instanceof PartDefinition block) {
            block.setIsAbstract("true".equals(cls.getAttribute("isAbstract")));
        }
        if (element instanceof Requirement requirement) {
            Element application = this.applicationOf.get(this.xmiAttr(cls, "id"));
            requirement.setReqId(emptyToNull(firstNonEmpty(application.getAttribute("id"), this.childText(application, "id"))));
            requirement.setText(emptyToNull(firstNonEmpty(application.getAttribute("text"), this.childText(application, "text"))));
            String method = this.extension(cls, "verifyMethod");
            if (method != null) {
                requirement.setVerifyMethod(VerifyMethod.get(method));
            }
            String priority = this.extension(cls, "priority");
            if (priority != null) {
                requirement.setPriority(Integer.parseInt(priority));
            }
        }
        if (element instanceof InterfaceDef interfaceDef) {
            interfaceDef.setProtocol(this.extension(cls, "protocol"));
            interfaceDef.setDataType(this.extension(cls, "dataType"));
        }
        this.roots.add(element);
        this.count(element);
    }

    private void contents(Element cls) {
        EObject owner = this.imported.get(this.xmiAttr(cls, "id"));
        if (owner == null) {
            return;
        }
        for (Element child : children(cls)) {
            String local = child.getLocalName();
            if (this.isExtension(child) || local.equals("ownedComment")) {
                continue;
            }
            switch (local) {
                case "generalization" -> this.generalization(owner, child);
                case "ownedAttribute" -> {
                    if (owner instanceof PartDefinition block) {
                        this.attribute(block, child);
                    } else {
                        this.refuse(child);
                    }
                }
                case "ownedConnector" -> {
                    if (owner instanceof PartDefinition) {
                        this.connector(child);
                    } else {
                        this.refuse(child);
                    }
                }
                default -> this.refuse(child);
            }
        }
    }

    private void generalization(EObject owner, Element generalization) {
        String general = this.reference(generalization, "general");
        this.afterwards.add(() -> {
            EObject superType = general == null ? null : this.imported.get(general);
            if (owner instanceof PartDefinition block && superType instanceof PartDefinition s) {
                block.getSuperTypes().add(s);
            } else if (owner instanceof InterfaceDef i && superType instanceof InterfaceDef s) {
                i.getSuperTypes().add(s);
            } else {
                this.problems.add("「" + ((ArchElement) owner).getName() + "」的泛化指向不能导入的元素 " + general);
            }
        });
    }

    private void attribute(PartDefinition block, Element attribute) {
        String type = this.type(attribute);
        String typeRef = this.reference(attribute, "type");
        if (type.equals("Port")) {
            Port port = F.createPort();
            this.common(attribute, port);
            port.setIsConjugated("true".equals(attribute.getAttribute("isConjugated")));
            String direction = this.extension(attribute, "direction");
            port.setDirection(direction == null ? PortDirection.INOUT : PortDirection.get(direction));
            block.getOwnedPorts().add(port);
            if (typeRef != null) {
                this.afterwards.add(() -> {
                    if (this.imported.get(typeRef) instanceof InterfaceDef interfaceDef) {
                        port.setInterfaceType(interfaceDef);
                    } else {
                        this.problems.add("端口「" + port.getName() + "」（" + this.xmiAttr(attribute, "id") + "）的类型不是 «InterfaceBlock»：" + typeRef);
                    }
                });
            }
            String partOwner = this.extension(attribute, "partOwner");
            if (partOwner != null) {
                this.portsOfParts.put(port, partOwner);
            }
            this.count(port);
            return;
        }
        if (!type.equals("Property")) {
            this.refuse(attribute);
            return;
        }
        Element typeElement = typeRef == null ? null : this.byId.get(typeRef);
        boolean blockTyped = typeElement != null && "Block".equals(this.stereotypeOf.get(typeRef));
        // A part without a type (allowed while modelling, marked red) is exported as a composite property without a type;
        // our own export must import again (found by the end-to-end test, 2026-11-13). Other tools' untyped properties
        // stay refused: there they are value or reference properties as often as parts.
        boolean ownUntypedPart = this.ownExport && typeRef == null && "composite".equals(attribute.getAttribute("aggregation"));
        if (!"composite".equals(attribute.getAttribute("aggregation")) || !(blockTyped || ownUntypedPart)) {
            this.problems.add("不支持的属性「" + attribute.getAttribute("name") + "」（" + this.xmiAttr(attribute, "id") + "）：只导入以 «Block» 为类型的组合属性（部件）"
                    + (typeRef == null ? "，它没有类型" : blockTyped ? "，它不是组合" : "，它的类型不是 «Block»（值属性、引用属性不导入）"));
            return;
        }
        Part part = F.createPart();
        this.common(attribute, part);
        part.setMultiplicity(this.multiplicity(attribute));
        block.getOwnedParts().add(part);
        if (typeRef != null) {
            this.afterwards.add(() -> part.setTypedBy((PartDefinition) this.imported.get(typeRef)));
        }
        this.count(part);
    }

    private String multiplicity(Element attribute) {
        String explicit = this.extension(attribute, "multiplicity");
        if (explicit != null) {
            return explicit;
        }
        String lower = this.literal(attribute, "lowerValue");
        String upper = this.literal(attribute, "upperValue");
        if (lower == null && upper == null) {
            return null;
        }
        lower = lower == null ? "1" : lower;
        upper = upper == null ? "1" : upper;
        return lower.equals(upper) ? lower : lower + ".." + upper;
    }

    private String literal(Element attribute, String name) {
        List<Element> values = children(attribute).stream().filter(e -> name.equals(e.getLocalName())).toList();
        if (values.isEmpty()) {
            return null;
        }
        String value = values.get(0).getAttribute("value");
        return value.isEmpty() ? ("upperValue".equals(name) ? null : "0") : value;
    }

    private void connector(Element connector) {
        List<Element> ends = children(connector).stream().filter(e -> "end".equals(e.getLocalName())).toList();
        if (ends.size() != 2) {
            this.problems.add("连接器「" + connector.getAttribute("name") + "」（" + this.xmiAttr(connector, "id") + "）应有两个端点，实际 " + ends.size() + " 个");
            return;
        }
        Connection connection = F.createConnection();
        this.common(connector, connection);
        String kind = this.extension(connector, "kind");
        if (kind != null) {
            connection.setKind(ConnectionKind.get(kind));
        }
        String interfaceType = this.extension(connector, "interfaceType");
        List<String> itemFlows = this.extensions(connector, "itemFlow");
        String source = this.reference(ends.get(0), "role");
        String target = this.reference(ends.get(1), "role");
        this.afterwards.add(() -> {
            if (this.imported.get(source) instanceof Port s && this.imported.get(target) instanceof Port t) {
                connection.setSource(s);
                connection.setTarget(t);
            } else {
                this.problems.add("连接器「" + connection.getName() + "」的端点不是导入的端口：" + source + " → " + target);
            }
            if (interfaceType != null && this.imported.get(interfaceType) instanceof InterfaceDef i) {
                connection.setInterfaceType(i);
            }
        });
        this.connectionRoots.add(connection);
        this.count(connection);
        for (String flow : itemFlows) {
            String[] f = flow.split("\\|", -1);
            ItemFlow itemFlow = F.createItemFlow();
            itemFlow.setId(f[0]);
            itemFlow.setName(emptyToNull(f.length > 1 ? f[1] : null));
            itemFlow.setItemName(emptyToNull(f.length > 2 ? f[2] : null));
            itemFlow.setItemType(emptyToNull(f.length > 3 ? f[3] : null));
            if (f.length > 4 && !f[4].isEmpty()) {
                itemFlow.setLayer(Layer.get(f[4]));
            }
            itemFlow.setConnection(connection);
            this.connectionRoots.add(itemFlow);
            this.count(itemFlow);
        }
    }

    private void relation(Element abstraction) {
        String id = this.xmiAttr(abstraction, "id");
        String stereotype = this.stereotypeOf.getOrDefault(id, "");
        List<String> clients = this.references(abstraction, "client");
        List<String> suppliers = this.references(abstraction, "supplier");
        if (!List.of("Satisfy", "Verify", "Allocate").contains(stereotype)) {
            this.problems.add("不支持的关系「" + abstraction.getAttribute("name") + "」（" + id + "）：uml:Abstraction 必须带 «Satisfy»、«Verify» 或 «Allocate»"
                    + (stereotype.isEmpty() ? "" : "，它带的是 «" + stereotype + "»"));
            return;
        }
        if (clients.size() != 1 || suppliers.size() != 1 || !(this.imported.get(clients.get(0)) instanceof ArchElement client)
                || !(this.imported.get(suppliers.get(0)) instanceof ArchElement supplier)) {
            this.problems.add("关系 «" + stereotype + "»（" + id + "）的两端必须各是一个导入的元素");
            return;
        }
        switch (stereotype) {
            case "Satisfy", "Verify" -> {
                if (!(supplier instanceof Requirement requirement)) {
                    this.problems.add("«" + stereotype + "»（" + id + "）的目标不是需求");
                    return;
                }
                (stereotype.equals("Satisfy") ? requirement.getSatisfiedBy() : requirement.getVerifiedBy()).add(client);
            }
            default -> {
                Allocate allocate = F.createAllocate();
                this.common(abstraction, allocate);
                allocate.setFrom(client);
                allocate.setTo(supplier);
                allocate.setRationale(this.extension(abstraction, "rationale"));
                this.relationRoots.add(allocate);
                this.count(allocate);
            }
        }
    }

    /**
     * v1.4: a {@code uml:Interaction}: lifelines (represents = the type of the interaction's property they represent, or the
     * element the SysArch extension names), messages in the order of their send occurrences (UML's time order; ours
     * writes them in order anyway), sender / receiver = the lifelines the send / receive occurrence covers, messageSort →
     * kind (synchCall SYNC, asynchCall / asynchSignal ASYNC, reply REPLY, others SYNC).
     */
    private void interaction(Element element) {
        com.example.sysarch.model.Interaction interaction = F.createInteraction();
        this.common(element, interaction);
        Map<String, String> propertyType = new HashMap<>();
        Map<String, String> covered = new HashMap<>();
        List<String> fragmentOrder = new ArrayList<>();
        List<Element> lifelines = new ArrayList<>();
        List<Element> messages = new ArrayList<>();
        List<Runnable> later = new ArrayList<>();
        for (Element child : children(element)) {
            String local = child.getLocalName();
            if (this.isExtension(child) || local.equals("ownedComment")) {
                continue;
            }
            switch (local) {
                case "ownedAttribute" -> propertyType.put(this.xmiAttr(child, "id"), this.reference(child, "type"));
                case "lifeline" -> lifelines.add(child);
                case "fragment" -> {
                    String id = this.xmiAttr(child, "id");
                    fragmentOrder.add(id);
                    covered.put(id, this.reference(child, "covered"));
                }
                case "message" -> messages.add(child);
                default -> this.refuse(child);
            }
        }
        for (Element child : lifelines) {
            com.example.sysarch.model.Lifeline lifeline = F.createLifeline();
            this.common(child, lifeline);
            interaction.getLifelines().add(lifeline);
            String property = this.reference(child, "represents");
            String target = property != null ? propertyType.get(property) : this.extension(child, "represents");
            if (target != null) {
                later.add(() -> {
                    if (this.imported.get(target) instanceof com.example.sysarch.model.ArchElement represented) {
                        lifeline.setRepresents(represented);
                    } else {
                        this.ignored.add("生命线「" + lifeline.getName() + "」代表的元素 " + target + " 不在文件中，未设置");
                    }
                });
            }
            this.count(lifeline);
        }
        messages.sort(java.util.Comparator.comparingInt(m -> {
            int at = fragmentOrder.indexOf(this.reference(m, "sendEvent"));
            return at < 0 ? Integer.MAX_VALUE : at;
        }));
        for (Element child : messages) {
            com.example.sysarch.model.Message message = F.createMessage();
            this.common(child, message);
            message.setKind(switch (child.getAttribute("messageSort")) {
                case "asynchCall", "asynchSignal" -> com.example.sysarch.model.MessageKind.ASYNC;
                case "reply" -> com.example.sysarch.model.MessageKind.REPLY;
                default -> com.example.sysarch.model.MessageKind.SYNC;
            });
            String sender = covered.get(this.reference(child, "sendEvent"));
            String receiver = covered.get(this.reference(child, "receiveEvent"));
            if (sender != null && this.imported.get(sender) instanceof com.example.sysarch.model.Lifeline s) {
                message.setSender(s);
            }
            if (receiver != null && this.imported.get(receiver) instanceof com.example.sysarch.model.Lifeline r) {
                message.setReceiver(r);
            }
            interaction.getMessages().add(message);
            this.count(message);
        }
        later.forEach(Runnable::run); // its own list: the importer's afterwards have already run
        this.interactionRoots.add(interaction);
        this.count(interaction);
    }

    /** Id, name, description and the SysArch extension's layer and stereotypes. */
    private void common(Element source, ArchElement element) {
        String xmiId = this.xmiAttr(source, "id");
        String id = this.extension(source, "id");
        element.setId(id != null ? id : xmiId.isEmpty() ? null : xmiId);
        element.setName(emptyToNull(source.getAttribute("name")));
        for (Element comment : children(source)) {
            if ("ownedComment".equals(comment.getLocalName())) {
                String body = firstNonEmpty(comment.getAttribute("body"), this.childText(comment, "body"));
                if (body != null && element.getDescription() == null) {
                    element.setDescription(body);
                }
            }
        }
        String layer = this.extension(source, "layer");
        if (layer != null && Layer.get(layer) != null) {
            element.setLayer(Layer.get(layer));
        } else {
            element.setLayer(this.defaultLayer);
            this.defaulted.add(element.eClass().getName() + "「" + element.getName() + "」");
        }
        element.getStereotypes().addAll(this.extensions(source, "stereotype"));
        if (!xmiId.isEmpty()) {
            this.imported.put(xmiId, element);
        }
    }

    // ---- XMI helpers

    private void refuse(Element element) {
        String type = element.getAttributeNS(element.getOwnerDocument().getDocumentElement().getNamespaceURI(), "type");
        this.problems.add("不支持的元素 " + (type.isEmpty() ? element.getTagName() : type) + "「" + element.getAttribute("name") + "」（"
                + this.xmiAttr(element, "id") + "，在 " + element.getTagName() + " 中）：XMI 导入只接受块、部件、端口、连接器、接口块、需求及其满足/验证/分配关系、交互");
    }

    /** {@code xmi:type} local name ("Class" for uml:Class). */
    private String type(Element element) {
        String type = this.xmiAttr(element, "type");
        return type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
    }

    private String xmiAttr(Element element, String name) {
        return element.getAttributeNS(this.document.getDocumentElement().getNamespaceURI(), name);
    }

    /** A single reference: attribute {@code name="id"}, or a child {@code <name xmi:idref="id"/>}. */
    private String reference(Element element, String name) {
        List<String> all = this.references(element, name);
        return all.isEmpty() ? null : all.get(0);
    }

    private List<String> references(Element element, String name) {
        List<String> out = new ArrayList<>();
        if (!element.getAttribute(name).isBlank()) {
            out.addAll(List.of(element.getAttribute(name).trim().split("\\s+")));
        }
        for (Element child : children(element)) {
            if (name.equals(child.getLocalName())) {
                String idref = this.xmiAttr(child, "idref");
                out.add(!idref.isEmpty() ? idref : child.getAttribute("href"));
            }
        }
        return out;
    }

    private String childText(Element element, String name) {
        for (Element child : children(element)) {
            if (name.equals(child.getLocalName())) {
                return child.getTextContent();
            }
        }
        return null;
    }

    private boolean isExtension(Element element) {
        return "Extension".equals(element.getLocalName()) && isXmi(element.getNamespaceURI());
    }

    private String extension(Element element, String name) {
        List<String> values = this.extensions(element, name);
        return values.isEmpty() ? null : values.get(0);
    }

    /** Values of {@code <name value="…"/>} in this element's own {@code xmi:Extension extender="SysArch Studio"}. */
    private List<String> extensions(Element element, String name) {
        List<String> out = new ArrayList<>();
        for (Element child : children(element)) {
            if (this.isExtension(child) && XmiExporter.EXTENDER.equals(child.getAttribute("extender"))) {
                for (Element entry : children(child)) {
                    if (name.equals(entry.getLocalName())) {
                        out.add(entry.getAttribute("value"));
                    }
                }
            }
        }
        return out;
    }

    /** Counted under the same kinds as the SysML v2 import, so the report reads the same. */
    private static final Map<String, String> KIND = Map.ofEntries(Map.entry("PartDefinition", "part def"), Map.entry("Part", "part"),
            Map.entry("InterfaceDef", "port def"), Map.entry("Port", "port"), Map.entry("Connection", "connection"), Map.entry("Requirement", "requirement"),
            Map.entry("Allocate", "allocation"), Map.entry("ItemFlow", "item flow"), Map.entry("Interaction", "occurrence def"), Map.entry("Lifeline", "lifeline"),
            Map.entry("Message", "message"));

    private void count(ArchElement element) {
        this.counts.merge(KIND.getOrDefault(element.eClass().getName(), element.eClass().getName()), 1, Integer::sum);
    }

    private static List<Element> children(Element element) {
        List<Element> out = new ArrayList<>();
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element e) {
                out.add(e);
            }
        }
        return out;
    }

    private static boolean isXmi(String ns) {
        return ns != null && (ns.startsWith("http://www.omg.org/spec/XMI/") || ns.startsWith("http://schema.omg.org/spec/XMI/"));
    }

    private static boolean isUml(String ns) {
        return ns.startsWith("http://www.omg.org/spec/UML/") || ns.startsWith("http://schema.omg.org/spec/UML/");
    }

    private static boolean isSysml(String ns) {
        return ns.startsWith("http://www.omg.org/spec/SysML/"); // not MagicDraw's own customization profiles
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

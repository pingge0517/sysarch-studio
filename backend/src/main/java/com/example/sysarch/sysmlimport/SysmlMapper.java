package com.example.sysarch.sysmlimport;

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
import com.example.sysarch.sysmlimport.SysmlParser.Decl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.ecore.EObject;

/**
 * The parsed tree → sysarch elements (P2 §5.2 step 3). Plain SysML gives the structure; SysArch's own export
 * conventions are read back too (user's decision "无损往返"):
 * <ul>
 * <li>{@code @SysArchInfo { layer = "…"; stereotypes = "a, b"; }} → layer and stereotypes;</li>
 * <li>doc lines {@code protocol:}, {@code data type:} (port def), {@code reqId:}, {@code verify method: M; priority: N},
 * {@code description:} (requirement; the other lines are its text), {@code kind: K; interface: I}, {@code item flow …},
 * {@code description:} (connection); any other doc → description (spec);</li>
 * <li>package-level doc lines {@code 满足 … <RQ> ← … <ID>}, {@code 验证 …}, {@code 分配 AL：… <A> → … <B>（理由） {name: …; layer: …}}.</li>
 * </ul>
 * An element without its own layer takes the layer of what contains it (part ← block, port ← part or block, connection
 * ← block), else {@code defaultLayer} (user's decision, listed). An allocation's name defaults to "from → to" and its
 * layer to that of "to", as SysArch creates them. What cannot be placed in the model is listed as UNMAPPED.
 */
public final class SysmlMapper {

    public static final String METADATA = "SysArchInfo";

    /** The mapped elements (top level, in text order) and what the report needs. */
    public record Mapped(List<EObject> roots, Map<String, Integer> counts, List<String> defaultedLayer, List<String> unmapped, List<String> derivedPorts,
            String packageName) {

        public int total() {
            return this.counts.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private static final SysarchFactory FACTORY = SysarchFactory.eINSTANCE;

    private static final Pattern SATISFY = Pattern.compile("^(满足|验证) .*<([^<>]+)> ← .*<([^<>]+)>$");

    private static final Pattern ALLOCATE = Pattern.compile("^分配 (\\S+)：.*<([^<>]+)> → .*?<([^<>]+)>(?:（(.*?)）)?(?: \\{(.*)\\})?$");

    private static final Pattern ITEM_FLOW = Pattern.compile("^item flow (\\S+) (.*?): (.*?) : (.*)$");

    private final Layer defaultLayer;

    private final List<EObject> roots = new ArrayList<>();

    private final Map<String, Integer> counts = new TreeMap<>();

    private final List<String> defaulted = new ArrayList<>();

    private final List<String> unmapped = new ArrayList<>();

    private final List<String> derived = new ArrayList<>();

    private final Map<String, ArchElement> byId = new HashMap<>();

    private final Map<String, List<PartDefinition>> blocksByName = new HashMap<>();

    private final Map<String, List<InterfaceDef>> interfacesByName = new HashMap<>();

    private final Map<Decl, ArchElement> elements = new LinkedHashMap<>();

    private int generated;

    private SysmlMapper(Layer defaultLayer) {
        this.defaultLayer = defaultLayer;
    }

    public static Mapped map(Decl root, Layer defaultLayer) {
        SysmlMapper mapper = new SysmlMapper(defaultLayer);
        String packageName = mapper.run(root);
        return new Mapped(List.copyOf(mapper.roots), Map.copyOf(mapper.counts), List.copyOf(mapper.defaulted), List.copyOf(mapper.unmapped),
                List.copyOf(mapper.derived), packageName);
    }

    private String run(Decl root) {
        // 1. definitions first, so usages and relations can refer to them in any order
        for (Decl decl : root.children) {
            switch (decl.kind) {
                case "part def" -> this.block(decl);
                case "port def" -> this.interfaceDef(decl);
                default -> { }
            }
        }
        for (Decl decl : root.children) {
            switch (decl.kind) {
                case "part def", "port def" -> { }
                case "requirement" -> this.requirement(decl);
                case "occurrence def" -> this.interaction(decl); // v1.4
                case "part", "port", "connection" -> this.unmapped.add(decl + "：顶层的" + decl.kind + "没有所属的块，未导入");
                default -> this.unmapped.add(decl + "：未导入");
            }
        }
        // 2. what refers to definitions: supertypes, part types, port interfaces, connections
        this.elements.forEach(this::resolve);
        for (Decl decl : root.children) {
            if (decl.kind.equals("part def")) {
                this.connections(decl, (PartDefinition) this.elements.get(decl));
            }
        }
        // 3. relations SysArch exports as text
        for (String doc : root.docs) {
            for (String line : doc.split("\n")) {
                this.relation(line.strip());
            }
        }
        return root.name;
    }

    // ------------------------------------------------------------------ elements

    private PartDefinition block(Decl decl) {
        PartDefinition block = FACTORY.createPartDefinition();
        this.init(block, decl, "part def", null);
        block.setIsAbstract(decl.isAbstract);
        block.setDescription(text(decl.docs));
        this.blocksByName.computeIfAbsent(block.getName(), n -> new ArrayList<>()).add(block);
        this.roots.add(block);
        for (Decl child : decl.children) {
            switch (child.kind) {
                case "part" -> block.getOwnedParts().add(this.part(child, block.getLayer()));
                case "port" -> block.getOwnedPorts().add(this.port(child, block.getLayer()));
                case "connection" -> { } // after all ports exist
                default -> this.unmapped.add(child + "：块内的" + child.kind + "未导入");
            }
        }
        return block;
    }

    private Part part(Decl decl, Layer inherited) {
        Part part = FACTORY.createPart();
        this.init(part, decl, "part", inherited);
        part.setMultiplicity(decl.multiplicity);
        part.setDescription(text(decl.docs));
        for (Decl child : decl.children) {
            if (child.kind.equals("port")) {
                part.getPorts().add(this.port(child, part.getLayer()));
            } else {
                this.unmapped.add(child + "：部件内的" + child.kind + "未导入");
            }
        }
        return part;
    }

    private Port port(Decl decl, Layer inherited) {
        Port port = FACTORY.createPort();
        this.init(port, decl, "port", inherited);
        port.setDirection(decl.direction == null ? PortDirection.INOUT : PortDirection.getByName(decl.direction.toUpperCase(java.util.Locale.ROOT)));
        port.setIsConjugated(decl.conjugated);
        port.setDescription(text(decl.docs));
        decl.children.forEach(child -> this.unmapped.add(child + "：端口内的" + child.kind + "未导入"));
        return port;
    }

    private void interfaceDef(Decl decl) {
        InterfaceDef def = FACTORY.createInterfaceDef();
        this.init(def, decl, "port def", null);
        List<String> rest = new ArrayList<>();
        for (String line : lines(decl.docs)) {
            if (line.startsWith("protocol: ")) {
                def.setProtocol(line.substring("protocol: ".length()));
            } else if (line.startsWith("data type: ")) {
                def.setDataType(line.substring("data type: ".length()));
            } else {
                rest.add(line);
            }
        }
        def.setDescription(rest.isEmpty() ? null : String.join("\n", rest));
        this.interfacesByName.computeIfAbsent(def.getName(), n -> new ArrayList<>()).add(def);
        this.roots.add(def);
        decl.children.forEach(child -> this.unmapped.add(child + "：端口定义内的" + child.kind + "未导入"));
    }

    private void requirement(Decl decl) {
        Requirement requirement = FACTORY.createRequirement();
        this.init(requirement, decl, "requirement", null);
        List<String> text = new ArrayList<>();
        for (String line : lines(decl.docs)) {
            if (line.startsWith("reqId: ")) {
                requirement.setReqId(line.substring("reqId: ".length()));
            } else if (line.startsWith("verify method: ")) {
                for (String part : line.substring("verify method: ".length()).split(";")) {
                    String value = part.strip();
                    if (value.startsWith("priority:")) {
                        try {
                            requirement.setPriority(Integer.parseInt(value.substring("priority:".length()).strip()));
                        } catch (NumberFormatException e) {
                            this.unmapped.add(decl + "：优先级「" + value + "」不是整数");
                        }
                    } else if (VerifyMethod.getByName(value) != null) {
                        requirement.setVerifyMethod(VerifyMethod.getByName(value));
                    }
                }
            } else if (line.startsWith("description: ")) {
                requirement.setDescription(line.substring("description: ".length()));
            } else {
                text.add(line);
            }
        }
        requirement.setText(text.isEmpty() ? null : String.join("\n", text));
        this.roots.add(requirement);
        decl.children.forEach(child -> this.unmapped.add(child + "：需求内的" + child.kind + "未导入"));
    }

    private void connections(Decl blockDecl, PartDefinition block) {
        for (Decl decl : blockDecl.children) {
            if (!decl.kind.equals("connection")) {
                continue;
            }
            Port source = this.end(block, decl.sourceEnd);
            Port target = this.end(block, decl.targetEnd);
            if (source == null || target == null) {
                this.unmapped.add(decl + "：连接端点 " + String.join(".", decl.sourceEnd) + " → " + String.join(".", decl.targetEnd) + " 在块「"
                        + block.getName() + "」里找不到，连接未导入");
                continue;
            }
            Connection connection = FACTORY.createConnection();
            this.init(connection, decl, "connection", block.getLayer());
            connection.setSource(source);
            connection.setTarget(target);
            connection.setKind(ConnectionKind.ASSEMBLY);
            List<String> rest = new ArrayList<>();
            List<ItemFlow> flows = new ArrayList<>();
            for (String line : lines(decl.docs)) {
                Matcher flow = ITEM_FLOW.matcher(line);
                if (line.startsWith("kind: ")) {
                    for (String part : line.split(";")) {
                        String value = part.strip();
                        if (value.startsWith("kind: ") && ConnectionKind.getByName(value.substring(6).strip()) != null) {
                            connection.setKind(ConnectionKind.getByName(value.substring(6).strip()));
                        } else if (value.startsWith("interface: ")) {
                            connection.setInterfaceType(this.unique(this.interfacesByName, value.substring(11).strip(), decl, "接口"));
                        }
                    }
                } else if (flow.matches()) {
                    ItemFlow item = FACTORY.createItemFlow();
                    item.setId(flow.group(1));
                    item.setName(flow.group(2));
                    item.setItemName(flow.group(3).isEmpty() ? null : flow.group(3));
                    item.setItemType(flow.group(4).isEmpty() ? null : flow.group(4));
                    item.setLayer(connection.getLayer());
                    item.setConnection(connection);
                    this.register(item, "item flow");
                    flows.add(item);
                } else if (line.startsWith("description: ")) {
                    rest.add(line.substring("description: ".length()));
                } else {
                    rest.add(line);
                }
            }
            if (connection.getInterfaceType() == null && source.getInterfaceType() != null) {
                connection.setInterfaceType(source.getInterfaceType()); // as SysArch's connect tool does
            }
            connection.setDescription(rest.isEmpty() ? null : String.join("\n", rest));
            this.roots.add(connection);
            this.roots.addAll(flows);
        }
    }

    /**
     * {@code part.port} or {@code port} inside a block. Plain SysML declares ports on the definition and a part has them
     * through its type; sysarch connects the ports of parts, so such a port is made explicit on the part (same name,
     * direction, interface), and listed.
     */
    private Port end(PartDefinition block, List<String> chain) {
        if (chain == null) {
            return null;
        }
        if (chain.size() == 1) {
            return block.getOwnedPorts().stream().filter(p -> chain.get(0).equals(p.getName())).findFirst().orElse(null);
        }
        if (chain.size() != 2) {
            return null;
        }
        Part part = block.getOwnedParts().stream().filter(p -> chain.get(0).equals(p.getName())).findFirst().orElse(null);
        if (part == null) {
            return null;
        }
        Port own = part.getPorts().stream().filter(p -> chain.get(1).equals(p.getName())).findFirst().orElse(null);
        if (own != null) {
            return own;
        }
        Port typed = typedPort(part.getTypedBy(), chain.get(1), new java.util.HashSet<>());
        if (typed == null) {
            return null;
        }
        Port port = FACTORY.createPort();
        port.setId("IMP-PORT-" + String.format("%04d", ++this.generated));
        port.setName(typed.getName());
        port.setDirection(typed.getDirection());
        port.setInterfaceType(typed.getInterfaceType());
        port.setIsConjugated(typed.isIsConjugated());
        port.setLayer(part.getLayer());
        part.getPorts().add(port);
        this.register(port, "port");
        this.derived.add(port.getId() + "：部件「" + part.getName() + "」的端口「" + port.getName() + "」，按类型「" + part.getTypedBy().getName() + "」补出");
        return port;
    }

    /** A port of a block or of its supertypes, by name. */
    private static Port typedPort(PartDefinition block, String name, java.util.Set<PartDefinition> seen) {
        if (block == null || !seen.add(block)) {
            return null;
        }
        for (Port port : block.getOwnedPorts()) {
            if (name.equals(port.getName())) {
                return port;
            }
        }
        for (PartDefinition superType : block.getSuperTypes()) {
            Port port = typedPort(superType, name, seen);
            if (port != null) {
                return port;
            }
        }
        return null;
    }

    /** References by name, once every definition exists. */
    private void resolve(Decl decl, ArchElement element) {
        switch (element) {
            case PartDefinition block -> decl.superTypes.forEach(name -> add(block.getSuperTypes(), this.unique(this.blocksByName, name, decl, "父类型")));
            case InterfaceDef def -> decl.superTypes.forEach(name -> add(def.getSuperTypes(), this.unique(this.interfacesByName, name, decl, "父接口")));
            case Part part -> part.setTypedBy(decl.type == null ? null : this.unique(this.blocksByName, decl.type, decl, "类型"));
            case Port port -> port.setInterfaceType(decl.type == null ? null : this.unique(this.interfacesByName, decl.type, decl, "接口"));
            case com.example.sysarch.model.Lifeline lifeline -> lifeline.setRepresents(decl.type != null ? this.unique(this.blocksByName, decl.type, decl, "代表的块")
                    : this.represented(decl));
            default -> { }
        }
    }

    private static final Pattern REPRESENTS = Pattern.compile("^represents: .*<([^<>]+)>$");

    /** A lifeline's "represents: name <id>" doc line (what it stands for when that is not a block). */
    private ArchElement represented(Decl decl) {
        for (String line : lines(decl.docs)) {
            var m = REPRESENTS.matcher(line.strip());
            if (m.matches()) {
                ArchElement found = this.byId.get(m.group(1));
                if (found == null) {
                    this.unmapped.add(decl + "：代表的元素 <" + m.group(1) + "> 在文本里没有，未设置");
                }
                return found;
            }
        }
        return null;
    }

    /**
     * v1.4: an {@code occurrence def} as SysArch exports an interaction: {@code part}s are lifelines (typed = the block
     * represented, resolved later), {@code message}s keep their order, ends by short name, else name, among this
     * occurrence's parts; "kind: X" sets the message kind; successions only restate the order and are not needed.
     */
    private void interaction(Decl decl) {
        com.example.sysarch.model.Interaction interaction = FACTORY.createInteraction();
        this.init(interaction, decl, "occurrence def", null);
        interaction.setDescription(text(decl.docs));
        this.roots.add(interaction);
        Map<String, com.example.sysarch.model.Lifeline> byShortName = new HashMap<>();
        Map<String, List<com.example.sysarch.model.Lifeline>> byName = new HashMap<>();
        for (Decl child : decl.children) {
            if (child.kind.equals("part")) {
                com.example.sysarch.model.Lifeline lifeline = FACTORY.createLifeline();
                this.init(lifeline, child, "lifeline", interaction.getLayer());
                lifeline.setDescription(text(child.docs.stream().filter(d -> !d.strip().startsWith("represents:")).toList()));
                interaction.getLifelines().add(lifeline);
                if (child.shortName != null) {
                    byShortName.put(child.shortName, lifeline);
                }
                byName.computeIfAbsent(lifeline.getName(), k -> new ArrayList<>()).add(lifeline);
            }
        }
        for (Decl child : decl.children) {
            switch (child.kind) {
                case "part", "succession" -> { }
                case "message" -> {
                    com.example.sysarch.model.Message message = FACTORY.createMessage();
                    this.init(message, child, "message", interaction.getLayer());
                    List<String> description = new ArrayList<>();
                    for (String doc : child.docs) {
                        if (doc.strip().startsWith("kind: ")) {
                            com.example.sysarch.model.MessageKind kind = com.example.sysarch.model.MessageKind.getByName(doc.strip().substring(6).strip());
                            if (kind == null) {
                                this.unmapped.add(child + "：消息类型「" + doc.strip().substring(6) + "」不存在");
                            } else {
                                message.setKind(kind);
                            }
                        } else {
                            description.add(doc);
                        }
                    }
                    message.setDescription(text(description));
                    message.setSender(this.lifeline(child, child.sourceEnd, byShortName, byName, "发送方"));
                    message.setReceiver(this.lifeline(child, child.targetEnd, byShortName, byName, "接收方"));
                    interaction.getMessages().add(message);
                }
                default -> this.unmapped.add(child + "：交互里的" + child.kind + "未导入");
            }
        }
    }

    private com.example.sysarch.model.Lifeline lifeline(Decl message, List<String> end, Map<String, com.example.sysarch.model.Lifeline> byShortName,
            Map<String, List<com.example.sysarch.model.Lifeline>> byName, String what) {
        if (end == null) {
            return null;
        }
        String ref = end.get(end.size() - 1);
        if (byShortName.containsKey(ref)) {
            return byShortName.get(ref);
        }
        List<com.example.sysarch.model.Lifeline> named = byName.getOrDefault(ref, List.of());
        if (named.size() == 1) {
            return named.get(0);
        }
        this.unmapped.add(message + "：" + what + "「" + ref + "」" + (named.isEmpty() ? "不是本交互的生命线" : "有 " + named.size() + " 条同名生命线，无法确定") + "，未设置");
        return null;
    }

    private static <T> void add(List<T> list, T value) {
        if (value != null) {
            list.add(value);
        }
    }

    private <T extends ArchElement> T unique(Map<String, List<T>> byName, String name, Decl decl, String what) {
        List<T> found = byName.getOrDefault(name, List.of());
        if (found.size() == 1) {
            return found.get(0);
        }
        this.unmapped.add(decl + "：" + what + "「" + name + "」" + (found.isEmpty() ? "在文本里没有定义" : "有 " + found.size() + " 个同名定义，无法确定") + "，未设置");
        return null;
    }

    // ------------------------------------------------------------------ relations (package-level doc)

    private void relation(String line) {
        if (line.isEmpty()) {
            return;
        }
        Matcher satisfy = SATISFY.matcher(line);
        if (satisfy.matches()) {
            ArchElement requirement = this.byId.get(satisfy.group(2));
            ArchElement by = this.byId.get(satisfy.group(3));
            if (requirement instanceof Requirement r && by != null) {
                (satisfy.group(1).equals("满足") ? r.getSatisfiedBy() : r.getVerifiedBy()).add(by);
            } else {
                this.unmapped.add("关系「" + line + "」：元素不在文本里，未导入");
            }
            return;
        }
        Matcher allocate = ALLOCATE.matcher(line);
        if (allocate.matches()) {
            ArchElement from = this.byId.get(allocate.group(2));
            ArchElement to = this.byId.get(allocate.group(3));
            if (from == null || to == null || this.byId.containsKey(allocate.group(1))) {
                this.unmapped.add("分配「" + line + "」：" + (from == null || to == null ? "两端元素不在文本里" : "id 重复") + "，未导入");
                return;
            }
            Allocate allocation = FACTORY.createAllocate();
            allocation.setId(allocate.group(1));
            allocation.setFrom(from);
            allocation.setTo(to);
            allocation.setRationale(allocate.group(4));
            allocation.setName(from.getName() + " → " + to.getName());
            allocation.setLayer(to.getLayer());
            if (allocate.group(5) != null) {
                for (String part : allocate.group(5).split("; ")) {
                    String value = part.strip();
                    if (value.startsWith("name: ")) {
                        allocation.setName(value.substring(6));
                    } else if (value.startsWith("layer: ") && Layer.getByName(value.substring(7)) != null) {
                        allocation.setLayer(Layer.getByName(value.substring(7)));
                    } else if (value.startsWith("description: ")) {
                        allocation.setDescription(value.substring(13));
                    } else if (value.startsWith("stereotypes: ")) {
                        for (String s : value.substring(13).split(",")) {
                            allocation.getStereotypes().add(s.strip());
                        }
                    }
                }
            }
            this.register(allocation, "allocation");
            this.roots.add(allocation);
            return;
        }
        this.unmapped.add("包级说明「" + line + "」未导入");
    }

    // ------------------------------------------------------------------ common

    private void init(ArchElement element, Decl decl, String kind, Layer inherited) {
        String id = decl.shortName;
        if (id == null || id.isBlank()) {
            id = "IMP-" + kind.toUpperCase(java.util.Locale.ROOT).replace(' ', '-') + "-" + String.format("%04d", ++this.generated);
        }
        if (this.byId.containsKey(id)) {
            String unique = id + "-" + (++this.generated);
            this.unmapped.add(decl + "：id「" + id + "」重复，改用「" + unique + "」");
            id = unique;
        }
        element.setId(id);
        element.setName(decl.name == null || decl.name.isBlank() ? id : decl.name);
        Map<String, String> info = decl.metadata.get(METADATA);
        Layer layer = info == null || info.get("layer") == null ? null : Layer.getByName(info.get("layer"));
        if (info != null && info.get("layer") != null && layer == null) {
            this.unmapped.add(decl + "：层「" + info.get("layer") + "」不存在");
        }
        if (layer == null) {
            layer = inherited;
        }
        if (layer == null) {
            layer = this.defaultLayer;
            this.defaulted.add(id + " " + element.getName());
        }
        element.setLayer(layer);
        if (info != null && info.get("stereotypes") != null) {
            for (String stereotype : info.get("stereotypes").split(",")) {
                if (!stereotype.isBlank()) {
                    element.getStereotypes().add(stereotype.strip());
                }
            }
        }
        decl.metadata.keySet().stream().filter(m -> !m.equals(METADATA)).forEach(m -> this.unmapped.add(decl + "：元数据 @" + m + " 未导入"));
        this.elements.put(decl, element);
        this.register(element, kind);
    }

    private void register(ArchElement element, String kind) {
        this.byId.put(element.getId(), element);
        this.counts.merge(kind, 1, Integer::sum);
    }

    private static List<String> lines(List<String> docs) {
        List<String> lines = new ArrayList<>();
        docs.forEach(doc -> lines.addAll(List.of(doc.split("\n", -1))));
        return lines;
    }

    private static String text(List<String> docs) {
        return docs.isEmpty() ? null : String.join("\n", docs);
    }
}

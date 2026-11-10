package com.example.sysarch.report;

import com.example.sysarch.ElementIds;
import com.example.sysarch.baseline.ModelSnapshot;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.DiagramLayout;
import com.example.sysarch.model.InterfaceDef;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.Requirement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.emf.ecore.resource.Resource;

/**
 * The report data extractor (P2 §4.3, T5 step 1): a model in, the template variables out, as plain maps and lists so
 * the templates need nothing but {@code <#list>} and {@code ${...}}. Every judgement (status, coverage, gaps, the
 * relations an ICD row needs) is made here; see {@code docs/report-context.md}. No value is null.
 */
public final class ReportExtractor {

    /** Why a template cannot be generated at all (spec §4.4: "具体缺什么"). */
    public static class MissingException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final List<String> missing;

        public MissingException(List<String> missing) {
            super(String.join("；", missing));
            this.missing = List.copyOf(missing);
        }

        public List<String> missing() {
            return this.missing;
        }
    }

    /** Where the report's data comes from. */
    public record Source(String projectId, String projectName, String baselineLabel, String owner, String generatedAt) {
    }

    private static final Map<Layer, String> LAYER_NAMES = Map.of(Layer.OPERATIONAL, "OA 运行分析", Layer.SYSTEM, "SA 系统分析", Layer.LOGICAL, "LA 逻辑架构",
            Layer.PHYSICAL, "PA 物理架构", Layer.EPBS, "EPBS 产品分解");

    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(Map.entry("description", "说明"), Map.entry("stereotypes", "构造型"),
            Map.entry("typedBy", "类型"), Map.entry("multiplicity", "多重性"), Map.entry("direction", "方向"), Map.entry("interfaceType", "接口"),
            Map.entry("isConjugated", "共轭"), Map.entry("isAbstract", "抽象"), Map.entry("superTypes", "父类型"), Map.entry("protocol", "协议"),
            Map.entry("dataType", "数据类型"), Map.entry("source", "源端口"), Map.entry("target", "目标端口"), Map.entry("kind", "连接类型"),
            Map.entry("reqId", "需求编号"), Map.entry("text", "需求正文"), Map.entry("verifyMethod", "验证方法"), Map.entry("priority", "优先级"),
            Map.entry("satisfiedBy", "满足者"), Map.entry("verifiedBy", "验证者"), Map.entry("from", "分配自"), Map.entry("to", "分配至"),
            Map.entry("rationale", "理由"), Map.entry("workItemId", "Work Item"), Map.entry("rev", "版本"));

    private final List<ArchElement> all = new ArrayList<>();

    private final List<PartDefinition> blocks = new ArrayList<>();

    private final List<Part> parts = new ArrayList<>();

    private final List<Connection> connections = new ArrayList<>();

    private final List<InterfaceDef> interfaceDefs = new ArrayList<>();

    private final List<Requirement> requirements = new ArrayList<>();

    private final List<Port> ports = new ArrayList<>();

    /** Element ids → elements (P2 T7: the appendix names every referenced element; a scan per value was 10k × 10k). */
    private final java.util.Map<String, ArchElement> byId = new java.util.HashMap<>();

    private ReportExtractor(Collection<Resource> model) {
        for (Resource resource : model) {
            resource.getAllContents().forEachRemaining(e -> {
                if (e instanceof ArchElement element && !(e instanceof DiagramLayout)) {
                    this.all.add(element);
                    this.byId.putIfAbsent(ElementIds.of(element), element);
                    switch (element) {
                        case PartDefinition block -> this.blocks.add(block);
                        case Part part -> this.parts.add(part);
                        case Connection connection -> this.connections.add(connection);
                        case InterfaceDef def -> this.interfaceDefs.add(def);
                        case Requirement requirement -> this.requirements.add(requirement);
                        case Port port -> this.ports.add(port);
                        default -> { }
                    }
                }
            });
        }
        Comparator<ArchElement> byId = Comparator.comparing(ElementIds::of);
        for (List<? extends ArchElement> list : List.of(this.all, this.blocks, this.parts, this.connections, this.interfaceDefs, this.requirements, this.ports)) {
            @SuppressWarnings("unchecked")
            List<ArchElement> sortable = (List<ArchElement>) list;
            sortable.sort(byId);
        }
    }

    /**
     * The context of one template.
     *
     * @throws MissingException when the template cannot be generated from this model
     */
    public static Map<String, Object> extract(String templateId, Collection<Resource> model, Source source) {
        ReportExtractor extractor = new ReportExtractor(model);
        List<String> missing = extractor.missing(templateId);
        if (!missing.isEmpty()) {
            throw new MissingException(missing);
        }
        return extractor.context(source);
    }

    List<String> missing(String templateId) {
        List<String> missing = new ArrayList<>();
        switch (templateId) {
            case "srs" -> {
                if (this.system() == null) {
                    missing.add("没有带 system 构造型的块（系统规格书以它为系统概述）");
                }
            }
            case "icd" -> {
                if (this.connections.isEmpty() && this.interfaceDefs.isEmpty()) {
                    missing.add("模型里没有连接，也没有接口定义（接口清单无内容可列）");
                }
            }
            case "trace-report" -> {
                if (this.requirements.isEmpty()) {
                    missing.add("模型里没有需求（追溯报告无内容可列）");
                }
            }
            default -> missing.add("未知模板 " + templateId);
        }
        return missing;
    }

    private Map<String, Object> context(Source source) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("project", map("id", source.projectId(), "name", source.projectName(), "baselineLabel", source.baselineLabel(), "generatedAt",
                source.generatedAt()));
        PartDefinition system = this.system();
        context.put("system", map("name", system == null ? "" : text(system.getName()), "description", system == null ? "" : text(system.getDescription()),
                "owner", text(source.owner())));
        List<Map<String, Object>> elements = new ArrayList<>();
        for (Layer layer : Layer.VALUES) {
            this.structural().stream().filter(e -> e.getLayer() == layer).forEach(e -> elements.add(this.element(e)));
        }
        this.structural().stream().filter(e -> e.getLayer() == null).forEach(e -> elements.add(this.element(e)));
        context.put("elements", elements);
        List<Map<String, Object>> layers = new ArrayList<>();
        for (Layer layer : Layer.VALUES) {
            List<Map<String, Object>> inLayer = elements.stream().filter(e -> layer.getName().equals(e.get("layer"))).toList();
            if (!inLayer.isEmpty()) {
                layers.add(map("layer", layer.getName(), "name", LAYER_NAMES.get(layer), "elements", inLayer));
            }
        }
        context.put("layers", layers);
        context.put("interfaces", this.connections.stream().map(this::interfaceRow).toList());
        context.put("interfaceDefs", this.interfaceDefs.stream().map(this::interfaceDef).toList());
        List<Map<String, Object>> reqs = this.requirements.stream().map(this::requirement).toList();
        context.put("requirements", reqs);
        context.put("unsatisfied", reqs.stream().filter(r -> "missing".equals(r.get("statusClass"))).toList());
        long satisfied = this.requirements.stream().filter(r -> !r.getSatisfiedBy().isEmpty()).count();
        long verified = this.requirements.stream().filter(r -> !r.getVerifiedBy().isEmpty()).count();
        context.put("stats", map("elementCount", this.all.size(), "connectionCount", this.connections.size(), "requirementCount", this.requirements.size(),
                "coverage", this.requirements.isEmpty() ? "—" : Math.round(100.0 * satisfied / this.requirements.size()) + "%", "satisfied", satisfied,
                "verified", verified, "unsatisfied", this.requirements.size() - satisfied, "interfaceCount", this.connections.size()));
        context.put("gaps", this.gaps());
        return context;
    }

    /** The block with the "system" stereotype (the lowest id if several). */
    private PartDefinition system() {
        return this.blocks.stream().filter(b -> b.getStereotypes().contains("system")).findFirst().orElse(null);
    }

    private List<ArchElement> structural() {
        List<ArchElement> structural = new ArrayList<>(this.blocks);
        structural.addAll(this.parts);
        structural.sort(Comparator.comparing(ElementIds::of));
        return structural;
    }

    private Map<String, Object> element(ArchElement element) {
        List<Port> own = element instanceof Part part ? part.getPorts() : ((PartDefinition) element).getOwnedPorts();
        Set<Port> reach = new LinkedHashSet<>(own);
        if (element instanceof PartDefinition block) {
            block.getOwnedParts().forEach(p -> reach.addAll(p.getPorts())); // its inside: connections between its parts
            this.parts.stream().filter(p -> p.getTypedBy() == block).forEach(p -> reach.addAll(p.getPorts())); // its usages
        }
        List<Map<String, Object>> portRows = own.stream().map(p -> map("id", ElementIds.of(p), "name", text(p.getName()), "direction",
                p.getDirection() == null ? "" : p.getDirection().getName(), "interface", p.getInterfaceType() == null ? "" : text(p.getInterfaceType().getName())))
                .toList();
        List<Map<String, Object>> connectionRows = this.connections.stream().filter(c -> reach.contains(c.getSource()) || reach.contains(c.getTarget()))
                .map(c -> map("id", ElementIds.of(c), "name", text(c.getName()), "source", endpoint(c.getSource()), "target", endpoint(c.getTarget()), "interface",
                        c.getInterfaceType() == null ? "" : text(c.getInterfaceType().getName())))
                .toList();
        List<Map<String, Object>> details = new ArrayList<>();
        ModelSnapshot.state(element).attributes().forEach((key, value) -> {
            if (!key.startsWith("$")) {
                details.add(map("key", FIELD_LABELS.getOrDefault(key, key), "value", value instanceof List<?> list ? String.join("、", names(list)) : names(List.of(value)).get(0)));
            }
        });
        return map("id", ElementIds.of(element), "name", text(element.getName()), "type", element instanceof Part ? "部件" : "块", "layer",
                element.getLayer() == null ? "" : element.getLayer().getName(), "layerName", element.getLayer() == null ? "" : LAYER_NAMES.get(element.getLayer()),
                "stereotypes", List.copyOf(element.getStereotypes()), "ports", portRows, "connections", connectionRows, "description",
                text(element.getDescription()), "typedBy", element instanceof Part part && part.getTypedBy() != null ? text(part.getTypedBy().getName()) : "",
                "details", details);
    }

    /** An ICD row: one connection, its interface and the requirements on its two ends. */
    private Map<String, Object> interfaceRow(Connection connection) {
        InterfaceDef def = connection.getInterfaceType();
        Set<ArchElement> ends = new LinkedHashSet<>();
        for (Port port : new Port[] { connection.getSource(), connection.getTarget() }) {
            ArchElement owner = owner(port);
            if (owner != null) {
                ends.add(owner);
                if (owner instanceof Part part && part.getTypedBy() != null) {
                    ends.add(part.getTypedBy());
                }
            }
        }
        Set<String> related = new TreeSet<>();
        this.requirements.stream().filter(r -> r.getSatisfiedBy().stream().anyMatch(ends::contains) || r.getVerifiedBy().stream().anyMatch(ends::contains))
                .forEach(r -> related.add(reqId(r)));
        return map("id", ElementIds.of(connection), "name", text(connection.getName()), "interface", def == null ? "" : text(def.getName()), "protocol",
                def == null ? "" : text(def.getProtocol()), "dataType", def == null ? "" : text(def.getDataType()), "sourceElement", ownerName(connection.getSource()),
                "targetElement", ownerName(connection.getTarget()), "layer", connection.getLayer() == null ? "" : connection.getLayer().getName(), "requirements",
                List.copyOf(related));
    }

    private Map<String, Object> interfaceDef(InterfaceDef def) {
        long usages = this.connections.stream().filter(c -> c.getInterfaceType() == def).count();
        return map("id", ElementIds.of(def), "name", text(def.getName()), "protocol", text(def.getProtocol()), "dataType", text(def.getDataType()), "layer",
                def.getLayer() == null ? "" : def.getLayer().getName(), "usages", usages);
    }

    private Map<String, Object> requirement(Requirement r) {
        boolean satisfied = !r.getSatisfiedBy().isEmpty();
        boolean verified = !r.getVerifiedBy().isEmpty();
        String status = verified && satisfied ? "已验证" : satisfied ? "已满足" : "未满足";
        String statusClass = verified && satisfied ? "ok" : satisfied ? "partial" : "missing";
        return map("id", ElementIds.of(r), "reqId", reqId(r), "name", text(r.getName()), "text", text(r.getText()), "satisfiedBy",
                r.getSatisfiedBy().stream().map(e -> text(e.getName())).toList(), "verifiedBy", r.getVerifiedBy().stream().map(e -> text(e.getName())).toList(),
                "status", status, "statusClass", statusClass, "layer", r.getLayer() == null ? "" : r.getLayer().getName(), "priority", r.getPriority(),
                "verifyMethod", r.getVerifyMethod() == null ? "" : r.getVerifyMethod().getName());
    }

    /** "待补充": what the model still lacks, by kind (empty kinds left out). */
    private List<Map<String, Object>> gaps() {
        List<Map<String, Object>> gaps = new ArrayList<>();
        gap(gaps, "条需求无 satisfiedBy", this.requirements.stream().filter(r -> r.getSatisfiedBy().isEmpty()).map(r -> reqId(r) + " " + text(r.getName())).toList());
        gap(gaps, "条需求未验证（无 verifiedBy）", this.requirements.stream().filter(r -> r.getVerifiedBy().isEmpty()).map(r -> reqId(r) + " " + text(r.getName())).toList());
        gap(gaps, "个接口定义未填协议", this.interfaceDefs.stream().filter(d -> text(d.getProtocol()).isBlank()).map(d -> ElementIds.of(d) + " " + text(d.getName())).toList());
        gap(gaps, "个端口未指定接口", this.ports.stream().filter(p -> p.getInterfaceType() == null).map(p -> ElementIds.of(p) + " " + text(p.getName())).toList());
        gap(gaps, "个部件未指定类型", this.parts.stream().filter(p -> p.getTypedBy() == null).map(p -> ElementIds.of(p) + " " + text(p.getName())).toList());
        gap(gaps, "条连接未指定接口", this.connections.stream().filter(c -> c.getInterfaceType() == null).map(c -> ElementIds.of(c) + " " + text(c.getName())).toList());
        return gaps;
    }

    private static void gap(List<Map<String, Object>> gaps, String title, List<String> items) {
        if (!items.isEmpty()) {
            gaps.add(map("title", items.size() + " " + title, "items", items));
        }
    }

    private static ArchElement owner(Port port) {
        if (port == null) {
            return null;
        }
        return port.getPartOwner() != null ? port.getPartOwner() : port.getOwner();
    }

    private static String ownerName(Port port) {
        ArchElement owner = owner(port);
        return owner == null ? "" : text(owner.getName());
    }

    private static String endpoint(Port port) {
        return port == null ? "" : ownerName(port) + "." + text(port.getName());
    }

    private static String reqId(Requirement r) {
        return text(r.getReqId()).isBlank() ? ElementIds.of(r) : r.getReqId();
    }

    /** Element ids in a value turned into names where they name an element of this model. */
    private List<String> names(List<?> values) {
        List<String> names = new ArrayList<>();
        for (Object value : values) {
            String text = String.valueOf(value);
            ArchElement element = this.byId.get(text);
            names.add(element == null ? text : text(element.getName()) + "（" + text + "）");
        }
        return names;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1] == null ? "" : keyValues[i + 1]);
        }
        return map;
    }

}

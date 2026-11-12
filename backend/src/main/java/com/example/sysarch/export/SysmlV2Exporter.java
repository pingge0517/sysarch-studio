package com.example.sysarch.export;

import com.example.sysarch.ElementIds;
import com.example.sysarch.model.Allocate;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.DiagramLayout;
import com.example.sysarch.model.InterfaceDef;
import com.example.sysarch.model.ItemFlow;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.PortDirection;
import com.example.sysarch.model.Requirement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * SysML v2 textual notation of a sysarch model (P1 T8, spec P1 §5 T8). Verified against the OMG SysML v2 pilot
 * implementation (2026-09.1: no syntax or semantic error, no warning, with the standard library).
 *
 * <p>Mapping (T8 step 2):
 * <ul>
 * <li>{@code PartDefinition} → {@code [abstract] part def}, its {@code superTypes} as {@code :>};
 * {@code Part} → {@code part : Type [multiplicity]}; {@code Port} → {@code in|out|inout port : InterfaceDef}
 * ({@code ~} when conjugated); {@code InterfaceDef} → {@code port def} (its {@code superTypes} as {@code :>});
 * {@code Requirement} → {@code requirement}; {@code Connection} → {@code connection ... connect a to b} inside the
 * block that owns both ends.</li>
 * <li>Every element keeps its id as SysML short name ({@code <'PD-001'>}); names are quoted (unrestricted names), so
 * any name, Chinese included, survives.</li>
 * <li>What SysML v2 has no element for is not invented (T8 step 2/4): {@code layer} and {@code stereotypes} are
 * metadata ({@code @SysArchInfo}, a {@code metadata def} at the top); descriptions, requirement text and fields,
 * interface protocol and data type, item flows, allocations, satisfy/verify and connections whose ends are in
 * different blocks are {@code doc} comments, on the element or, for relations, on the package.</li>
 * <li>Scope (T8 step 1): {@code PROJECT} = everything; {@code LAYER} = the blocks, interfaces and requirements of one
 * layer; {@code ELEMENT} = the given elements (a part, port or connection exports its block). A block or interface
 * referenced from outside the scope is emitted as a bare declaration, so every name resolves.</li>
 * </ul>
 * The output is deterministic (document order, no timestamp). A header of {@code //} notes, which are not model
 * elements, states the scope and the number of exported elements per kind, for the round-trip check in SysON.
 */
public final class SysmlV2Exporter {

    public enum Scope {
        PROJECT, LAYER, ELEMENT
    }

    /** A bad export request (400 at the API). */
    public static class InvalidExportException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public InvalidExportException(String message) {
            super(message);
        }
    }

    static final String METADATA = "SysArchInfo";

    private static final Pattern MULTIPLICITY = Pattern.compile("\\*|\\d+(\\.\\.(\\d+|\\*))?");

    private static final String INDENT = "    ";

    private final List<EObject> all = new ArrayList<>();

    private final Set<ArchElement> selected = new LinkedHashSet<>();

    private final Set<ArchElement> stubs = new LinkedHashSet<>();

    /** Block → the connections drawn inside it. */
    private final Map<PartDefinition, List<Connection>> connectionsByBlock = new LinkedHashMap<>();

    private final List<String> relationNotes = new ArrayList<>();

    private final Map<String, Integer> counts = new LinkedHashMap<>();

    private final StringBuilder out = new StringBuilder();

    private SysmlV2Exporter(Collection<Resource> resources) {
        for (Resource resource : resources) {
            for (TreeIterator<EObject> it = resource.getAllContents(); it.hasNext();) {
                EObject element = it.next();
                if (element instanceof ArchElement && !(element instanceof DiagramLayout)) {
                    this.all.add(element);
                }
            }
        }
    }

    /**
     * @param packageName the name of the exported package (the project's)
     * @param layer for {@link Scope#LAYER}
     * @param elementIds for {@link Scope#ELEMENT}
     */
    public static String export(String packageName, Collection<Resource> resources, Scope scope, Layer layer, Collection<String> elementIds) {
        SysmlV2Exporter exporter = new SysmlV2Exporter(resources);
        exporter.select(scope, layer, elementIds);
        return exporter.render(packageName, scope, layer);
    }

    // ---- what is exported

    private void select(Scope scope, Layer layer, Collection<String> elementIds) {
        switch (scope) {
            case PROJECT -> this.topLevel().forEach(this.selected::add);
            case LAYER -> {
                if (layer == null) {
                    throw new InvalidExportException("scope LAYER needs a layer (" + List.of(Layer.values()) + ")");
                }
                this.topLevel().filter(e -> e.getLayer() == layer).forEach(this.selected::add);
            }
            case ELEMENT -> {
                if (elementIds == null || elementIds.isEmpty()) {
                    throw new InvalidExportException("scope ELEMENT needs elementIds");
                }
                Map<String, ArchElement> byId = new LinkedHashMap<>();
                this.all.forEach(e -> byId.putIfAbsent(ElementIds.of(e), (ArchElement) e));
                for (String id : elementIds) {
                    ArchElement element = byId.get(id);
                    if (element == null) {
                        throw new InvalidExportException("no element '" + id + "'");
                    }
                    ArchElement exported = this.exportedFor(element);
                    if (exported == null) {
                        throw new InvalidExportException("element '" + id + "' (" + element.eClass().getName() + ") cannot be exported on its own");
                    }
                    this.selected.add(exported);
                }
            }
        }
        // Keep document order.
        List<ArchElement> ordered = this.all.stream().map(e -> (ArchElement) e).filter(this.selected::contains).toList();
        this.selected.clear();
        this.selected.addAll(ordered);
        this.placeConnections();
        this.collectStubs();
    }

    /** The elements that are exported as top-level declarations. */
    private java.util.stream.Stream<ArchElement> topLevel() {
        return this.all.stream().map(e -> (ArchElement) e)
                .filter(e -> e instanceof PartDefinition || e instanceof InterfaceDef || e instanceof Requirement || e instanceof com.example.sysarch.model.Interaction);
    }

    /** A part, port or connection is exported with the block that holds it. */
    private ArchElement exportedFor(ArchElement element) {
        return switch (element) {
            case PartDefinition block -> block;
            case InterfaceDef interfaceDef -> interfaceDef;
            case Requirement requirement -> requirement;
            case com.example.sysarch.model.Interaction interaction -> interaction;
            case com.example.sysarch.model.Lifeline lifeline -> (ArchElement) lifeline.eContainer();
            case com.example.sysarch.model.Message message -> (ArchElement) message.eContainer();
            case Part part -> part.getOwner();
            case Port port -> blockOf(port);
            case Connection connection -> commonBlock(connection);
            default -> null;
        };
    }

    private void placeConnections() {
        for (EObject element : this.all) {
            if (element instanceof Connection connection) {
                PartDefinition block = commonBlock(connection);
                if (block != null && this.selected.contains(block)) {
                    this.connectionsByBlock.computeIfAbsent(block, b -> new ArrayList<>()).add(connection);
                } else if (this.touchesSelection(connection)) {
                    this.relationNotes.add("连接 " + id(connection) + " " + name(connection) + "：" + end(connection.getSource()) + " → "
                            + end(connection.getTarget()) + "（两端不在同一个块中，SysML v2 无法在一处连接，仅作说明）");
                }
            }
        }
    }

    private boolean touchesSelection(Connection connection) {
        return this.selected.contains(blockOf(connection.getSource())) || this.selected.contains(blockOf(connection.getTarget()));
    }

    /** Blocks and interfaces that exported elements reference but the scope leaves out: declared bare, so names resolve. */
    private void collectStubs() {
        Set<ArchElement> referenced = new LinkedHashSet<>();
        for (ArchElement element : this.selected) {
            if (element instanceof PartDefinition block) {
                referenced.addAll(block.getSuperTypes());
                for (Part part : block.getOwnedParts()) {
                    if (part.getTypedBy() != null) {
                        referenced.add(part.getTypedBy());
                    }
                    part.getPorts().forEach(port -> addInterface(referenced, port));
                }
                block.getOwnedPorts().forEach(port -> addInterface(referenced, port));
            } else if (element instanceof InterfaceDef interfaceDef) {
                referenced.addAll(interfaceDef.getSuperTypes());
            }
        }
        referenced.removeAll(this.selected);
        this.all.stream().map(e -> (ArchElement) e).filter(referenced::contains).forEach(this.stubs::add);
    }

    private static void addInterface(Set<ArchElement> referenced, Port port) {
        if (port.getInterfaceType() != null) {
            referenced.add(port.getInterfaceType());
        }
    }

    // ---- rendering

    private String render(String packageName, Scope scope, Layer layer) {
        this.out.setLength(0); // the package body first, then the header, which counts what the body holds
        this.line(1, "private import ScalarValues::*;");
        this.line(0, "");
        this.line(1, "/* SysArch Studio：SysML v2 没有对应元素的 layer、stereotypes 以元数据保留。 */");
        this.line(1, "metadata def " + METADATA + " {");
        this.line(2, "attribute layer : String;");
        this.line(2, "attribute stereotypes : String;");
        this.line(1, "}");
        for (ArchElement element : this.selected) {
            if (element instanceof InterfaceDef interfaceDef) {
                this.line(0, "");
                this.interfaceDef(interfaceDef);
            }
        }
        for (ArchElement element : this.selected) {
            if (element instanceof PartDefinition block) {
                this.line(0, "");
                this.block(block);
            }
        }
        for (ArchElement element : this.selected) {
            if (element instanceof Requirement requirement) {
                this.line(0, "");
                this.requirement(requirement);
            }
        }
        for (ArchElement element : this.selected) {
            if (element instanceof com.example.sysarch.model.Interaction interaction) {
                this.line(0, "");
                this.interaction(interaction);
            }
        }
        if (!this.stubs.isEmpty()) {
            this.line(0, "");
            this.line(1, "// 导出范围之外、但被引用的定义：只声明，保证名称可解析。");
            for (ArchElement stub : this.stubs) {
                this.count(stub instanceof InterfaceDef ? "port def" : "part def");
                this.line(1, (stub instanceof InterfaceDef ? "port def " : "part def ") + shortAndName(stub) + ";");
            }
        }
        this.relations();
        String body = this.out.toString();
        this.out.setLength(0);

        this.line(0, "// SysML v2 textual notation exported by SysArch Studio (P1). Element ids are the short names <'...'>.");
        this.line(0, "// scope: " + scope + (scope == Scope.LAYER ? " " + layer.getName() : ""));
        StringBuilder counts = new StringBuilder("// elements:");
        this.counts.forEach((kind, n) -> counts.append(' ').append(kind).append('=').append(n));
        this.line(0, counts.toString());
        this.line(0, "package " + quote(packageName) + " {");
        this.out.append(body);
        this.line(0, "}");
        return this.out.toString();
    }

    private void interfaceDef(InterfaceDef interfaceDef) {
        this.count("port def");
        this.line(1, "port def " + shortAndName(interfaceDef) + specializes(interfaceDef.getSuperTypes()) + " {");
        this.metadata(2, interfaceDef);
        List<String> doc = new ArrayList<>();
        if (interfaceDef.getProtocol() != null) {
            doc.add("protocol: " + interfaceDef.getProtocol());
        }
        if (interfaceDef.getDataType() != null) {
            doc.add("data type: " + interfaceDef.getDataType());
        }
        if (interfaceDef.getDescription() != null) {
            doc.add(interfaceDef.getDescription());
        }
        this.doc(2, doc);
        this.line(1, "}");
    }

    private void block(PartDefinition block) {
        this.count("part def");
        this.line(1, (block.isIsAbstract() ? "abstract " : "") + "part def " + shortAndName(block) + specializes(block.getSuperTypes()) + " {");
        this.metadata(2, block);
        this.doc(2, block.getDescription() == null ? List.of() : List.of(block.getDescription()));
        block.getOwnedPorts().forEach(port -> this.port(2, port, block.getLayer()));
        for (Part part : block.getOwnedParts()) {
            this.count("part");
            String head = "part " + shortAndName(part) + (part.getTypedBy() == null ? "" : " : " + quote(name(part.getTypedBy()))) + multiplicity(part);
            boolean extras = this.hasExtras(part, block.getLayer());
            if (part.getPorts().isEmpty() && !extras) {
                this.line(2, head + ";");
            } else {
                this.line(2, head + " {");
                this.extras(3, part, block.getLayer());
                part.getPorts().forEach(port -> this.port(3, port, part.getLayer()));
                this.line(2, "}");
            }
        }
        for (Connection connection : this.connectionsByBlock.getOrDefault(block, List.of())) {
            this.count("connection");
            String head = "connection " + shortAndName(connection) + " connect " + end(connection.getSource()) + " to " + end(connection.getTarget());
            List<String> doc = new ArrayList<>();
            doc.add("kind: " + connection.getKind().getName() + (connection.getInterfaceType() == null ? "" : "; interface: " + name(connection.getInterfaceType())));
            for (EObject element : this.all) {
                if (element instanceof ItemFlow flow && flow.getConnection() == connection) {
                    this.count("item flow (doc)");
                    doc.add("item flow " + id(flow) + " " + name(flow) + ": " + Objects.toString(flow.getItemName(), "") + " : " + Objects.toString(flow.getItemType(), ""));
                }
            }
            if (connection.getDescription() != null) {
                doc.add("description: " + connection.getDescription());
            }
            this.line(2, head + " {");
            if (connection.getLayer() != block.getLayer() || !connection.getStereotypes().isEmpty()) {
                this.metadata(3, connection);
            }
            this.doc(3, doc);
            this.line(2, "}");
        }
        this.line(1, "}");
    }

    /**
     * What a part or port carries beyond its declaration (P2 T6, for a lossless import): metadata when its layer
     * differs from the layer it sits in or it has stereotypes, a doc when it has a description. Nothing for the
     * common case, so the usual export stays as it was.
     */
    private boolean hasExtras(ArchElement element, Layer inherited) {
        return element.getLayer() != inherited || !element.getStereotypes().isEmpty() || element.getDescription() != null;
    }

    private void extras(int depth, ArchElement element, Layer inherited) {
        if (element.getLayer() != inherited || !element.getStereotypes().isEmpty()) {
            this.metadata(depth, element);
        }
        if (element.getDescription() != null) {
            this.doc(depth, List.of(element.getDescription()));
        }
    }

    private void port(int depth, Port port, Layer inherited) {
        this.count("port");
        String direction = port.getDirection() == PortDirection.IN ? "in " : port.getDirection() == PortDirection.OUT ? "out " : "inout ";
        String type = port.getInterfaceType() == null ? "" : " : " + (port.isIsConjugated() ? "~" : "") + quote(name(port.getInterfaceType()));
        if (this.hasExtras(port, inherited)) {
            this.line(depth, direction + "port " + shortAndName(port) + type + " {");
            this.extras(depth + 1, port, inherited);
            this.line(depth, "}");
        } else {
            this.line(depth, direction + "port " + shortAndName(port) + type + ";");
        }
    }

    private void requirement(Requirement requirement) {
        this.count("requirement");
        this.line(1, "requirement " + shortAndName(requirement) + " {");
        this.metadata(2, requirement);
        List<String> doc = new ArrayList<>();
        if (requirement.getReqId() != null) {
            doc.add("reqId: " + requirement.getReqId());
        }
        doc.add("verify method: " + requirement.getVerifyMethod().getName() + "; priority: " + requirement.getPriority());
        if (requirement.getText() != null) {
            doc.add(requirement.getText());
        }
        if (requirement.getDescription() != null) {
            doc.add("description: " + requirement.getDescription()); // P2 T6: told apart from the text on import
        }
        this.doc(2, doc);
        this.line(1, "}");
    }

    /**
     * v1.4: an interaction as SysML v2's form of one: an {@code occurrence def} whose {@code part}s are the lifelines (typed
     * by the block a lifeline represents) and whose {@code message}s run {@code from} sender {@code to} receiver, in time
     * order, which {@code first … then …} successions state. Ends and successions use the short names (ids are unique,
     * names need not be). What SysML v2 has no place for is a doc line, as elsewhere: a lifeline that represents
     * something other than a block ({@code represents: name <id>}), a message kind other than SYNC ({@code kind: REPLY}).
     */
    private void interaction(com.example.sysarch.model.Interaction interaction) {
        this.count("occurrence def");
        this.line(1, "occurrence def " + shortAndName(interaction) + " {");
        this.metadata(2, interaction);
        this.doc(2, interaction.getDescription() == null ? List.of() : List.of(interaction.getDescription()));
        Layer layer = interaction.getLayer();
        for (com.example.sysarch.model.Lifeline lifeline : interaction.getLifelines()) {
            this.count("lifeline");
            ArchElement represents = lifeline.getRepresents();
            String head = "part " + shortAndName(lifeline) + (represents instanceof PartDefinition block ? " : " + quote(name(block)) : "");
            boolean representsOther = represents != null && !(represents instanceof PartDefinition);
            if (this.hasExtras(lifeline, layer) || representsOther) {
                this.line(2, head + " {");
                this.extras(3, lifeline, layer);
                if (representsOther) {
                    this.doc(3, List.of("represents: " + reference(represents)));
                }
                this.line(2, "}");
            } else {
                this.line(2, head + ";");
            }
        }
        List<com.example.sysarch.model.Message> messages = interaction.getMessages();
        for (com.example.sysarch.model.Message message : messages) {
            this.count("message");
            String ends = message.getSender() == null || message.getReceiver() == null ? ""
                    : " from " + quote(id(message.getSender())) + " to " + quote(id(message.getReceiver()));
            String head = "message " + shortAndName(message) + ends;
            boolean kind = message.getKind() != com.example.sysarch.model.MessageKind.SYNC;
            if (this.hasExtras(message, layer) || kind) {
                this.line(2, head + " {");
                this.extras(3, message, layer);
                if (kind) {
                    this.doc(3, List.of("kind: " + message.getKind().getName()));
                }
                this.line(2, "}");
            } else {
                this.line(2, head + ";");
            }
        }
        for (int i = 1; i < messages.size(); i++) {
            this.line(2, "first " + quote(id(messages.get(i - 1))) + " then " + quote(id(messages.get(i))) + ";");
        }
        this.line(1, "}");
    }

    /** Relations SysML v2 gets as text only: allocations, satisfy/verify, connections across blocks. */
    private void relations() {
        List<String> notes = new ArrayList<>();
        for (EObject element : this.all) {
            if (element instanceof Allocate allocate && (this.selected.contains(blockOrSelf(allocate.getFrom())) || this.selected.contains(blockOrSelf(allocate.getTo())))) {
                notes.add("分配 " + id(allocate) + "：" + reference(allocate.getFrom()) + " → " + reference(allocate.getTo())
                        + (allocate.getRationale() == null ? "" : "（" + allocate.getRationale() + "）") + allocationExtras(allocate));
            }
            if (element instanceof Requirement requirement && this.selected.contains(requirement)) {
                requirement.getSatisfiedBy().forEach(by -> notes.add("满足 " + reference(requirement) + " ← " + reference(by)));
                requirement.getVerifiedBy().forEach(by -> notes.add("验证 " + reference(requirement) + " ← " + reference(by)));
            }
        }
        notes.addAll(this.relationNotes);
        if (!notes.isEmpty()) {
            this.line(0, "");
            this.count("relation (doc)", notes.size());
            this.doc(1, notes);
        }
    }

    private void metadata(int depth, ArchElement element) {
        StringBuilder text = new StringBuilder("@" + METADATA + " { layer = " + string(element.getLayer().getName()) + ";");
        if (!element.getStereotypes().isEmpty()) {
            text.append(" stereotypes = ").append(string(String.join(", ", element.getStereotypes()))).append(';');
        }
        this.line(depth, text.append(" }").toString());
    }

    private void doc(int depth, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        if (lines.size() == 1 && !lines.get(0).contains("\n")) {
            this.line(depth, "doc /* " + commentSafe(lines.get(0)) + " */");
            return;
        }
        this.line(depth, "doc /*");
        for (String text : lines) {
            for (String line : commentSafe(text).split("\n", -1)) {
                this.line(depth, " * " + line);
            }
        }
        this.line(depth, " */");
    }

    private void line(int depth, String text) {
        this.out.append(INDENT.repeat(depth)).append(text).append('\n');
    }

    private void count(String kind) {
        this.count(kind, 1);
    }

    private void count(String kind, int n) {
        this.counts.merge(kind, n, Integer::sum);
    }

    // ---- names

    /** {@code <'id'> 'name'}: the element id as short name, the name quoted. */
    static String shortAndName(ArchElement element) {
        return "<" + quote(id(element)) + "> " + quote(name(element));
    }

    /** A SysML v2 unrestricted name: single quotes, with backslash and quote escaped. */
    static String quote(String name) {
        return "'" + name.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /** A SysML v2 string literal. */
    static String string(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** A comment body cannot contain the closing delimiter. */
    static String commentSafe(String text) {
        return text.replace("*/", "* /").replace("\r", "");
    }

    static String name(ArchElement element) {
        return element.getName() == null || element.getName().isBlank() ? id(element) : element.getName();
    }

    private static String id(ArchElement element) {
        return ElementIds.of(element);
    }

    /**
     * An allocation's name and layer when they are not the import's defaults (name "from → to", the layer of "to"),
     * and its description: {@code {name: …; layer: …; description: …}} (P2 T6). Empty for the usual allocation.
     */
    static String allocationExtras(Allocate allocate) {
        List<String> parts = new ArrayList<>();
        String defaultName = allocate.getFrom() == null || allocate.getTo() == null ? null : name(allocate.getFrom()) + " → " + name(allocate.getTo());
        if (allocate.getName() != null && !allocate.getName().equals(defaultName)) {
            parts.add("name: " + allocate.getName());
        }
        Layer defaultLayer = allocate.getTo() == null ? null : allocate.getTo().getLayer();
        if (allocate.getLayer() != null && allocate.getLayer() != defaultLayer) {
            parts.add("layer: " + allocate.getLayer().getName());
        }
        if (allocate.getDescription() != null) {
            parts.add("description: " + allocate.getDescription().replace('\n', ' '));
        }
        if (!allocate.getStereotypes().isEmpty()) {
            parts.add("stereotypes: " + String.join(", ", allocate.getStereotypes()));
        }
        return parts.isEmpty() ? "" : " {" + String.join("; ", parts) + "}";
    }

    private static String reference(ArchElement element) {
        return element == null ? "（未设置）" : name(element) + " <" + id(element) + ">";
    }

    private static String specializes(List<? extends ArchElement> superTypes) {
        return superTypes.isEmpty() ? "" : " :> " + String.join(", ", superTypes.stream().map(t -> quote(name(t))).toList());
    }

    private static String multiplicity(Part part) {
        String value = part.getMultiplicity() == null ? null : part.getMultiplicity().replace(" ", "");
        return value != null && MULTIPLICITY.matcher(value).matches() ? " [" + value + "]" : "";
    }

    /** {@code part.port} for a port on a part, {@code port} for a port of the block itself (inside that block). */
    private static String end(Port port) {
        if (port == null) {
            return "（未设置）";
        }
        return port.getPartOwner() != null ? quote(name(port.getPartOwner())) + "." + quote(name(port)) : quote(name(port));
    }

    /** The block a port belongs to: its owner, or the owner of its part. */
    static PartDefinition blockOf(Port port) {
        if (port == null) {
            return null;
        }
        return port.getOwner() != null ? port.getOwner() : port.getPartOwner() == null ? null : port.getPartOwner().getOwner();
    }

    /** The block that holds both ends of a connection, or null. */
    static PartDefinition commonBlock(Connection connection) {
        PartDefinition source = blockOf(connection.getSource());
        PartDefinition target = blockOf(connection.getTarget());
        return source != null && source == target ? source : null;
    }

    private static ArchElement blockOrSelf(ArchElement element) {
        return element instanceof Part part ? part.getOwner() : element instanceof Port port ? blockOf(port) : element;
    }
}

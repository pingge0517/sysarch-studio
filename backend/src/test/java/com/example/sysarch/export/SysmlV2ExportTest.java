package com.example.sysarch.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.model.ItemFlow;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.SysarchFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * P1 T8: the SysML v2 text of the domain-controller demo. The three scopes are also written to
 * {@code build/sysml-export/} for the check with the OMG pilot parser (no syntax or semantic error, no warning), which
 * is not part of the build: it is a 120 MB download (see CLAUDE.md).
 */
class SysmlV2ExportTest {

    private static final Path OUTPUT = Path.of("build", "sysml-export");

    private Resource demo;

    @BeforeEach
    void demo() {
        this.demo = new ResourceImpl(URI.createURI("memory:/demo.xmi"));
        this.demo.getContents().addAll(DomainControllerDemo.build());
    }

    @Test
    void theProjectExportHasEveryElementWithItsNameIdAndLayer() throws IOException {
        String text = this.export(SysmlV2Exporter.Scope.PROJECT, null, null, "demo-project.sysml");

        assertTrue(text.startsWith("// SysML v2 textual notation exported by SysArch Studio"), text);
        assertTrue(text.contains("package '域控演示' {"));
        assertTrue(text.contains("// elements: port def=1 part def=6 part=3 port=6 connection=4 requirement=5 relation (doc)=7"), header(text));
        assertTrue(text.contains("part def <'PD-001'> '域控制器' {\n        @SysArchInfo { layer = \"LOGICAL\"; stereotypes = \"system\"; }"), text);
        assertTrue(text.contains("part <'PT-001'> '感知融合' : '感知融合单元' [1] {\n            out port <'PO-001'> 'objects' : 'CAN-FD';"), text);
        assertTrue(text.contains("connection <'CO-001'> '目标列表' connect '感知融合'.'objects' to '决策规划'.'perception' {"), text);
        assertTrue(text.contains("port def <'IF-001'> 'CAN-FD' {"), text);
        assertTrue(text.contains("requirement <'RQ-001'> '目标列表周期' {"), text);
        assertTrue(text.contains(" * 满足 目标列表周期 <RQ-001> ← 感知融合单元 <PD-002>"), text);
        assertTrue(text.contains(" * 分配 AL-001：感知融合单元 <PD-002> → 域控 SoC <PD-005>（感知融合需要 SoC 的 NPU）"), text);
    }

    /** Same model, same text: no timestamp, document order. */
    @Test
    void theExportIsDeterministic() {
        assertEquals(SysmlV2Exporter.export("x", List.of(this.demo), SysmlV2Exporter.Scope.PROJECT, null, null),
                SysmlV2Exporter.export("x", List.of(this.demo), SysmlV2Exporter.Scope.PROJECT, null, null));
    }

    /** A layer export declares what it references from other layers, so every name resolves. */
    @Test
    void aLayerExportDeclaresReferencedDefinitions() throws IOException {
        String physical = this.export(SysmlV2Exporter.Scope.LAYER, Layer.PHYSICAL, null, "demo-physical.sysml");

        assertTrue(physical.contains("// scope: LAYER PHYSICAL"));
        assertTrue(physical.contains("part def <'PD-005'> '域控 SoC' {"));
        assertFalse(physical.contains("part def <'PD-001'>"), "LOGICAL blocks are not exported");
        assertTrue(physical.contains(" * 分配 AL-001："), "allocations into the layer are kept");
        assertTrue(physical.contains("requirement <'RQ-004'> '算力余量' {"));

        String logical = this.export(SysmlV2Exporter.Scope.LAYER, Layer.LOGICAL, null, "demo-logical.sysml");
        assertTrue(logical.contains("part def <'PD-001'> '域控制器' {"));
        assertFalse(logical.contains("<'PD-005'>"));
    }

    @Test
    void anElementExportTakesTheBlockAndDeclaresWhatItNeeds() throws IOException {
        String text = this.export(SysmlV2Exporter.Scope.ELEMENT, null, List.of("PT-001"), "demo-element.sysml");

        assertTrue(text.contains("part def <'PD-001'> '域控制器' {"), "a part exports its block");
        assertTrue(text.contains("    part def <'PD-002'> '感知融合单元';"), "the part's type is declared bare");
        assertTrue(text.contains("    port def <'IF-001'> 'CAN-FD';"), "and the ports' interface");
        assertFalse(text.contains("requirement"));
    }

    @Test
    void badScopesAreRefused() {
        assertThrows(SysmlV2Exporter.InvalidExportException.class,
                () -> SysmlV2Exporter.export("x", List.of(this.demo), SysmlV2Exporter.Scope.LAYER, null, null));
        assertThrows(SysmlV2Exporter.InvalidExportException.class,
                () -> SysmlV2Exporter.export("x", List.of(this.demo), SysmlV2Exporter.Scope.ELEMENT, null, List.of("nope")));
        assertThrows(SysmlV2Exporter.InvalidExportException.class,
                () -> SysmlV2Exporter.export("x", List.of(this.demo), SysmlV2Exporter.Scope.ELEMENT, null, List.of()));
    }

    /** Names are unrestricted names: quotes and backslashes escaped; comments cannot be closed early. */
    @Test
    void namesAndTextsAreEscaped() throws IOException {
        PartDefinition odd = SysarchFactory.eINSTANCE.createPartDefinition();
        odd.setId("PD-ODD");
        odd.setName("O'Brien \\ \"box\"");
        odd.setDescription("a */ b");
        odd.getStereotypes().add("say \"hi\"");
        this.demo.getContents().add(odd);
        ItemFlow flow = SysarchFactory.eINSTANCE.createItemFlow();
        flow.setId("IFL-1");
        flow.setName("目标");
        flow.setItemName("objects");
        flow.setItemType("ObjectList");
        flow.setConnection((com.example.sysarch.model.Connection) this.demo.getContents().stream()
                .filter(e -> e instanceof com.example.sysarch.model.Connection c && "CO-001".equals(c.getId())).findFirst().orElseThrow());
        this.demo.getContents().add(flow);

        String text = this.export(SysmlV2Exporter.Scope.PROJECT, null, null, "demo-escaping.sysml");

        assertTrue(text.contains("part def <'PD-ODD'> 'O\\'Brien \\\\ \"box\"' {"), text);
        assertTrue(text.contains("stereotypes = \"say \\\"hi\\\"\";"));
        assertTrue(text.contains("doc /* a * / b */"));
        assertTrue(text.contains(" * item flow IFL-1 目标: objects : ObjectList"), "an item flow is a doc comment on its connection");
    }

    private String export(SysmlV2Exporter.Scope scope, Layer layer, List<String> ids, String file) throws IOException {
        String text = SysmlV2Exporter.export("域控演示", List.of(this.demo), scope, layer, ids);
        Files.createDirectories(OUTPUT);
        Files.writeString(OUTPUT.resolve(file), text, StandardCharsets.UTF_8);
        return text;
    }

    private static String header(String text) {
        Matcher matcher = Pattern.compile("// elements:[^\n]*").matcher(text);
        return matcher.find() ? matcher.group() : text;
    }
}

package com.example.sysarch.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.PartDefinition;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * P2 T5 acceptance ("演示数据生成三份报告，SRS 不少于 12 页，ICD 的接口条数与模型一致") and P2-AC-5 ("12 页以上，含三层架构与
 * 追溯表"), plus the template rule (risk table: only loops and interpolation). Outputs go to {@code build/reports/}.
 */
class ReportTemplatesTest {

    private static final Path OUT = Path.of("build", "reports");

    private static final ReportRenderer RENDERER = new ReportRenderer(ReportConfiguration.defaultFonts());

    private static Resource demo;

    @BeforeAll
    static void demo() throws IOException {
        demo = new ResourceImpl(URI.createURI("memory:/demo.json"));
        demo.getContents().addAll(DomainControllerDemo.build());
        Files.createDirectories(OUT);
    }

    @Test
    void theSrsHasTwelvePagesOrMoreWithTheLayersAndTheTraceTable() throws IOException {
        ReportRenderer.Rendered srs = this.render("srs");

        assertTrue(srs.pages() >= 12, "SRS has " + srs.pages() + " pages");
        String text = pdfText(srs.pdf());
        assertTrue(text.contains("LA 逻辑架构") && text.contains("PA 物理架构"), "the layer chapters");
        assertTrue(text.contains("需求追溯表"));
        assertTrue(text.contains("域控制器"), "Chinese text is in the PDF (a CJK font was embedded): " + text.substring(0, Math.min(200, text.length())));
        assertTrue(srs.html().contains("2 条需求无 satisfiedBy"), "gaps listed, not a failure");
        assertTrue(docxText(srs.docx()).contains("系统规格书"), "the Word file has the content");
    }

    /** ICD: one row per connection; as many as the model has. */
    @Test
    void theIcdListsEveryConnectionOfTheModel() throws IOException {
        long connections = demo.getContents().stream().filter(Connection.class::isInstance).count();
        ReportRenderer.Rendered icd = this.render("icd");

        Map<String, Object> context = ReportExtractor.extract("icd", List.of(demo), source());
        assertEquals((int) connections, ((List<?>) context.get("interfaces")).size());
        assertEquals(4, connections);
        String html = icd.html();
        assertEquals(connections, Pattern.compile("<h3>2\\.\\d+ ").matcher(html).results().count(), "one section per interface");
        assertTrue(html.contains("CAN FD"), "protocol from the interface definition");
        assertTrue(html.contains("RQ-001") || html.contains("DC-REQ-001"), "related requirements: " + html.length());
    }

    @Test
    void theTraceReportHasTotalsCoverageAndTheUnsatisfiedList() throws IOException {
        Map<String, Object> context = ReportExtractor.extract("trace-report", List.of(demo), source());
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) context.get("stats");
        assertEquals(5, stats.get("requirementCount"));
        assertEquals(3L, stats.get("satisfied"));
        assertEquals("60%", stats.get("coverage"), "coverage = satisfied / total, computed by the extractor");
        assertEquals(2, ((List<?>) context.get("unsatisfied")).size());
        String html = this.render("trace-report").html();
        assertTrue(html.contains("60%"));
        assertTrue(html.contains("class=\"missing\""));
    }

    @Test
    void theHtmlIsWellFormedXhtml() throws Exception {
        for (String template : ReportRenderer.TEMPLATES) {
            String html = RENDERER.html(template, ReportExtractor.extract(template, List.of(demo), source()));
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.newDocumentBuilder().parse(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
        }
    }

    /** "具体缺什么": a template that cannot be generated names what is missing. */
    @Test
    void whatCannotBeGeneratedSaysWhatIsMissing() {
        Resource empty = new ResourceImpl(URI.createURI("memory:/empty.json"));
        PartDefinition block = com.example.sysarch.model.SysarchFactory.eINSTANCE.createPartDefinition();
        block.setId("PD-1");
        block.setName("无构造型的块");
        empty.getContents().add(block);
        ReportExtractor.MissingException srs = assertThrows(ReportExtractor.MissingException.class,
                () -> ReportExtractor.extract("srs", List.of(empty), source()));
        assertTrue(srs.missing().get(0).contains("system 构造型"));
        assertThrows(ReportExtractor.MissingException.class, () -> ReportExtractor.extract("icd", List.of(empty), source()));
        assertThrows(ReportExtractor.MissingException.class, () -> ReportExtractor.extract("trace-report", List.of(empty), source()));
    }

    /** Risk table: "模板只允许 loop 与变量插值，判断逻辑退回抽取器". */
    @Test
    void templatesHoldNoDecisions() throws IOException {
        Pattern forbidden = Pattern.compile("<#if|<#elseif|<#switch|<#case|\\?then\\(|\\?\\?|\\$\\{[^}]*![^=]");
        for (String file : List.of("srs.ftl", "icd.ftl", "trace-report.ftl", "_style.ftl")) {
            try (InputStream in = ReportRenderer.class.getResourceAsStream("/reports/" + file)) {
                String template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertFalse(forbidden.matcher(template).find(), file + " contains a decision: " + forbidden.matcher(template).results().map(m -> m.group()).toList());
            }
        }
    }

    private ReportRenderer.Rendered render(String template) throws IOException {
        ReportRenderer.Rendered rendered = RENDERER.render(template, ReportExtractor.extract(template, List.of(demo), source()));
        Files.writeString(OUT.resolve(template + ".html"), rendered.html(), StandardCharsets.UTF_8);
        Files.write(OUT.resolve(template + ".docx"), rendered.docx());
        Files.write(OUT.resolve(template + ".pdf"), rendered.pdf());
        return rendered;
    }

    private static ReportExtractor.Source source() {
        return new ReportExtractor.Source("p-1", "域控演示", "评审前", "alice", "2026-11-10 10:00");
    }

    private static String pdfText(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static String docxText(byte[] docx) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals("word/document.xml")) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8).replaceAll("<[^>]+>", "");
                }
            }
        }
        return "";
    }
}

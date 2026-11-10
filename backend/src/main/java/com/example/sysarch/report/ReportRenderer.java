package com.example.sysarch.report;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.docx4j.convert.in.xhtml.XHTMLImporterImpl;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.RFonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Template + context → XHTML → Word and PDF (P2 §4.1, T5 step 2). One HTML for all three formats: Word through
 * docx4j's XHTML import (the spec's route), PDF (user's decision, for the page count) through openhtmltopdf at A4.
 *
 * <p>PDF embeds a CJK TrueType font from {@code sysarch.reports.fonts} (the first {@code .ttf} that exists),
 * registered as the CSS family {@code "SysArch CJK"}; without one Chinese text would be blank.
 * Word only names the fonts (Microsoft YaHei / SimHei), the reader's Word renders them.
 */
public class ReportRenderer {

    public static final List<String> TEMPLATES = List.of("srs", "icd", "trace-report");

    public static final String FONT_FAMILY = "SysArch CJK";

    /** One rendered report. */
    public record Rendered(String html, byte[] docx, byte[] pdf, int pages) {
    }

    private final Logger logger = LoggerFactory.getLogger(ReportRenderer.class);

    private final Configuration freemarker;

    private final List<Path> fonts;

    public ReportRenderer(List<Path> fonts) {
        this.fonts = List.copyOf(fonts);
        this.freemarker = new Configuration(Configuration.VERSION_2_3_33);
        this.freemarker.setClassForTemplateLoading(ReportRenderer.class, "/reports");
        this.freemarker.setDefaultEncoding("UTF-8");
        this.freemarker.setOutputEncoding("UTF-8");
        this.freemarker.setLogTemplateExceptions(false);
        this.freemarker.setWrapUncheckedExceptions(true);
        this.freemarker.setFallbackOnNullLoopVariable(false);
    }

    public Rendered render(String templateId, Map<String, Object> context) {
        String html = this.html(templateId, context);
        byte[] pdf = this.pdf(html);
        return new Rendered(html, this.docx(html), pdf, pages(pdf));
    }

    public String html(String templateId, Map<String, Object> context) {
        if (!TEMPLATES.contains(templateId)) {
            throw new IllegalArgumentException("unknown template " + templateId);
        }
        try {
            Template template = this.freemarker.getTemplate(templateId + ".ftl");
            StringWriter out = new StringWriter();
            template.process(context, out);
            return out.toString();
        } catch (IOException | TemplateException e) {
            throw new IllegalStateException("template " + templateId + " failed: " + e.getMessage(), e);
        }
    }

    byte[] docx(String html) {
        try {
            WordprocessingMLPackage word = WordprocessingMLPackage.createPackage();
            RFonts cjk = new RFonts();
            cjk.setAscii("Microsoft YaHei");
            cjk.setHAnsi("Microsoft YaHei");
            cjk.setEastAsia("Microsoft YaHei");
            XHTMLImporterImpl.addFontMapping(FONT_FAMILY, cjk);
            XHTMLImporterImpl importer = new XHTMLImporterImpl(word);
            word.getMainDocumentPart().getContent().addAll(importer.convert(html, null));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            word.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Word conversion failed: " + e.getMessage(), e);
        }
    }

    byte[] pdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            this.registerFont(builder);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PDF conversion failed: " + e.getMessage(), e);
        }
    }

    private void registerFont(PdfRendererBuilder builder) throws IOException {
        for (Path font : this.fonts) {
            if (!Files.isRegularFile(font)) {
                continue;
            }
            if (!font.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ttf")) {
                continue; // openhtmltopdf embeds TrueType files; collections (.ttc) and CFF OpenType are skipped
            }
            builder.useFont(font.toFile(), FONT_FAMILY);
            return;
        }
        this.logger.warn("No CJK font found in {}: Chinese text in PDF reports will be missing (set sysarch.reports.fonts)", this.fonts);
    }

    static int pages(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

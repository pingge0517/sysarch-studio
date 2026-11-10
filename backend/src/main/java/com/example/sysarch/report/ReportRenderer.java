package com.example.sysarch.report;

import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.extend.FSCacheEx;
import com.openhtmltopdf.extend.FSCacheValue;
import com.openhtmltopdf.pdfboxout.PdfBoxFontResolver;
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
import org.docx4j.convert.in.xhtml.renderer.DocxRenderer;
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

    static {
        // docx4j keeps font mappings in a static map: set once, never while conversions run.
        RFonts cjk = new RFonts();
        cjk.setAscii("Microsoft YaHei");
        cjk.setHAnsi("Microsoft YaHei");
        cjk.setEastAsia("Microsoft YaHei");
        XHTMLImporterImpl.addFontMapping(FONT_FAMILY, cjk);
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

    /**
     * HTML, then Word and PDF at the same time (P2 T7: they are independent; the 10k SRS takes about as long as its Word
     * alone instead of both).
     */
    public Rendered render(String templateId, Map<String, Object> context) {
        String html = this.html(templateId, context);
        java.util.concurrent.CompletableFuture<byte[]> pdf = java.util.concurrent.CompletableFuture.supplyAsync(() -> this.pdf(html));
        byte[] docx = this.docx(html);
        try {
            byte[] pdfBytes = pdf.join();
            return new Rendered(html, docx, pdfBytes, pages(pdfBytes));
        } catch (java.util.concurrent.CompletionException e) {
            throw e.getCause() instanceof RuntimeException cause ? cause : e;
        }
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

    public byte[] docx(String html) {
        try {
            WordprocessingMLPackage word = WordprocessingMLPackage.createPackage();
            XHTMLImporterImpl importer = new XHTMLImporterImpl(word);
            // The importer lays the HTML out with openhtmltopdf. With only its default Type 1 fonts, measuring Chinese
            // text throws (and is caught) for every word: 62 s for the 10k SRS. With the CJK font registered, it measures.
            // docx4j's own resolver has no metrics cache (adding a font to it fails), so it gets one that has.
            DocxRenderer layout = new DocxRenderer();
            Path font = this.font();
            PDDocument fonts = new PDDocument();
            if (font != null) {
                PdfBoxFontResolver resolver = new PdfBoxFontResolver(layout.getSharedContext(), fonts, new MetricsCache(), PdfRendererBuilder.PdfAConformance.NONE,
                        false);
                resolver.addFont(font.toFile(), FONT_FAMILY, 400, IdentValue.NORMAL, false);
                layout.getSharedContext().setFontResolver(resolver);
            }
            importer.setRenderer(layout);
            try {
                word.getMainDocumentPart().getContent().addAll(importer.convert(html, null));
            } finally {
                fonts.close();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            word.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Word conversion failed: " + e.getMessage(), e);
        }
    }

    public byte[] pdf(String html) {
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

    private void registerFont(PdfRendererBuilder builder) {
        Path font = this.font();
        if (font != null) {
            builder.useFont(font.toFile(), FONT_FAMILY);
        } else {
            this.logger.warn("No CJK font found in {}: Chinese text in PDF reports will be missing (set sysarch.report.fonts)", this.fonts);
        }
    }

    /** The first existing TrueType file of the configured fonts (openhtmltopdf embeds neither .ttc nor CFF OpenType). */
    private Path font() {
        for (Path font : this.fonts) {
            if (Files.isRegularFile(font) && font.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ttf")) {
                return font;
            }
        }
        return null;
    }

    /** A plain map as openhtmltopdf's font metrics cache (per conversion). */
    private static final class MetricsCache implements FSCacheEx<String, FSCacheValue> {

        private final java.util.Map<String, FSCacheValue> values = new java.util.HashMap<>();

        @Override
        public void put(String key, FSCacheValue value) {
            this.values.put(key, value);
        }

        @Override
        public FSCacheValue get(String key, java.util.concurrent.Callable<? extends FSCacheValue> loader) {
            return this.values.computeIfAbsent(key, k -> {
                try {
                    return loader.call();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        @Override
        public FSCacheValue get(String key) {
            return this.values.get(key);
        }
    }

    static int pages(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

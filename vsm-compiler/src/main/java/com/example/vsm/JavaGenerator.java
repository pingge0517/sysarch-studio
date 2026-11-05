package com.example.vsm;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Renders the FreeMarker templates in {@code src/main/resources/templates}. The configuration is fixed and reads
 * nothing from the environment (locale, time zone, number format, line endings), so the same YAML always gives
 * byte-identical Java (spec §4.5, §11.2 #4).
 */
final class JavaGenerator {

    static final String PACKAGE = "com.example.sysarch.view.generated";

    private final Configuration freemarker;

    JavaGenerator() {
        this.freemarker = new Configuration(Configuration.VERSION_2_3_34);
        this.freemarker.setClassForTemplateLoading(JavaGenerator.class, "/templates");
        this.freemarker.setDefaultEncoding("UTF-8");
        this.freemarker.setLocale(Locale.ROOT);
        this.freemarker.setTimeZone(TimeZone.getTimeZone("UTC"));
        this.freemarker.setNumberFormat("computer");
        this.freemarker.setBooleanFormat("c");
        this.freemarker.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        this.freemarker.setLogTemplateExceptions(false);
        this.freemarker.setWrapUncheckedExceptions(true);
        this.freemarker.setFallbackOnNullLoopVariable(false);
    }

    /** File name to content, in a stable order: one provider per diagram, then the registry. */
    Map<String, String> generate(ViewModel model) throws IOException, TemplateException {
        Map<String, String> files = new LinkedHashMap<>();
        for (ViewModel.Diagram diagram : model.diagrams()) {
            files.put(diagram.className() + ".java", this.render("DiagramDescriptionProvider.ftl", Map.of("package", PACKAGE, "d", diagram)));
        }
        List<ViewModel.Diagram> diagrams = model.diagrams();
        files.put("ViewpointRegistry.java", this.render("ViewpointRegistry.ftl",
                Map.of("package", PACKAGE, "viewpoints", model.viewpoints(), "diagrams", diagrams)));
        return files;
    }

    private String render(String templateName, Map<String, Object> data) throws IOException, TemplateException {
        Template template = this.freemarker.getTemplate(templateName);
        StringWriter out = new StringWriter();
        template.process(data, out);
        return out.toString().replace("\r\n", "\n");
    }
}

package com.example.vsm;

import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.yaml.snakeyaml.error.MarkedYAMLException;

/**
 * VSM compiler (spec §4.5, T3): {@code views/*.vsm.yaml} to {@code <RepId>DiagramDescriptionProvider.java} and
 * {@code ViewpointRegistry.java}. It runs four fixed steps: parse the YAML, cross-check it against the metamodel
 * (after a JSON Schema check), render the templates, and write the registry.
 *
 * <p>Any error prints {@code views/<file>:<line> <message>} and exits with code 1, leaving the previous output
 * untouched. On success the output directory is deleted and rewritten in full.
 *
 * <p>Usage: {@code VsmCompiler <repoRoot>}, normally through {@code ./gradlew :vsm-compiler:generateViews}.
 */
public final class VsmCompiler {

    public static final int OK = 0;

    public static final int INVALID_VIEWS = 1;

    public static final int USAGE_OR_IO_ERROR = 2;

    static final String YAML_SUFFIX = ".vsm.yaml";

    /** Where inputs come from and output goes. {@code displayRoot} is what file names in messages are relative to. */
    public record Options(Path viewsDir, Path schemaFile, Path ecoreFile, Path outputDir, Path displayRoot) {

        public static Options forRepository(Path repoRoot) {
            Path root = repoRoot.toAbsolutePath().normalize();
            return new Options(root.resolve("views"), root.resolve("views/schema/vsm.schema.json"), root.resolve("metamodel/sysarch.ecore"),
                    root.resolve("backend/src/main/java/com/example/sysarch/view/generated"), root);
        }
    }

    private VsmCompiler() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: VsmCompiler <repoRoot>");
            System.exit(USAGE_OR_IO_ERROR);
        }
        System.exit(run(Options.forRepository(Path.of(args[0])), System.out, System.err));
    }

    /** Compiles all views; returns the process exit code. */
    public static int run(Options options, PrintStream out, PrintStream err) {
        try {
            List<Path> files = listViews(options.viewsDir());
            if (files.isEmpty()) {
                err.println("no *" + YAML_SUFFIX + " files in " + options.viewsDir());
                return USAGE_OR_IO_ERROR;
            }
            SchemaValidator schema = new SchemaValidator(options.schemaFile());
            Metamodel metamodel = Metamodel.load(options.ecoreFile());
            SemanticValidator semantics = new SemanticValidator(metamodel);
            List<Diagnostic> diagnostics = new ArrayList<>();
            Map<String, YamlNode> documents = new LinkedHashMap<>();

            for (Path path : files) {
                String file = display(options.displayRoot(), path);
                YamlNode document = parse(file, path, diagnostics);
                if (document == null) {
                    continue;
                }
                document.duplicateKeys().forEach(d -> diagnostics.add(new Diagnostic(file, d.getValue(), "duplicate key '" + d.getKey() + "'")));
                List<Diagnostic> schemaErrors = schema.validate(file, document);
                diagnostics.addAll(schemaErrors);
                if (schemaErrors.isEmpty()) {
                    semantics.check(file, document); // the metamodel check assumes a schema-valid shape
                    documents.put(file, document);
                }
            }
            diagnostics.addAll(semantics.diagnostics());

            if (!diagnostics.isEmpty()) {
                diagnostics.stream().sorted(Diagnostic.ORDER).forEach(err::println);
                err.println(diagnostics.size() + " error(s); nothing generated");
                return INVALID_VIEWS;
            }

            ViewModel model = new ViewModel();
            documents.forEach(model::add);
            Map<String, String> generated = new JavaGenerator().generate(model);
            write(options.outputDir(), generated);
            out.println("Generated " + generated.size() + " files into " + display(options.displayRoot(), options.outputDir()));
            return OK;
        } catch (IOException | UncheckedIOException | TemplateException e) {
            err.println("vsm-compiler failed: " + e.getMessage());
            return USAGE_OR_IO_ERROR;
        }
    }

    private static YamlNode parse(String file, Path path, List<Diagnostic> diagnostics) throws IOException {
        try {
            YamlNode document = YamlNode.parse(Files.readString(path, StandardCharsets.UTF_8));
            if (document == null || !document.isMapping()) {
                diagnostics.add(new Diagnostic(file, 1, "expected a YAML mapping at the top level"));
                return null;
            }
            return document;
        } catch (MarkedYAMLException e) {
            int line = e.getProblemMark() == null ? 1 : e.getProblemMark().getLine() + 1;
            diagnostics.add(new Diagnostic(file, line, "YAML syntax error: " + e.getProblem()));
            return null;
        }
    }

    private static List<Path> listViews(Path viewsDir) throws IOException {
        try (Stream<Path> stream = Files.list(viewsDir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(YAML_SUFFIX)).sorted().toList();
        }
    }

    private static void write(Path outputDir, Map<String, String> files) throws IOException {
        // Empty the directory rather than deleting it: Windows refuses to delete a directory that some process
        // (a shell, an IDE) has open, which would leave the output half-removed.
        Files.createDirectories(outputDir);
        try (Stream<Path> walk = Files.walk(outputDir)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(outputDir)).toList()) {
                Files.delete(path);
            }
        }
        for (Map.Entry<String, String> file : files.entrySet()) {
            Files.writeString(outputDir.resolve(file.getKey()), file.getValue(), StandardCharsets.UTF_8);
        }
    }

    private static String display(Path root, Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path relative = absolute.startsWith(root) ? root.relativize(absolute) : absolute;
        return relative.toString().replace('\\', '/');
    }
}

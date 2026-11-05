package com.example.sysarch.codegen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.emf.codegen.ecore.generator.Generator;
import org.eclipse.emf.codegen.ecore.generator.GeneratorAdapterFactory;
import org.eclipse.emf.codegen.ecore.genmodel.GenJDKLevel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelFactory;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelPackage;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.codegen.ecore.genmodel.GenRuntimeVersion;
import org.eclipse.emf.codegen.ecore.genmodel.generator.GenBaseGeneratorAdapter;
import org.eclipse.emf.codegen.ecore.genmodel.generator.GenModelGeneratorAdapterFactory;
import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.URIConverter;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;

/**
 * Standalone (no Eclipse runtime) EMF code generation for {@code metamodel/sysarch.ecore} (spec §3.3, T2).
 *
 * <p>Steps: build {@code metamodel/sysarch.genmodel} from the Ecore, delete the previous output, run the EMF
 * model and edit templates into {@code backend/src/main/java}, then move the edit layer's
 * {@code plugin.properties} and icons into {@code backend/src/main/resources}.
 *
 * <p>The output is deterministic: no timestamps, LF line endings, and no machine-specific settings (spec
 * §11.2 #4). Generated code is never edited by hand. Customizations belong in other packages.
 *
 * <p>Usage: {@code EmfCodeGenerator <repoRoot>}, normally through {@code ./gradlew :metamodel:generateModel}.
 */
public final class EmfCodeGenerator {

    /** Platform project name that EMF's output paths are resolved against. Mapped to {@code <repoRoot>/backend/}. */
    private static final String PROJECT = "backend";

    private static final String JAVA_DIR = "src/main/java";

    private static final String RESOURCES_DIR = "src/main/resources";

    private static final String BASE_PACKAGE = "com.example";

    /** EPackage {@code sysarch} + suffix {@code model} gives the spec's package {@code com.example.sysarch.model}. */
    private static final String MODEL_SUFFIX = "model";

    private static final String MODEL_PACKAGE_PATH = "com/example/sysarch/model";

    private EmfCodeGenerator() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: EmfCodeGenerator <repoRoot>");
            System.exit(2);
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path backend = root.resolve(PROJECT);
        Path ecoreFile = root.resolve("metamodel/sysarch.ecore");
        Path genModelFile = root.resolve("metamodel/sysarch.genmodel");

        URIConverter.URI_MAP.put(URI.createPlatformResourceURI("/" + PROJECT + "/", false),
                URI.createFileURI(backend.toString() + "/"));

        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("genmodel", new EcoreResourceFactoryImpl());
        resourceSet.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
        resourceSet.getPackageRegistry().put(GenModelPackage.eNS_URI, GenModelPackage.eINSTANCE);

        Resource ecoreResource = resourceSet.getResource(URI.createFileURI(ecoreFile.toString()), true);
        EcoreUtil.resolveAll(resourceSet);
        EPackage ePackage = (EPackage) ecoreResource.getContents().get(0);

        GenModel genModel = createGenModel(ePackage);
        Resource genModelResource = resourceSet.createResource(URI.createFileURI(genModelFile.toString()));
        genModelResource.getContents().add(genModel);
        genModelResource.save(Map.of(
                XMLResource.OPTION_ENCODING, "UTF-8",
                XMLResource.OPTION_LINE_DELIMITER, "\n"));

        cleanPreviousOutput(backend);

        Generator generator = new Generator();
        GeneratorAdapterFactory.Descriptor.Registry registry = generator.getAdapterFactoryDescriptorRegistry();
        if (registry.getDescriptors(GenModelPackage.eNS_URI).isEmpty()) {
            registry.addDescriptor(GenModelPackage.eNS_URI, GenModelGeneratorAdapterFactory.DESCRIPTOR);
        }
        genModel.setCanGenerate(true); // set by the Eclipse GenModel editor; must be explicit when headless
        generator.setInput(genModel);
        generator.getOptions().codeFormatting = false; // the JDT formatter would make output depend on the environment
        for (String projectType : List.of(GenBaseGeneratorAdapter.MODEL_PROJECT_TYPE, GenBaseGeneratorAdapter.EDIT_PROJECT_TYPE)) {
            Diagnostic diagnostic = generator.generate(genModel, projectType, new BasicMonitor.Printing(System.out));
            if (diagnostic.getSeverity() >= Diagnostic.ERROR) {
                throw new IllegalStateException("EMF generation failed for " + projectType + ": " + diagnostic);
            }
        }

        relocateNonJavaOutput(backend);
        normalizeLineEndings(backend.resolve(JAVA_DIR).resolve(MODEL_PACKAGE_PATH));
        normalizeLineEndings(backend.resolve(RESOURCES_DIR).resolve("plugin.properties"));
        System.out.println("Generated EMF model + edit code into " + backend.resolve(JAVA_DIR).resolve(MODEL_PACKAGE_PATH));
    }

    private static GenModel createGenModel(EPackage ePackage) {
        GenModel genModel = GenModelFactory.eINSTANCE.createGenModel();
        genModel.setModelName("Sysarch");
        genModel.setModelPluginID("com.example.sysarch");
        genModel.setEditPluginID("com.example.sysarch.edit");
        genModel.setModelDirectory("/" + PROJECT + "/" + JAVA_DIR);
        genModel.setEditDirectory("/" + PROJECT + "/" + JAVA_DIR);
        genModel.setEditPluginClass("com.example.sysarch.model.provider.SysarchEditPlugin");
        genModel.setImporterID("org.eclipse.emf.importer.ecore");
        genModel.getForeignModel().add("sysarch.ecore");
        genModel.setComplianceLevel(GenJDKLevel.JDK170_LITERAL);
        // Spec §2.1 targets EMF 2.35. Generated code uses only 2.35 APIs and runs on the newer
        // EMF runtime that Sirius Web 2025.8 ships (ecore 2.37 / edit 2.22).
        genModel.setRuntimeVersion(GenRuntimeVersion.EMF235);
        genModel.setLineDelimiter("\n");
        genModel.setCopyrightFields(false);
        genModel.setCodeFormatting(false);
        genModel.setBundleManifest(false);
        genModel.setUpdateClasspath(false);
        genModel.setOSGiCompatible(false);
        genModel.setCreationIcons(false);
        genModel.setOperationReflection(true);
        genModel.initialize(List.of(ePackage));

        GenPackage genPackage = genModel.getGenPackages().get(0);
        genPackage.setPrefix("Sysarch");
        genPackage.setBasePackage(BASE_PACKAGE);
        genPackage.setInterfacePackageSuffix(MODEL_SUFFIX);
        genPackage.setMetaDataPackageSuffix(MODEL_SUFFIX);
        genPackage.setClassPackageSuffix(MODEL_SUFFIX + ".impl");
        genPackage.setUtilityPackageSuffix(MODEL_SUFFIX + ".util");
        genPackage.setProviderPackageSuffix(MODEL_SUFFIX + ".provider");
        genPackage.setPresentationPackageSuffix(MODEL_SUFFIX + ".presentation");
        genPackage.setTestsPackageSuffix(MODEL_SUFFIX + ".tests");
        genPackage.setDisposableProviderFactory(true);
        return genModel;
    }

    /** Deletes everything a previous run produced, keeping the hand-written {@code package-info.java}. */
    private static void cleanPreviousOutput(Path backend) throws IOException {
        Path modelDir = backend.resolve(JAVA_DIR).resolve(MODEL_PACKAGE_PATH);
        for (String sub : List.of("impl", "util", "provider")) {
            deleteRecursively(modelDir.resolve(sub));
        }
        if (Files.isDirectory(modelDir)) {
            try (Stream<Path> files = Files.list(modelDir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java"))
                        .filter(f -> !f.getFileName().toString().equals("package-info.java")).toList()) {
                    Files.delete(file);
                }
            }
        }
        deleteRecursively(backend.resolve(RESOURCES_DIR).resolve("icons"));
        Files.deleteIfExists(backend.resolve(RESOURCES_DIR).resolve("plugin.properties"));
    }

    /**
     * EMF writes plugin.properties and icons/ to the project root, and build.properties/plugin.xml for Eclipse
     * plugins. Keep the first two as classpath resources, where {@code SysarchEditPlugin} looks them up, and drop
     * the Eclipse-only files.
     */
    private static void relocateNonJavaOutput(Path backend) throws IOException {
        Path resources = backend.resolve(RESOURCES_DIR);
        Files.createDirectories(resources);
        Path pluginProperties = backend.resolve("plugin.properties");
        if (Files.exists(pluginProperties)) {
            Files.move(pluginProperties, resources.resolve("plugin.properties"), StandardCopyOption.REPLACE_EXISTING);
        }
        Path icons = backend.resolve("icons");
        if (Files.isDirectory(icons)) {
            Files.move(icons, resources.resolve("icons"), StandardCopyOption.REPLACE_EXISTING);
        }
        for (String eclipseOnly : List.of("build.properties", "plugin.xml")) {
            Files.deleteIfExists(backend.resolve(eclipseOnly));
        }
        deleteRecursively(backend.resolve("META-INF"));
    }

    /**
     * EMF's template output ignores {@code GenModel.setLineDelimiter} and uses the platform separator
     * (CRLF on Windows, plus lone CRs for blank lines in plugin.properties). Rewrite every generated
     * text file to LF so the output is byte-identical on every OS (spec §11.2 #4).
     */
    private static void normalizeLineEndings(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path file : walk.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java") || f.toString().endsWith(".properties")).toList()) {
                String content = Files.readString(file, StandardCharsets.ISO_8859_1);
                String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
                if (!normalized.equals(content)) {
                    Files.writeString(file, normalized, StandardCharsets.ISO_8859_1);
                }
            }
        }
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

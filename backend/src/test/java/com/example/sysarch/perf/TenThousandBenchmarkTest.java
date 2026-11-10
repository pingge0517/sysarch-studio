package com.example.sysarch.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.baseline.ModelSnapshot;
import com.example.sysarch.baseline.ModelSnapshot.ElementState;
import com.example.sysarch.baseline.SnapshotModels;
import com.example.sysarch.merge.MergeEngine;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.report.ReportConfiguration;
import com.example.sysarch.report.ReportExtractor;
import com.example.sysarch.report.ReportRenderer;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.view.generated.AllocationMatrixTableDescriptionProvider;
import com.example.sysarch.view.generated.IbdDiagramDescriptionProvider;
import com.example.sysarch.view.projection.DiagramProjector;
import com.example.sysarch.view.sirius.DiagramFilterState;
import com.example.sysarch.view.sirius.ProjectionCache;
import com.example.sysarch.view.sirius.SiriusDiagramDescriptionFactory;
import com.example.sysarch.view.table.Matrix;
import com.example.sysarch.view.table.MatrixProjector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.components.DiagramComponent;
import org.eclipse.sirius.components.diagrams.components.DiagramComponentProps;
import org.eclipse.sirius.components.diagrams.description.DiagramDescription;
import org.eclipse.sirius.components.diagrams.renderer.DiagramRenderer;
import org.eclipse.sirius.components.representations.Element;
import org.eclipse.sirius.components.representations.VariableManager;
import org.eclipse.sirius.emfjson.resource.JsonResourceFactoryImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * P2 T7 benchmark (spec §6.1, T7 step 2): the 10,000-element fixture ({@link TenThousandModel}), timed in-process
 * through the same code the server runs: open the IBD (projection + Sirius' renderer, cold and warm), refresh it after
 * an edit, project the allocation matrix, generate the SRS (extract, HTML, Word, PDF), preview a merge of 200 changes.
 * Results in {@code build/perf/benchmark-10k.json}; {@code checkPerfThreshold} holds them to §6.1. The model is also
 * written as a Sirius JSON document ({@code build/perf/model-10k.json}) for the live check.
 */
class TenThousandBenchmarkTest {

    private static final ConstraintValidator VALIDATOR = new ConstraintValidator();

    private static final IIdentityService IDS = new IIdentityService() {
        @Override
        public String getId(Object object) {
            return object instanceof EObject element ? DiagramProjector.elementId(element) : null;
        }

        @Override
        @SuppressWarnings("removal")
        public String getKind(Object object) {
            return object instanceof EObject element ? "sysarch::" + element.eClass().getName() : "";
        }
    };

    private static final Map<String, Object> RESULTS = new LinkedHashMap<>();

    private static TenThousandModel fixture;

    private static Resource model;

    @BeforeAll
    static void fixture() throws IOException {
        fixture = TenThousandModel.build();
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("json", new JsonResourceFactoryImpl());
        Files.createDirectories(Path.of("build", "perf"));
        model = resourceSet.createResource(URI.createFileURI(Path.of("build", "perf", "model-10k.json").toAbsolutePath().toString()));
        model.getContents().addAll(fixture.contents);
        model.save(Map.of());
        RESULTS.put("elements", ModelSnapshot.capture(List.of(model)).size());
        // The live check builds the model through the SysML v2 import (a Sirius-native document; references in the
        // JSON file above are business ids, which Sirius' loader does not resolve).
        long start = System.nanoTime();
        String text = com.example.sysarch.export.SysmlV2Exporter.export("万元素基准", List.of(model), com.example.sysarch.export.SysmlV2Exporter.Scope.PROJECT,
                null, null);
        RESULTS.put("sysmlExportMs", ms(start));
        Files.writeString(Path.of("build", "perf", "model-10k.sysml"), text, StandardCharsets.UTF_8);
        start = System.nanoTime();
        var parsed = com.example.sysarch.sysmlimport.SysmlParser.parse(text);
        var mapped = com.example.sysarch.sysmlimport.SysmlMapper.map(parsed.root(), com.example.sysarch.model.Layer.LOGICAL);
        RESULTS.put("sysmlImportParseMapMs", ms(start));
        RESULTS.put("sysmlImportElements", mapped.total());
    }

    @AfterAll
    static void write() throws IOException {
        VALIDATOR.close();
        String json = RESULTS.entrySet().stream().map(e -> "  \"" + e.getKey() + "\": " + (e.getValue() instanceof Number n ? format(n) : "\"" + e.getValue() + "\""))
                .collect(Collectors.joining(",\n", "{\n", "\n}\n"));
        Files.writeString(Path.of("build", "perf", "benchmark-10k.json"), json, StandardCharsets.UTF_8);
    }

    @Test
    void theFixtureHasTenThousandElements() {
        assertTrue(((Number) RESULTS.get("elements")).intValue() >= 10_000, RESULTS.get("elements") + " elements");
        assertEquals(RESULTS.get("elements"), RESULTS.get("sysmlImportElements"), "the SysML round trip keeps every element");
    }

    /** §6.1 "1 万元素打开 IBD < 3s": first render with a fresh projection cache, then warm. Also a refresh after an edit. */
    @Test
    void openingTheIbdOfTheSystemBlock() {
        SiriusDiagramDescriptionFactory factory = new SiriusDiagramDescriptionFactory(SysarchPackage.eINSTANCE, IDS, new ProjectionCache(), new DiagramFilterState(),
                VALIDATOR);
        DiagramDescription ibd = factory.create(new IbdDiagramDescriptionProvider().build());

        long start = System.nanoTime();
        Diagram first = render(ibd, fixture.root, null);
        RESULTS.put("ibdOpenColdMs", ms(start));
        assertEquals(1, first.getNodes().size(), "the root frame");
        RESULTS.put("ibdNodes", count(first.getNodes()));

        double best = Double.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            SiriusDiagramDescriptionFactory fresh = new SiriusDiagramDescriptionFactory(SysarchPackage.eINSTANCE, IDS, new ProjectionCache(), new DiagramFilterState(),
                    VALIDATOR);
            DiagramDescription description = fresh.create(new IbdDiagramDescriptionProvider().build());
            long t = System.nanoTime();
            render(description, fixture.root, null);
            best = Math.min(best, ms(t));
        }
        RESULTS.put("ibdOpenWarmMs", best);

        Diagram previous = first;
        double worstRefresh = 0;
        for (int i = 0; i < 10; i++) {
            ((Part) fixture.root.getOwnedParts().get(i)).setName("子系统 " + i + " v2");
            long t = System.nanoTime();
            previous = render(ibd, fixture.root, previous);
            worstRefresh = Math.max(worstRefresh, ms(t));
        }
        RESULTS.put("ibdRefreshAfterEditMaxMs", worstRefresh);
        assertTrue(((Number) RESULTS.get("ibdOpenColdMs")).doubleValue() < 3000, RESULTS.toString());
    }

    /** §6.1 "分配矩阵 50×50 首屏 < 1.5s": the server part is the projection of the whole matrix of the 10k model. */
    @Test
    void projectingTheAllocationMatrix() {
        var table = new AllocationMatrixTableDescriptionProvider().build();
        long start = System.nanoTime();
        Matrix matrix = MatrixProjector.project(table, List.of(model));
        RESULTS.put("matrixColdMs", ms(start));
        double best = Double.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            long t = System.nanoTime();
            MatrixProjector.project(table, List.of(model));
            best = Math.min(best, ms(t));
        }
        RESULTS.put("matrixWarmMs", best);
        RESULTS.put("matrixRows", matrix.rows().size());
        RESULTS.put("matrixColumns", matrix.columns().size());
        RESULTS.put("matrixCells", matrix.cells().size());
    }

    /** §6.1 "报告生成（1 万元素 SRS）< 60s": rebuild from the snapshot (as a job does), extract, HTML, Word, PDF. */
    @Test
    void generatingTheSrs() {
        Map<String, ElementState> states = ModelSnapshot.capture(List.of(model));
        long start = System.nanoTime();
        Resource rebuilt = new ResourceImpl(URI.createURI("memory:/report.json"));
        rebuilt.getContents().addAll(SnapshotModels.build(states));
        long t0 = System.nanoTime();
        Map<String, Object> context = ReportExtractor.extract("srs", List.of(rebuilt),
                new ReportExtractor.Source("p", "万元素", "当前模型", "bench", "2026-11-10 12:00"));
        long t1 = System.nanoTime();
        ReportRenderer renderer = new ReportRenderer(ReportConfiguration.defaultFonts());
        String html = renderer.html("srs", context);
        long t2 = System.nanoTime();
        ReportRenderer.Rendered rendered = renderer.render("srs", context);
        long t5 = System.nanoTime();
        long t4 = t2;
        RESULTS.put("srsRebuildMs", (t0 - start) / 1e6);
        RESULTS.put("srsExtractMs", (t1 - t0) / 1e6);
        RESULTS.put("srsHtmlMs", (t2 - t1) / 1e6);
        RESULTS.put("srsRenderMs", (t5 - t4) / 1e6);
        RESULTS.put("srsTotalMs", (t1 - start + t5 - t4) / 1e6); // what a job does: rebuild, extract, render (HTML, then Word and PDF in parallel)
        RESULTS.put("srsPages", rendered.pages());
        RESULTS.put("srsHtmlKb", html.length() / 1024);
    }

    /** §6.1 "合并预演（200 变更）< 10s": two live snapshots of 10k elements and the three-way merge. */
    @Test
    void previewingAMergeOfTwoHundredChanges() {
        Map<String, ElementState> base = ModelSnapshot.capture(List.of(model));
        Resource branch = new ResourceImpl(URI.createURI("memory:/branch.json"));
        branch.getContents().addAll(SnapshotModels.build(base));
        branch.getContents().stream().filter(PartDefinition.class::isInstance).map(PartDefinition.class::cast).limit(200)
                .forEach(b -> b.setDescription("分支修改"));
        long start = System.nanoTime();
        Map<String, ElementState> theirs = ModelSnapshot.capture(List.of(branch));
        Map<String, ElementState> ours = ModelSnapshot.capture(List.of(model));
        MergeEngine.Result result = MergeEngine.merge(base, theirs, ours, MergeEngine.Strategy.POLARION_WINS, List.of());
        RESULTS.put("mergePreviewMs", ms(start));
        RESULTS.put("mergeOperations", result.operations().size());
        assertEquals(200, result.operations().size());
    }

    // ---------------------------------------------------------------- helpers

    private static Diagram render(DiagramDescription description, EObject root, Diagram previous) {
        VariableManager vm = new VariableManager();
        vm.put(VariableManager.SELF, root);
        vm.put(DiagramContext.DIAGRAM_CONTEXT, null);
        DiagramComponentProps props = DiagramComponentProps.newDiagramComponentProps().variableManager(vm).diagramDescription(description)
                .allDiagramDescriptions(List.of(description)).operationValidator((operation, variables) -> { }).viewCreationRequests(List.of())
                .viewDeletionRequests(List.of()).previousDiagram(Optional.ofNullable(previous)).diagramEvents(List.of()).nodeAppearanceHandlers(List.of())
                .edgeAppearanceHandlers(List.of()).build();
        return new DiagramRenderer().render(new Element(DiagramComponent.class, props));
    }

    private static int count(List<org.eclipse.sirius.components.diagrams.Node> nodes) {
        return nodes.stream().mapToInt(n -> 1 + count(n.getChildNodes()) + count(n.getBorderNodes())).sum();
    }

    private static double ms(long start) {
        return (System.nanoTime() - start) / 1e6;
    }

    private static String format(Number n) {
        return n instanceof Double d ? String.format(Locale.ROOT, "%.1f", d) : n.toString();
    }
}

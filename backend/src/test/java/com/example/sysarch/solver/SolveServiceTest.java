package com.example.sysarch.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.sysarch.identity.Editors;
import com.example.sysarch.model.ConstraintBlock;
import com.example.sysarch.model.Parameter;
import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.project.ProjectModels;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * P3 T4 end to end, without Sirius (the model's editing context replaced by a plain resource): P3-AC-2 the converged
 * value equals the hand calculation and is written to the parameter; P3-AC-3 an overdetermined system is 422 with the
 * reason; P3-AC-4 an injected {@code import os} is refused and no solver process ever starts; not converged or failing
 * solvers write nothing; an unconfigured solver is 503.
 */
class SolveServiceTest {

    private Resource model;

    private ProjectModels models;

    private final AtomicInteger solverCalls = new AtomicInteger();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void fixture() {
        this.model = new ResourceImpl(URI.createURI("memory:/solve.json"));
        this.models = mock(ProjectModels.class);
        when(this.models.read(anyString(), any(Function.class))).thenAnswer(i -> ((Function<List<Resource>, Object>) i.getArgument(1)).apply(List.of(this.model)));
        when(this.models.write(anyString(), any(Editors.Editor.class), any(Function.class)))
                .thenAnswer(i -> ((Function<List<Resource>, ProjectModels.Outcome<?>>) i.getArgument(2)).apply(List.of(this.model)).value());
        this.model.getContents().add(SolveSystemTest.block("CB-1", "功率", "P_out = P_in * eff", SolveSystemTest.param("P-1", "P_in", "W"),
                SolveSystemTest.param("P-2", "P_out", "W"), SolveSystemTest.param("P-3", "eff", "1")));
    }

    private SolveService service(ConstraintSolver solver) {
        return new SolveService(this.models, List.of(solver), null);
    }

    /** A solver that only counts calls and answers a fixed result. */
    private ConstraintSolver counting(ConstraintSolver.SolveResult answer) {
        return new ConstraintSolver() {
            @Override
            public String id() {
                return "python";
            }

            @Override
            public SolveResult solve(SolveRequest request) {
                SolveServiceTest.this.solverCalls.incrementAndGet();
                return answer;
            }
        };
    }

    private Parameter parameter(String id) {
        return ((ConstraintBlock) this.model.getContents().get(0)).getParameters().stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void theSolvedValueMatchesTheHandCalculationAndIsWrittenBack() {
        String python = System.getenv("SYSARCH_SOLVER_PYTHON") != null ? System.getenv("SYSARCH_SOLVER_PYTHON") : "C:\\SysArchSolver\\python\\python.exe";
        assumeTrue(Files.isRegularFile(Path.of(python)), "no Python with SciPy configured");
        SolveService service = this.service(new PythonSolver(python, Duration.ofSeconds(30), new ObjectMapper()));
        SolveService.Answer answer = service.solve("p", List.of("CB-1"), "python", Map.of("P-1", 100.0, "eff", 0.9), List.of("P-2"), 1e-9, 200,
                new Editors.Editor("alice"));
        assertTrue(answer.converged() && answer.written(), String.valueOf(answer));
        assertTrue(Math.abs(answer.outputs().get("P-2") - 90.0) < 1e-6, "P3-AC-2: 100 W × 0.9 = 90 W, " + answer);
        assertTrue(Math.abs(this.parameter("P-2").getDefaultValue() - 90.0) < 1e-6, "written back to Parameter.defaultValue");
        assertFalse(this.parameter("P-1").eIsSet(SysarchPackage.Literals.PARAMETER__DEFAULT_VALUE), "inputs are not written");
    }

    @Test
    void anOverdeterminedSystemIs422WithTheReasonAndNoSolverRuns() {
        ((ConstraintBlock) this.model.getContents().get(0)).getExpression().setBody("P_out = P_in * eff\nP_out = P_in");
        SolveService.Refused refused = assertThrows(SolveService.Refused.class, () -> this.service(this.counting(null)).solve("p", List.of("CB-1"), "python",
                Map.of("P_in", 100.0, "eff", 0.9), List.of("P_out"), 1e-6, 200, new Editors.Editor("alice")));
        assertEquals(422, refused.status());
        assertTrue(refused.reasons().get(0).startsWith("超定：2 个方程只有 1 个未知量"), refused.reasons().toString());
        assertEquals(0, this.solverCalls.get());
    }

    @Test
    void anUnderdeterminedSystemIs422() {
        SolveService.Refused refused = assertThrows(SolveService.Refused.class, () -> this.service(this.counting(null)).solve("p", List.of("CB-1"), "python",
                Map.of("P_in", 100.0), List.of("P_out", "eff"), 1e-6, 200, new Editors.Editor("alice")));
        assertTrue(refused.reasons().get(0).startsWith("欠定"), refused.reasons().toString());
    }

    @Test
    void anInjectedImportIsRefusedAndNoProcessStarts() {
        ((ConstraintBlock) this.model.getContents().get(0)).getExpression().setBody("import os");
        SolveService.Refused refused = assertThrows(SolveService.Refused.class, () -> this.service(this.counting(null)).solve("p", List.of("CB-1"), "python",
                Map.of("P_in", 100.0, "eff", 0.9), List.of("P_out"), 1e-6, 200, new Editors.Editor("mallory")));
        assertEquals(422, refused.status());
        assertTrue(refused.reasons().get(0).contains("不允许的关键字「import」"), refused.reasons().toString());
        assertEquals(0, this.solverCalls.get(), "P3-AC-4: refused before any solver runs");
    }

    @Test
    void notASolutionWritesNothing() {
        SolveService service = this.service(this.counting(new ConstraintSolver.SolveResult(true, Map.of("v1", 1.0), Map.of("v1", 90.0), 1, "gtol")));
        SolveService.Answer answer = service.solve("p", List.of("CB-1"), "python", Map.of("P_in", 100.0, "eff", 0.9), List.of("P_out"), 1e-6, 200,
                new Editors.Editor("alice"));
        assertFalse(answer.converged() || answer.written());
        assertTrue(answer.solverLog().contains("残差不为零"), answer.solverLog());
        assertFalse(this.parameter("P-2").eIsSet(SysarchPackage.Literals.PARAMETER__DEFAULT_VALUE), "nothing written");
    }

    @Test
    void aMissingBlockOrUnknownParameterOrUnconfiguredSolverIsRefused() {
        SolveService service = this.service(this.counting(null));
        assertTrue(assertThrows(SolveService.Refused.class, () -> service.solve("p", List.of("CB-9"), "python", Map.of(), List.of("P_out"), 1e-6, 200,
                new Editors.Editor("a"))).reasons().get(0).contains("模型里没有约束块 CB-9"));
        assertTrue(assertThrows(SolveService.Refused.class, () -> service.solve("p", List.of("CB-1"), "python", Map.of("Q", 1.0), List.of("P_out"), 1e-6, 200,
                new Editors.Editor("a"))).reasons().contains("所选约束块里没有参数 Q"));
        SolveService.Refused unconfigured = assertThrows(SolveService.Refused.class, () -> service.solve("p", List.of("CB-1"), "modelica", Map.of(),
                List.of("P_out"), 1e-6, 200, new Editors.Editor("a")));
        assertEquals(503, unconfigured.status());
        assertTrue(unconfigured.reasons().get(0).contains("SYSARCH_SOLVER_OMC"));
        when(this.models.read(eq("gone"), any())).thenThrow(new ProjectModels.UnknownProjectException("no Sirius project 'gone'"));
        assertThrows(ProjectModels.UnknownProjectException.class, () -> service.solve("gone", List.of("CB-1"), "python", Map.of(), List.of("P_out"), 1e-6, 200,
                new Editors.Editor("a")));
    }
}

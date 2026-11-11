package com.example.sysarch.solver;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * P3 T4: the three solvers. The Python ones run the spec's script for real when a Python with SciPy is configured
 * ({@code SYSARCH_SOLVER_PYTHON}, else {@code C:\SysArchSolver\python\python.exe}; CI installs SciPy), and are skipped
 * otherwise. P3-AC-2: the result equals the hand calculation within 1e-6; P3-AC-4: code that escapes the scan still
 * cannot import or open anything (empty builtins), and nothing on disk changes.
 */
class SolversTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static String python() {
        String configured = System.getenv("SYSARCH_SOLVER_PYTHON");
        String path = configured != null && !configured.isBlank() ? configured : "C:\\SysArchSolver\\python\\python.exe";
        return Files.isRegularFile(Path.of(path)) ? path : null;
    }

    private static PythonSolver pythonSolver(Duration timeout) {
        String python = python();
        assumeTrue(python != null, "no Python with SciPy configured (SYSARCH_SOLVER_PYTHON)");
        return new PythonSolver(python, timeout, JSON);
    }

    // ------------------------------------------------------------------ Python (the spec's script)

    @Test
    void oneEquationMatchesTheHandCalculation() {
        ConstraintSolver.SolveResult result = pythonSolver(Duration.ofSeconds(30))
                .solve(new ConstraintSolver.SolveRequest("_r0 = (v1) - (v0 * v2)\nv1 = _r0\n", "", Map.of("v0", 100.0, "v2", 0.9), List.of("v1"), 1e-9, 200));
        assertTrue(Math.abs(result.outputs().get("v1") - 90.0) < 1e-6, String.valueOf(result));
        assertTrue(SolveSystem.accept(result, 1e-9));
    }

    @Test
    void twoEquationsTwoUnknowns() {
        // x + y = 10, x - y = 2  ->  x = 6, y = 4
        ConstraintSolver.SolveResult result = pythonSolver(Duration.ofSeconds(30)).solve(new ConstraintSolver.SolveRequest(
                "_r0 = (v0 + v1) - (10)\n_r1 = (v0 - v1) - (2)\nv0 = _r0\nv1 = _r1\n", "", Map.of(), List.of("v0", "v1"), 1e-9, 200));
        assertTrue(Math.abs(result.outputs().get("v0") - 6) < 1e-6 && Math.abs(result.outputs().get("v1") - 4) < 1e-6, String.valueOf(result));
    }

    @Test
    void anInconsistentSystemIsNotASolutionThoughTheScriptSaysConverged() {
        // x * 0 = 5 has no solution; least_squares stops at its minimum and reports success
        ConstraintSolver.SolveResult result = pythonSolver(Duration.ofSeconds(30))
                .solve(new ConstraintSolver.SolveRequest("_r0 = (v0 * 0) - (5)\nv0 = _r0\n", "", Map.of(), List.of("v0"), 1e-9, 200));
        assertFalse(SolveSystem.accept(result, 1e-9), String.valueOf(result));
    }

    @Test
    void codeThatEscapedTheScanStillCannotImportOrOpen(@org.junit.jupiter.api.io.TempDir Path dir) throws IOException {
        PythonSolver solver = pythonSolver(Duration.ofSeconds(30));
        Path victim = dir.resolve("pwned.txt");
        String path = victim.toString().replace("\\", "/");
        for (String evil : List.of("import os\nv0 = 1", "v0 = __import__('os').getcwd()", "f = open('" + path + "', 'w')\nv0 = 1",
                "v0 = eval('1')")) {
            assertThrows(ConstraintSolver.SolverFailure.class, () -> solver.solve(new ConstraintSolver.SolveRequest(evil, "", Map.of(), List.of("v0"), 1e-6, 10)),
                    "no builtins: " + evil);
        }
        assertFalse(Files.exists(victim), "nothing was written");
    }

    @Test
    void theTimeLimitKillsTheProcess() {
        PythonSolver solver = pythonSolver(Duration.ofMillis(1));
        ConstraintSolver.SolverTimeout e = assertThrows(ConstraintSolver.SolverTimeout.class,
                () -> solver.solve(new ConstraintSolver.SolveRequest("_r0 = (v0) - (1)\nv0 = _r0\n", "", Map.of(), List.of("v0"), 1e-6, 200)));
        assertTrue(e.getMessage().startsWith("求解超时"), e.getMessage());
    }

    @Test
    void notConfiguredIsNotAvailable() {
        assertFalse(new PythonSolver("", Duration.ofSeconds(1), JSON).available());
        assertFalse(new RemoteHttpSolver("", Duration.ofSeconds(1), JSON).available());
        assertFalse(new ModelicaSolver("C:/nowhere/omc.exe", Duration.ofSeconds(1)).available());
    }

    // ------------------------------------------------------------------ remote

    @Test
    void theRemoteSolverSpeaksTheScriptsJson() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        try {
            server.stubFor(post(urlEqualTo("/solve")).withRequestBody(equalToJson("{\"expression\":\"_r0 = (v0) - (3)\\nv0 = _r0\\n\",\"inputs\":{},\"targets\":[\"v0\"],"
                    + "\"options\":{\"tolerance\":1.0E-6,\"maxIterations\":200}}")).willReturn(aResponse().withHeader("Content-Type", "application/json")
                    .withBody("{\"converged\": true, \"outputs\": {\"v0\": 3.0}, \"residuals\": {\"v0\": 0.0}, \"iterations\": 4, \"solverLog\": \"ok\"}")));
            ConstraintSolver.SolveResult result = new RemoteHttpSolver(server.baseUrl() + "/solve", Duration.ofSeconds(5), JSON)
                    .solve(new ConstraintSolver.SolveRequest("_r0 = (v0) - (3)\nv0 = _r0\n", "", Map.of(), List.of("v0"), 1e-6, 200));
            assertEquals(3.0, result.outputs().get("v0"));
            server.stubFor(post(urlEqualTo("/slow")).willReturn(aResponse().withStatus(504)));
            assertThrows(ConstraintSolver.SolverTimeout.class, () -> new RemoteHttpSolver(server.baseUrl() + "/slow", Duration.ofSeconds(5), JSON)
                    .solve(new ConstraintSolver.SolveRequest("v0 = 1", "", Map.of(), List.of("v0"), 1e-6, 200)));
        } finally {
            server.stop();
        }
    }

    /** solver/http_server.py for real: it solves, and it refuses an expression that is not arithmetic (422) itself. */
    @Test
    void theHttpSolverServiceSolvesAndRefusesCode() throws Exception {
        String python = python();
        assumeTrue(python != null, "no Python with SciPy configured");
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Path server = Path.of("..", "solver", "http_server.py").toAbsolutePath().normalize();
        ProcessBuilder builder = new ProcessBuilder(python, "-I", server.toString()).redirectErrorStream(true);
        builder.environment().put("SOLVER_PORT", Integer.toString(port));
        Process process = builder.start();
        try {
            HttpClient http = HttpClient.newHttpClient();
            for (int i = 0; i < 50; i++) {
                try {
                    http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health")).build(), HttpResponse.BodyHandlers.discarding());
                    break;
                } catch (IOException e) {
                    Thread.sleep(100);
                }
            }
            RemoteHttpSolver remote = new RemoteHttpSolver("http://127.0.0.1:" + port + "/", Duration.ofSeconds(30), JSON);
            ConstraintSolver.SolveResult result = remote.solve(new ConstraintSolver.SolveRequest("_r0 = (v1) - (v0 * v2)\nv1 = _r0\n", "",
                    Map.of("v0", 100.0, "v2", 0.9), List.of("v1"), 1e-9, 200));
            assertTrue(Math.abs(result.outputs().get("v1") - 90.0) < 1e-6, String.valueOf(result));
            HttpResponse<String> refused = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"expression\":\"import os\",\"inputs\":{},\"targets\":[\"v0\"],\"options\":{\"tolerance\":1e-6,"
                            + "\"maxIterations\":10}}", StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(422, refused.statusCode(), refused.body());
        } finally {
            process.destroyForcibly();
        }
    }

    // ------------------------------------------------------------------ Modelica (OpenModelica not installed here)

    @Test
    void theModelicaScriptAndItsOutput() {
        assertEquals("loadFile(\"SysArchSolve.mo\"); getErrorString();\nsimulate(SysArchSolve, startTime=0, stopTime=0, numberOfIntervals=1); getErrorString();\n"
                + "print(\"SYSARCH v1=\" + String(val(v1, 0.0)) + \"\\n\");\n", ModelicaSolver.script(List.of("v1")));
        assertEquals(Map.of("v1", 90.0), ModelicaSolver.parse("record SimulationResult …\nSYSARCH v1=90.0\n"));
        assertEquals(Map.of(), ModelicaSolver.parse("SYSARCH v1=NaN?\n"));
    }
}

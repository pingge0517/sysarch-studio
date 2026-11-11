package com.example.sysarch.solver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code modelica}: OpenModelica (P3 T4). The system goes in as a static Modelica model ({@link SolveSystem#modelica});
 * a {@code .mos} script loads it, simulates it for zero time and prints every target's value, which OpenModelica
 * computes with its own nonlinear solver. Not installed on every machine: without {@code sysarch.solver.omc} it is not
 * {@link #available()} (503). Residuals are recomputed by nobody here: OpenModelica fails rather than returning a
 * non-solution, so a finished run is converged and its residuals are reported as 0.
 */
public class ModelicaSolver implements ConstraintSolver {

    private static final Pattern VALUE = Pattern.compile("^SYSARCH (\\w+)=(\\S+)$", Pattern.MULTILINE);

    private final String omc;

    private final Duration timeout;

    public ModelicaSolver(String omc, Duration timeout) {
        this.omc = omc == null ? "" : omc.strip();
        this.timeout = timeout;
    }

    @Override
    public String id() {
        return "modelica";
    }

    @Override
    public boolean available() {
        return !this.omc.isEmpty() && Files.isRegularFile(Path.of(this.omc));
    }

    /** The {@code .mos} script: load, simulate for zero time, print {@code SYSARCH name=value} per target. */
    static String script(List<String> targets) {
        StringBuilder mos = new StringBuilder("loadFile(\"SysArchSolve.mo\"); getErrorString();\n");
        mos.append("simulate(SysArchSolve, startTime=0, stopTime=0, numberOfIntervals=1); getErrorString();\n");
        for (String target : targets) {
            mos.append("print(\"SYSARCH ").append(target).append("=\" + String(val(").append(target).append(", 0.0)) + \"\\n\");\n");
        }
        return mos.toString();
    }

    @Override
    public SolveResult solve(SolveRequest request) {
        Path work = null;
        try {
            work = Files.createTempDirectory("sysarch-omc-");
            Files.writeString(work.resolve("SysArchSolve.mo"), request.modelica(), StandardCharsets.UTF_8);
            Files.writeString(work.resolve("solve.mos"), script(request.targets()), StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(this.omc, "solve.mos").directory(work.toFile()).redirectErrorStream(true).start();
            if (!process.waitFor(this.timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw new SolverTimeout("求解超时：OpenModelica " + this.timeout.toSeconds() + " 秒内没有结果，未写回任何值");
            }
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Double> outputs = parse(out);
            if (!outputs.keySet().containsAll(request.targets())) {
                return new SolveResult(false, outputs, Map.of(), 0, out.strip());
            }
            Map<String, Double> residuals = new LinkedHashMap<>();
            request.targets().forEach(t -> residuals.put(t, 0.0));
            return new SolveResult(true, outputs, residuals, 1, "OpenModelica");
        } catch (IOException e) {
            throw new SolverFailure("无法运行 OpenModelica：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SolverFailure("求解被中断");
        } finally {
            if (work != null) {
                try (var files = Files.walk(work)) {
                    files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                } catch (IOException e) {
                    // temporary
                }
            }
        }
    }

    static Map<String, Double> parse(String out) {
        Map<String, Double> values = new LinkedHashMap<>();
        Matcher m = VALUE.matcher(out);
        while (m.find()) {
            try {
                values.put(m.group(1), Double.parseDouble(m.group(2)));
            } catch (NumberFormatException e) {
                // a value OpenModelica could not compute: left out, so the run is not converged
            }
        }
        return values;
    }
}

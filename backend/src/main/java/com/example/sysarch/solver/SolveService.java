package com.example.sysarch.solver;

import com.example.sysarch.ElementIds;
import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.identity.Editors;
import com.example.sysarch.model.BindingConnector;
import com.example.sysarch.model.ConstraintBlock;
import com.example.sysarch.model.Parameter;
import com.example.sysarch.project.ProjectModels;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * {@code POST /api/v1/solve} (P3 T4, spec §3.3): reads the chosen constraint blocks on the project's editing context,
 * builds the system ({@link SolveSystem}: units, bindings, scanned equations, over/under-determined), runs the chosen
 * solver outside the editing context, judges the answer ({@link SolveSystem#accept}) and, when it is a solution, writes
 * the targets' values to {@code Parameter.defaultValue} as the requesting user (locks and roles apply, as for any edit).
 * Not a solution: nothing is written. Every run is audited ({@code SOLVE}).
 *
 * <p>Inputs and targets name parameters by element id, or by name when that name is unique among the chosen blocks.
 */
public class SolveService {

    /** A request the model cannot answer (404 / 422), with the reasons. */
    public static class Refused extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int status;

        private final List<String> reasons;

        public Refused(int status, List<String> reasons) {
            super(String.join("；", reasons));
            this.status = status;
            this.reasons = List.copyOf(reasons);
        }

        public int status() {
            return this.status;
        }

        public List<String> reasons() {
            return this.reasons;
        }
    }

    /** The answer, keyed as the request named the targets. */
    public record Answer(boolean converged, Map<String, Double> outputs, Map<String, Double> residuals, int iterations, String solverLog, boolean written) {
    }

    private record Prepared(ConstraintSolver.SolveRequest request, Map<String, String> keyOfVariable, Map<String, List<String>> parametersOfVariable) {
    }

    private final ProjectModels models;

    private final Map<String, ConstraintSolver> solvers;

    private final AuditLog audit;

    public SolveService(ProjectModels models, List<ConstraintSolver> solvers, AuditLog audit) {
        this.models = Objects.requireNonNull(models);
        this.solvers = solvers.stream().collect(Collectors.toMap(ConstraintSolver::id, s -> s, (a, b) -> a, LinkedHashMap::new));
        this.audit = audit;
    }

    public Answer solve(String project, List<String> blockIds, String solverId, Map<String, Double> inputs, List<String> targets, double tolerance,
            int maxIterations, Editors.Editor editor) {
        ConstraintSolver solver = this.solvers.get(solverId);
        if (solver == null || !solver.available()) {
            throw new Refused(503, List.of("求解器 " + solverId + " 未配置（" + switch (solverId) {
                case "python" -> "SYSARCH_SOLVER_PYTHON";
                case "modelica" -> "SYSARCH_SOLVER_OMC";
                default -> "SYSARCH_SOLVER_REMOTE_URL";
            } + "）"));
        }
        if (blockIds == null || blockIds.isEmpty() || targets == null || targets.isEmpty()) {
            throw new Refused(422, List.of("constraintBlockIds 和 targets 都不能为空"));
        }
        Prepared prepared;
        try {
            prepared = this.models.read(project, resources -> prepare(resources, blockIds, inputs == null ? Map.of() : inputs, targets, tolerance, maxIterations));
        } catch (SolveSystem.Unsolvable e) {
            this.audit(project, editor, solverId, blockIds, "REFUSED " + e.getMessage());
            throw new Refused(422, e.reasons());
        }
        ConstraintSolver.SolveResult result;
        try {
            result = solver.solve(prepared.request());
        } catch (ConstraintSolver.SolverFailure e) {
            this.audit(project, editor, solverId, blockIds, "FAILED " + e.getMessage());
            throw new Refused(422, List.of(e.getMessage()));
        } catch (ConstraintSolver.SolverTimeout e) {
            this.audit(project, editor, solverId, blockIds, "TIMEOUT");
            throw e;
        }
        boolean solution = SolveSystem.accept(result, tolerance);
        Map<String, Double> outputs = new LinkedHashMap<>();
        Map<String, Double> residuals = new LinkedHashMap<>();
        prepared.keyOfVariable().forEach((variable, key) -> {
            if (result.outputs().containsKey(variable)) {
                outputs.put(key, result.outputs().get(variable));
            }
            if (result.residuals().containsKey(variable)) {
                residuals.put(key, result.residuals().get(variable));
            }
        });
        if (solution) {
            Map<String, Double> values = new LinkedHashMap<>();
            prepared.parametersOfVariable().forEach((variable, parameterIds) -> parameterIds.forEach(id -> values.put(id, result.outputs().get(variable))));
            try {
                this.models.write(project, editor, resources -> {
                    Map<String, Parameter> byId = parameters(resources);
                    values.forEach((id, value) -> byId.get(id).setDefaultValue(value));
                    return new ProjectModels.Outcome<>(null, !values.isEmpty());
                });
            } catch (RuntimeException refused) {
                // solved, but the edit was refused (someone else's lock 423, the editor limit 429, the role 403): nothing written
                this.audit(project, editor, solverId, blockIds, "SOLVED_NOT_WRITTEN " + refused.getMessage());
                throw refused;
            }
        }
        this.audit(project, editor, solverId, blockIds, (solution ? "SOLVED " : "NOT_CONVERGED ") + outputs);
        String log = result.log() + (result.converged() && !solution ? "（求解器报告结束，但残差不为零：方程组在给定输入下无解，未写回）" : "");
        return new Answer(solution, outputs, residuals, result.iterations(), log, solution);
    }

    private static Prepared prepare(List<Resource> resources, List<String> blockIds, Map<String, Double> inputs, List<String> targets, double tolerance,
            int maxIterations) {
        Map<String, ConstraintBlock> blocksById = new LinkedHashMap<>();
        List<BindingConnector> bindings = new ArrayList<>();
        EcoreUtil.getAllContents(resources, true).forEachRemaining(o -> {
            if (o instanceof ConstraintBlock block) {
                blocksById.put(ElementIds.of(block), block);
            } else if (o instanceof BindingConnector binding) {
                bindings.add(binding);
            }
        });
        List<String> missing = blockIds.stream().filter(id -> !blocksById.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new SolveSystem.Unsolvable(List.of("模型里没有约束块 " + String.join("、", missing)));
        }
        List<ConstraintBlock> blocks = blockIds.stream().distinct().map(blocksById::get).toList();
        SolveSystem system = SolveSystem.build(blocks, bindings);
        List<Parameter> chosen = blocks.stream().flatMap(b -> b.getParameters().stream()).toList();
        List<String> problems = new ArrayList<>();
        Map<SolveSystem.Variable, Double> known = new LinkedHashMap<>();
        inputs.forEach((key, value) -> {
            Parameter p = resolve(chosen, key, problems);
            if (p != null) {
                known.put(system.variableOf(p), value);
            }
        });
        List<SolveSystem.Variable> unknown = new ArrayList<>();
        Map<String, String> keyOfVariable = new LinkedHashMap<>();
        for (String key : targets) {
            Parameter p = resolve(chosen, key, problems);
            if (p != null && !unknown.contains(system.variableOf(p))) {
                unknown.add(system.variableOf(p));
                keyOfVariable.put(system.variableOf(p).name(), key);
            }
        }
        if (!problems.isEmpty()) {
            throw new SolveSystem.Unsolvable(problems);
        }
        ConstraintSolver.SolveRequest request = system.request(known, unknown, tolerance, maxIterations);
        Map<String, List<String>> parametersOfVariable = new LinkedHashMap<>();
        unknown.forEach(v -> parametersOfVariable.put(v.name(), v.parameters().stream().map(ElementIds::of).toList()));
        return new Prepared(request, keyOfVariable, parametersOfVariable);
    }

    /** A parameter of the chosen blocks by element id, or by its name when unique. */
    private static Parameter resolve(List<Parameter> chosen, String key, List<String> problems) {
        List<Parameter> byId = chosen.stream().filter(p -> key.equals(ElementIds.of(p))).toList();
        if (byId.size() == 1) {
            return byId.get(0);
        }
        List<Parameter> byName = chosen.stream().filter(p -> key.equals(p.getName())).toList();
        if (byName.size() == 1) {
            return byName.get(0);
        }
        problems.add(byName.isEmpty() ? "所选约束块里没有参数 " + key : "参数名 " + key + " 不唯一（" + byName.size() + " 个），请用参数的元素 ID");
        return null;
    }

    private static Map<String, Parameter> parameters(List<Resource> resources) {
        Map<String, Parameter> byId = new LinkedHashMap<>();
        EcoreUtil.getAllContents(resources, true).forEachRemaining(o -> {
            if (o instanceof Parameter p) {
                byId.put(ElementIds.of((EObject) p), p);
            }
        });
        return byId;
    }

    private void audit(String project, Editors.Editor editor, String solver, List<String> blocks, String outcome) {
        if (this.audit != null) {
            this.audit.append(project, editor.user(), "SOLVE", null, solver + " " + String.join(",", blocks) + ": " + outcome);
        }
    }
}

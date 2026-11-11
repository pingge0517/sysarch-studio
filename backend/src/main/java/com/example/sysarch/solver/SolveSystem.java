package com.example.sysarch.solver;

import com.example.sysarch.ElementIds;
import com.example.sysarch.model.BindingConnector;
import com.example.sysarch.model.ConstraintBlock;
import com.example.sysarch.model.Parameter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The equation system of a set of constraint blocks (P3 T4), built from the model before any solver runs; pure.
 * <ul>
 * <li>Every parameter's unit must be on the whitelist ({@link Units}), else 422 before solving (T4 step 2).</li>
 * <li>A binding between two parameters of the chosen blocks makes them one variable ({@code v0}, {@code v1}…);
 * bindings to blocks not chosen are ignored.</li>
 * <li>A block's expression is one equation per line (or {@code ;}), {@code lhs = rhs}, over its own parameter names;
 * each is scanned by {@link ExpressionSafety} and rewritten to the variables.</li>
 * <li>Knowns: the request's inputs, else a parameter's {@code defaultValue}; unknowns: the targets. Equations ≠
 * unknowns → 422 "超定" / "欠定" (spec §3.3), before solving.</li>
 * </ul>
 * The spec's Python script (kept verbatim) takes the expression as code that leaves one residual per target in the
 * target's own name, so {@link #request} writes each equation as {@code _r<i> = (lhs) - (rhs)} and then assigns the
 * residuals to the targets. Its {@code converged} flag alone is not trusted ({@link #accept}).
 */
public final class SolveSystem {

    /** 422: the system cannot be given to a solver; the reasons are for the user. */
    public static final class Unsolvable extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final List<String> reasons;

        public Unsolvable(List<String> reasons) {
            super(String.join("；", reasons));
            this.reasons = List.copyOf(reasons);
        }

        public List<String> reasons() {
            return this.reasons;
        }
    }

    /** One equation, rewritten to variables: {@code lhs - (rhs)} is its residual. */
    public record Equation(String blockId, String text, String lhs, String rhs) {
    }

    /** A variable: the parameters it stands for (bound together), with their element ids. */
    public record Variable(String name, List<Parameter> parameters) {

        public String label() {
            return this.parameters.stream().map(p -> p.getName() + "（" + ElementIds.of(p) + "）").collect(Collectors.joining(" = "));
        }
    }

    private final List<Equation> equations;

    private final Map<Parameter, Variable> variableOf;

    private final List<Variable> variables;

    private SolveSystem(List<Equation> equations, Map<Parameter, Variable> variableOf, List<Variable> variables) {
        this.equations = equations;
        this.variableOf = variableOf;
        this.variables = variables;
    }

    public List<Equation> equations() {
        return this.equations;
    }

    public List<Variable> variables() {
        return this.variables;
    }

    /** The variable of a parameter of the chosen blocks. */
    public Variable variableOf(Parameter parameter) {
        return this.variableOf.get(parameter);
    }

    public static SolveSystem build(List<ConstraintBlock> blocks, Collection<BindingConnector> bindings) {
        List<String> problems = new ArrayList<>();
        Map<Parameter, Parameter> parent = new IdentityHashMap<>();
        for (ConstraintBlock block : blocks) {
            for (Parameter parameter : block.getParameters()) {
                parent.put(parameter, parameter);
                if (!Units.supported(parameter.getUnit())) {
                    problems.add("单位不一致：约束块「" + block.getName() + "」的参数 " + parameter.getName() + " 的单位 "
                            + (parameter.getUnit() == null || parameter.getUnit().isEmpty() ? "（未填写）" : "[" + parameter.getUnit() + "]")
                            + " 不在求解器白名单内（" + String.join(", ", Units.WHITELIST) + "）");
                }
            }
        }
        for (BindingConnector binding : bindings) {
            Parameter a = binding.getSource();
            Parameter b = binding.getTarget();
            if (a != null && b != null && parent.containsKey(a) && parent.containsKey(b)) {
                if (!java.util.Objects.equals(a.getUnit(), b.getUnit())) {
                    problems.add("单位不一致：绑定「" + binding.getName() + "」连接 " + a.getName() + " [" + a.getUnit() + "] 与 " + b.getName() + " [" + b.getUnit() + "]");
                }
                parent.put(find(parent, a), find(parent, b));
            }
        }
        // one variable per group, in model order
        Map<Parameter, Variable> variableOf = new IdentityHashMap<>();
        Map<Parameter, List<Parameter>> groups = new LinkedHashMap<>();
        for (ConstraintBlock block : blocks) {
            for (Parameter parameter : block.getParameters()) {
                groups.computeIfAbsent(find(parent, parameter), k -> new ArrayList<>()).add(parameter);
            }
        }
        List<Variable> variables = new ArrayList<>();
        for (List<Parameter> group : groups.values()) {
            Variable variable = new Variable("v" + variables.size(), List.copyOf(group));
            variables.add(variable);
            group.forEach(p -> variableOf.put(p, variable));
        }
        List<Equation> equations = new ArrayList<>();
        for (ConstraintBlock block : blocks) {
            String body = block.getExpression() == null ? null : block.getExpression().getBody();
            if (body == null || body.isBlank()) {
                problems.add("约束块「" + block.getName() + "」没有表达式");
                continue;
            }
            Map<String, Parameter> byName = new TreeMap<>();
            block.getParameters().forEach(p -> byName.putIfAbsent(p.getName(), p));
            for (String line : body.split("[\\n;]")) {
                if (line.isBlank()) {
                    continue;
                }
                ExpressionSafety.Scan scan = ExpressionSafety.scan(line.strip(), byName.keySet());
                if (!scan.ok()) {
                    scan.problems().forEach(p -> problems.add("约束块「" + block.getName() + "」的表达式「" + line.strip() + "」：" + p));
                    continue;
                }
                StringBuilder lhs = new StringBuilder();
                StringBuilder rhs = new StringBuilder();
                StringBuilder side = lhs;
                for (String token : scan.tokens()) {
                    if (token.equals("=")) {
                        side = rhs;
                    } else {
                        Parameter parameter = byName.get(token);
                        side.append(parameter == null ? token : variableOf.get(parameter).name()).append(' ');
                    }
                }
                if (lhs.isEmpty() || rhs.isEmpty()) {
                    problems.add("约束块「" + block.getName() + "」的表达式「" + line.strip() + "」：等号两边都要有内容");
                    continue;
                }
                equations.add(new Equation(ElementIds.of(block), line.strip(), lhs.toString().strip(), rhs.toString().strip()));
            }
        }
        if (!problems.isEmpty()) {
            throw new Unsolvable(problems);
        }
        return new SolveSystem(List.copyOf(equations), variableOf, List.copyOf(variables));
    }

    /**
     * The solver request: knowns from {@code inputs} (by variable) and the parameters' values, unknowns
     * {@code targets}; 422 when equations and unknowns do not match or a variable has no value.
     */
    public ConstraintSolver.SolveRequest request(Map<Variable, Double> inputs, List<Variable> targets, double tolerance, int maxIterations) {
        List<String> problems = new ArrayList<>();
        Set<Variable> unknown = new HashSet<>(targets);
        Map<String, Double> knowns = new LinkedHashMap<>();
        for (Variable variable : this.variables) {
            if (unknown.contains(variable)) {
                continue;
            }
            Double value = inputs.get(variable);
            if (value == null) {
                // a value of its own (EDouble defaults to 0: eIsSet tells a value from none; an exact 0 counts as none)
                value = variable.parameters().stream().filter(p -> p.eIsSet(com.example.sysarch.model.SysarchPackage.Literals.PARAMETER__DEFAULT_VALUE))
                        .map(Parameter::getDefaultValue).findFirst().orElse(null);
            }
            if (value == null) {
                problems.add("参数 " + variable.label() + " 既没有输入值也不是求解目标");
            } else {
                knowns.put(variable.name(), value);
            }
        }
        for (Variable target : targets) {
            if (inputs.containsKey(target)) {
                problems.add("参数 " + target.label() + " 不能既是输入又是求解目标");
            }
        }
        if (problems.isEmpty() && this.equations.size() > targets.size()) {
            problems.add("超定：" + this.equations.size() + " 个方程只有 " + targets.size() + " 个未知量（" + names(targets)
                    + "），方程之间可能互相矛盾；请减少方程或增加求解目标");
        } else if (problems.isEmpty() && this.equations.size() < targets.size()) {
            problems.add("欠定：" + targets.size() + " 个未知量（" + names(targets) + "）只有 " + this.equations.size()
                    + " 个方程，解不唯一；请给出更多输入值或减少求解目标");
        }
        if (!problems.isEmpty()) {
            throw new Unsolvable(problems);
        }
        StringBuilder python = new StringBuilder();
        for (int i = 0; i < this.equations.size(); i++) {
            Equation e = this.equations.get(i);
            python.append("_r").append(i).append(" = (").append(e.lhs()).append(") - (").append(e.rhs()).append(")\n");
        }
        for (int i = 0; i < targets.size(); i++) {
            python.append(targets.get(i).name()).append(" = _r").append(i).append('\n');
        }
        return new ConstraintSolver.SolveRequest(python.toString(), this.modelica(knowns, targets), knowns,
                targets.stream().map(Variable::name).toList(), tolerance, maxIterations);
    }

    /** The same system as a Modelica model, for {@link ModelicaSolver} (a static model: parameters and equations). */
    String modelica(Map<String, Double> knowns, List<Variable> targets) {
        StringBuilder model = new StringBuilder("model SysArchSolve\n");
        knowns.forEach((name, value) -> model.append("  parameter Real ").append(name).append(" = ").append(String.format(Locale.ROOT, "%.17g", value)).append(";\n"));
        targets.forEach(t -> model.append("  Real ").append(t.name()).append(";\n"));
        model.append("equation\n");
        this.equations.forEach(e -> model.append("  ").append(e.lhs().replace("**", "^")).append(" = ").append(e.rhs().replace("**", "^")).append(";\n"));
        return model.append("end SysArchSolve;\n").toString();
    }

    /**
     * Whether a solver's answer is a solution: its own flag, every output finite, and every residual within
     * {@code tolerance} of the values' scale. SciPy's {@code least_squares} reports success when it stops at a
     * minimum, also a non-zero one (an inconsistent system), so the residuals decide.
     */
    public static boolean accept(ConstraintSolver.SolveResult result, double tolerance) {
        if (!result.converged() || result.outputs().values().stream().anyMatch(v -> v == null || !Double.isFinite(v))) {
            return false;
        }
        double scale = Math.max(1.0, result.outputs().values().stream().mapToDouble(Math::abs).max().orElse(1.0));
        double limit = Math.max(tolerance, 1e-9) * scale * 10;
        return result.residuals().values().stream().allMatch(r -> r != null && Math.abs(r) <= limit);
    }

    private static String names(List<Variable> variables) {
        return variables.stream().map(Variable::label).collect(Collectors.joining("、"));
    }

    private static Parameter find(Map<Parameter, Parameter> parent, Parameter p) {
        Parameter root = p;
        while (parent.get(root) != root) {
            root = parent.get(root);
        }
        return root;
    }
}

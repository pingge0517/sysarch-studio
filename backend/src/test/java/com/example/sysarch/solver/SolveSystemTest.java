package com.example.sysarch.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.model.BindingConnector;
import com.example.sysarch.model.ConstraintBlock;
import com.example.sysarch.model.ConstraintExpr;
import com.example.sysarch.model.Parameter;
import com.example.sysarch.model.SysarchFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * P3 T4: the equation system before any solver runs (pure). Units on the whitelist, bindings merge parameters, the
 * scan refuses anything but arithmetic over parameter names (P3-AC-4's first line of defence), over- and
 * underdetermined systems are refused with the reason (P3-AC-3), the generated residual code, and judging an answer.
 */
class SolveSystemTest {

    private static final SysarchFactory F = SysarchFactory.eINSTANCE;

    @Test
    void oneEquationBecomesResidualCodeForTheScript() {
        ConstraintBlock power = block("CB-1", "功率", "P_out = P_in * eff", param("P-1", "P_in", "W"), param("P-2", "P_out", "W"), param("P-3", "eff", "1"));
        SolveSystem system = SolveSystem.build(List.of(power), List.of());
        assertEquals(3, system.variables().size());
        var request = system.request(Map.of(var(system, power, 0), 100.0, var(system, power, 2), 0.9), List.of(var(system, power, 1)), 1e-6, 200);
        assertEquals("_r0 = (v1) - (v0 * v2)\nv1 = _r0\n", request.expression());
        assertEquals(Map.of("v0", 100.0, "v2", 0.9), request.inputs());
        assertEquals(List.of("v1"), request.targets());
        assertTrue(request.modelica().contains("parameter Real v0 = 100.00000000000000;") && request.modelica().contains("  Real v1;")
                && request.modelica().contains("  v1 = v0 * v2;"), request.modelica());
    }

    @Test
    void bindingsMakeOneVariableAcrossBlocks() {
        Parameter p = param("P-2", "P", "W");
        Parameter pIn = param("P-3", "P", "W");
        ConstraintBlock power = block("CB-1", "电功率", "P = U * I", param("P-1", "U", "V"), p, param("P-9", "I", "A"));
        ConstraintBlock loss = block("CB-2", "损耗", "L = P * k", pIn, param("P-4", "L", "W"), param("P-5", "k", "1"));
        SolveSystem system = SolveSystem.build(List.of(power, loss), List.of(binding(p, pIn)));
        assertEquals(system.variableOf(p), system.variableOf(pIn), "bound: one variable");
        assertEquals(5, system.variables().size());
        assertEquals(List.of("v1 = v0 * v2", "v3 = v1 * v4"), system.equations().stream().map(e -> e.lhs() + " = " + e.rhs()).toList());
    }

    @Test
    void chineseParameterNamesAreFine() {
        ConstraintBlock block = block("CB-1", "推力", "推力 = 质量 * 加速度", param("P-1", "推力", "N"), param("P-2", "质量", "kg"), param("P-3", "加速度", "1"));
        assertEquals("v0 = v1 * v2", SolveSystem.build(List.of(block), List.of()).equations().stream().map(e -> e.lhs() + " = " + e.rhs()).findFirst().orElseThrow());
    }

    @Test
    void overAndUnderdeterminedSystemsAreRefusedBeforeSolving() {
        ConstraintBlock two = block("CB-1", "两式", "y = 2 * x\ny = x + 1", param("P-1", "x", "m"), param("P-2", "y", "m"));
        SolveSystem system = SolveSystem.build(List.of(two), List.of());
        SolveSystem.Unsolvable over = assertThrows(SolveSystem.Unsolvable.class,
                () -> system.request(Map.of(system.variables().get(0), 1.0), List.of(system.variables().get(1)), 1e-6, 200));
        assertTrue(over.reasons().get(0).startsWith("超定：2 个方程只有 1 个未知量"), over.reasons().toString());
        ConstraintBlock one = block("CB-2", "一式", "y = x + z", param("P-3", "x", "m"), param("P-4", "y", "m"), param("P-5", "z", "m"));
        SolveSystem single = SolveSystem.build(List.of(one), List.of());
        SolveSystem.Unsolvable under = assertThrows(SolveSystem.Unsolvable.class,
                () -> single.request(Map.of(single.variables().get(0), 1.0), List.of(single.variables().get(1), single.variables().get(2)), 1e-6, 200));
        assertTrue(under.reasons().get(0).startsWith("欠定：2 个未知量"), under.reasons().toString());
        SolveSystem.Unsolvable missing = assertThrows(SolveSystem.Unsolvable.class, () -> single.request(Map.of(), List.of(single.variables().get(1)), 1e-6, 200));
        assertTrue(missing.reasons().get(0).contains("既没有输入值也不是求解目标"), missing.reasons().toString());
    }

    @Test
    void unitsOffTheWhitelistAndMixedBindingsAreRefused() {
        Parameter kw = param("P-1", "P", "kW");
        Parameter w = param("P-2", "Q", "W");
        ConstraintBlock a = block("CB-1", "甲", "P = 1", kw);
        ConstraintBlock b = block("CB-2", "乙", "Q = 1", w);
        SolveSystem.Unsolvable e = assertThrows(SolveSystem.Unsolvable.class, () -> SolveSystem.build(List.of(a, b), List.of(binding(kw, w))));
        assertTrue(e.reasons().stream().anyMatch(r -> r.startsWith("单位不一致：约束块「甲」的参数 P 的单位 [kW] 不在求解器白名单内")), e.reasons().toString());
        assertTrue(e.reasons().stream().anyMatch(r -> r.startsWith("单位不一致：绑定")), e.reasons().toString());
    }

    @Test
    void theScanRefusesEverythingButArithmeticOverParameterNames() {
        Set<String> names = Set.of("x", "y");
        for (String bad : List.of("import os", "y = __import__('os').system('dir')", "y = open('x')", "y = eval('1')", "y = x.real", "y = [x]", "y = x; z",
                "y = 'x'", "y = os", "y = exec", "y == x", "y = x = 1", "y = q")) {
            ExpressionSafety.Scan scan = ExpressionSafety.scan(bad, names);
            assertFalse(scan.ok(), "refused: " + bad);
        }
        assertEquals(List.of("不允许的关键字「import」：表达式只能写算式，不能调用或导入任何东西", "不允许的关键字「os」：表达式只能写算式，不能调用或导入任何东西"),
                ExpressionSafety.scan("import os", names).problems());
        assertTrue(ExpressionSafety.scan("y = 2.5e-3 * (x ** 2 - 1) / 4", names).ok());
        ConstraintBlock evil = block("CB-1", "恶意", "import os", param("P-1", "x", "m"));
        SolveSystem.Unsolvable e = assertThrows(SolveSystem.Unsolvable.class, () -> SolveSystem.build(List.of(evil), List.of()));
        assertTrue(e.reasons().get(0).startsWith("约束块「恶意」的表达式「import os」：不允许的关键字「import」"), e.reasons().toString());
    }

    @Test
    void anAnswerIsASolutionOnlyWhenTheResidualsAreZero() {
        assertTrue(SolveSystem.accept(new ConstraintSolver.SolveResult(true, Map.of("v1", 90.0), Map.of("v1", 1e-12), 8, ""), 1e-6));
        assertFalse(SolveSystem.accept(new ConstraintSolver.SolveResult(true, Map.of("v1", 1.0), Map.of("v1", 90.0), 1, "gtol"), 1e-6),
                "the script says converged at a non-zero minimum (spec §3.4's script with an inconsistent system)");
        assertFalse(SolveSystem.accept(new ConstraintSolver.SolveResult(false, Map.of("v1", 90.0), Map.of("v1", 0.0), 200, ""), 1e-6));
        assertFalse(SolveSystem.accept(new ConstraintSolver.SolveResult(true, Map.of("v1", Double.NaN), Map.of("v1", 0.0), 1, ""), 1e-6));
    }

    // ------------------------------------------------------------------ helpers

    static SolveSystem.Variable var(SolveSystem system, ConstraintBlock block, int parameter) {
        return system.variableOf(block.getParameters().get(parameter));
    }

    static ConstraintBlock block(String id, String name, String body, Parameter... parameters) {
        ConstraintBlock block = F.createConstraintBlock();
        block.setId(id);
        block.setName(name);
        ConstraintExpr expression = F.createConstraintExpr();
        expression.setBody(body);
        block.setExpression(expression);
        block.getParameters().addAll(List.of(parameters));
        return block;
    }

    static Parameter param(String id, String name, String unit) {
        Parameter p = F.createParameter();
        p.setId(id);
        p.setName(name);
        p.setUnit(unit);
        return p;
    }

    static BindingConnector binding(Parameter source, Parameter target) {
        BindingConnector b = F.createBindingConnector();
        b.setName(source.getName() + " → " + target.getName());
        b.setSource(source);
        b.setTarget(target);
        return b;
    }
}

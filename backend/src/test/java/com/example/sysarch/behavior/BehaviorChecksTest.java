package com.example.sysarch.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.model.Action;
import com.example.sysarch.model.ActionKind;
import com.example.sysarch.model.Activity;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.ConstraintBlock;
import com.example.sysarch.model.ConstraintExpr;
import com.example.sysarch.model.Parameter;
import com.example.sysarch.model.State;
import com.example.sysarch.model.StateMachine;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.model.Transition;
import com.example.sysarch.sync.consistency.ConsistencyRules;
import com.example.sysarch.sync.consistency.ConsistencyRules.Issue;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.validation.ValidationError;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * P3 T2 (spec §2.3, §4.2): the constraints on constraint blocks, state machines and transitions, and the behaviour checks
 * R7–R10. Acceptance: "构造 5 个反例（双初始态、不可达态、空 guard、无表达式 CALL），全部命中".
 */
class BehaviorChecksTest {

    private static final SysarchFactory F = SysarchFactory.eINSTANCE;

    private static final ConstraintValidator VALIDATOR = new ConstraintValidator();

    @AfterAll
    static void close() {
        VALIDATOR.close();
    }

    // ------------------------------------------------------------------ the counter-examples

    @Test
    void twoInitialStatesAreCaught() {
        StateMachine machine = machine("SM-1", 3);
        machine.getStates().get(1).setIsInitial(true);
        machine.getStates().get(2).setIsFinal(true);
        connect(machine, 0, 1);
        connect(machine, 1, 2);
        assertEquals(List.of("状态机「模式」必须有且仅有一个初始状态，现在有 2 个"), messages(machine, "SingleInitial"));
        assertEquals(List.of("R7"), rules(machine));
        assertTrue(issues(machine).get(0).message().contains("有 2 个初始状态（「S0」（SM-1-S0）、「S1」（SM-1-S1））"), issues(machine).toString());
        assertEquals(ConsistencyRules.BLOCKER, issues(machine).get(0).severity());
    }

    @Test
    void noInitialStateIsCaughtToo() {
        StateMachine machine = machine("SM-0", 2);
        machine.getStates().get(0).setIsInitial(false);
        machine.getStates().get(1).setIsFinal(true);
        connect(machine, 0, 1);
        assertEquals(List.of("R7"), rules(machine), "without an initial state reachability is not judged, R7 says why");
        assertTrue(issues(machine).get(0).message().contains("没有初始状态"));
    }

    @Test
    void anUnreachableStateIsCaught() {
        StateMachine machine = machine("SM-2", 4);
        connect(machine, 0, 1);
        connect(machine, 1, 3);
        connect(machine, 2, 3); // S2 has a way out but no way in
        assertEquals(List.of("状态机「模式」有从初始状态到不了的状态：S2"), messages(machine, "Reachability"));
        List<Issue> r8 = issues(machine).stream().filter(i -> i.rule().equals("R8")).toList();
        assertEquals(List.of("R8:SM-2-S2"), r8.stream().map(Issue::id).toList());
        assertEquals(ConsistencyRules.MAJOR, r8.get(0).severity());
    }

    @Test
    void aGuardWithoutTriggerIsCaught() {
        StateMachine machine = machine("SM-3", 2);
        Transition t = connect(machine, 0, 1);
        t.setGuard("speed > 0");
        assertEquals(List.of("迁移「t0-1」有守卫条件 [speed > 0] 却没有触发事件；有 guard 必须有 trigger"), messages(t, "GuardNotEmpty"));
        t.setTrigger("go");
        assertEquals(List.of(), messages(t, "GuardNotEmpty"));
        t.setTrigger("");
        t.setGuard("");
        assertEquals(List.of(), messages(t, "GuardNotEmpty"), "no guard, no trigger needed");
    }

    @Test
    void aCallWithoutExpressionIsCaught() {
        Activity activity = F.createActivity();
        activity.setId("AC-1");
        activity.setName("启动");
        Action call = named(F.createAction(), "AN-1", "自检");
        call.setKind(ActionKind.CALL);
        Action assign = named(F.createAction(), "AN-2", "赋值");
        assign.setKind(ActionKind.ASSIGN);
        activity.getActions().addAll(List.of(call, assign));
        List<Issue> issues = BehaviorChecks.evaluate(contents(activity));
        assertEquals(List.of("R10:AN-1"), issues.stream().map(Issue::id).toList(), "only CALL needs an expression");
        assertEquals(ConsistencyRules.MAJOR, issues.get(0).severity());
        call.setExpression("selfTest()");
        assertEquals(List.of(), BehaviorChecks.evaluate(contents(activity)));
    }

    @Test
    void aTransitionIntoADeadEndIsCaught() {
        StateMachine machine = machine("SM-4", 3);
        machine.getStates().get(2).setIsFinal(true);
        connect(machine, 0, 1); // S1 is not final and has no way out
        connect(machine, 0, 2); // into a final state: fine
        List<Issue> r9 = issues(machine).stream().filter(i -> i.rule().equals("R9")).toList();
        assertEquals(List.of("R9:SM-4-T0-1"), r9.stream().map(Issue::id).toList());
        assertTrue(r9.get(0).message().contains("进入状态「S1」"), r9.get(0).message());
        assertEquals(ConsistencyRules.MINOR, r9.get(0).severity());
    }

    @Test
    void aSoundMachineHasNoIssue() {
        StateMachine machine = machine("SM-5", 5);
        machine.getStates().get(4).setIsFinal(true);
        for (int i = 0; i < 4; i++) {
            connect(machine, i, i + 1);
        }
        connect(machine, 3, 1); // a loop back is fine
        assertEquals(List.of(), issues(machine));
        assertEquals(List.of(), VALIDATOR.validateAll(machine.eContents()).stream().map(ValidationError::code).toList());
        assertEquals(List.of(), VALIDATOR.validate(machine));
    }

    // ------------------------------------------------------------------ constraint blocks

    @Test
    void constraintBlocksNeedUniqueNamesAndKnownUnits() {
        ConstraintBlock block = named(F.createConstraintBlock(), "CB-1", "功率");
        ConstraintExpr expression = F.createConstraintExpr();
        expression.setBody("P_out = P_in * eff");
        block.setExpression(expression);
        block.getParameters().add(parameter("P-1", "P_in", "W"));
        block.getParameters().add(parameter("P-2", "P_out", "W"));
        assertEquals(List.of(), VALIDATOR.validate(block));
        block.getParameters().add(parameter("P-3", "P_in", "kW"));
        assertEquals(List.of("约束块「功率」有同名参数：P_in"), messages(block, "NoDuplicateParamName"));
        List<String> unit = messages(block, "UnitConsistent");
        assertEquals(1, unit.size());
        assertTrue(unit.get(0).startsWith("约束块「功率」的单位求解器不认识：P_in [kW]；只认 m, kg, s"), unit.get(0));
        assertEquals(List.of("P-3"), VALIDATOR.validate(block).stream().filter(e -> e.code().equals("UnitConsistent")).findFirst().orElseThrow().anchors());
        assertTrue(com.example.sysarch.solver.Units.supported("kHz") && !com.example.sysarch.solver.Units.supported("kW"), "prefixed only as listed (spec)");
    }

    // ------------------------------------------------------------------ reachability limits

    @Test
    void aLargeMachineIsWalkedQuicklyAndTheBudgetDegradesToAWarning() {
        StateMachine big = machine("SM-BIG", 1000);
        for (int i = 0; i < 999; i++) {
            connect(big, i, i + 1);
        }
        long start = System.nanoTime();
        Reachability.Result result = Reachability.of(big);
        assertTrue((System.nanoTime() - start) / 1e6 < 1000, "a 1000-state chain in well under a second");
        assertTrue(result.judged() && result.unreachable().isEmpty());

        AtomicLong now = new AtomicLong();
        Reachability.Result slow = Reachability.of(big, () -> now.addAndGet(Reachability.BUDGET.toNanos() / 10));
        assertTrue(slow.timedOut());
        assertFalse(slow.judged());

        StateMachine small = machine("SM-SMALL", Reachability.LARGE);
        for (int i = 0; i < Reachability.LARGE - 1; i++) {
            connect(small, i, i + 1);
        }
        assertTrue(Reachability.of(small, () -> now.addAndGet(Reachability.BUDGET.toNanos())).judged(), "200 states or fewer: no budget");
    }

    // ------------------------------------------------------------------ helpers

    /** A state machine with {@code n} states S0..S(n-1), S0 initial, ids {@code <id>-S<i>}. */
    private static StateMachine machine(String id, int n) {
        StateMachine machine = named(F.createStateMachine(), id, "模式");
        for (int i = 0; i < n; i++) {
            State state = named(F.createState(), id + "-S" + i, "S" + i);
            state.setIsInitial(i == 0);
            machine.getStates().add(state);
        }
        return machine;
    }

    private static Transition connect(StateMachine machine, int from, int to) {
        Transition t = named(F.createTransition(), machine.getId() + "-T" + from + "-" + to, "t" + from + "-" + to);
        t.setSource(machine.getStates().get(from));
        t.setTarget(machine.getStates().get(to));
        machine.getTransitions().add(t);
        return t;
    }

    private static Parameter parameter(String id, String name, String unit) {
        Parameter p = named(F.createParameter(), id, name);
        p.setUnit(unit);
        return p;
    }

    private static <T extends ArchElement> T named(T element, String id, String name) {
        element.setId(id);
        element.setName(name);
        return element;
    }

    private static List<String> messages(ArchElement element, String code) {
        return VALIDATOR.validate(element).stream().filter(e -> e.code().equals(code)).map(ValidationError::message).toList();
    }

    private static java.util.Iterator<org.eclipse.emf.common.notify.Notifier> contents(ArchElement root) {
        Resource resource = new ResourceImpl();
        resource.getContents().add(root);
        return org.eclipse.emf.ecore.util.EcoreUtil.getAllContents(List.of(resource), true);
    }

    private static List<Issue> issues(StateMachine machine) {
        return BehaviorChecks.evaluate(contents(machine));
    }

    private static List<String> rules(StateMachine machine) {
        return issues(machine).stream().map(Issue::rule).distinct().toList();
    }
}

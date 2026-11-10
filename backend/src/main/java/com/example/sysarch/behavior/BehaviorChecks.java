package com.example.sysarch.behavior;

import com.example.sysarch.model.Action;
import com.example.sysarch.model.ActionKind;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.State;
import com.example.sysarch.model.StateMachine;
import com.example.sysarch.model.Transition;
import com.example.sysarch.sync.consistency.ConsistencyRules;
import com.example.sysarch.sync.consistency.ConsistencyRules.Issue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;


/**
 * The behaviour checks of the consistency report (spec P3 §4.2, T2 step 3; user's decision: for every project, bound to
 * Polarion or not). Pure: a function of the model, nothing repairable.
 * <ul>
 * <li><b>R7 initial state</b> (BLOCKER): a state machine needs exactly one initial state.</li>
 * <li><b>R8 reachability</b> (MAJOR): every state reachable from the initial state ({@link Reachability}); above 200
 * states a walk that runs out of its 5 s budget is reported as MINOR (a warning, no verdict).</li>
 * <li><b>R9 dead transition</b> (MINOR; user's reading of "死迁移"): a transition into a state that is not final and has
 * no outgoing transition: whoever takes it is stuck.</li>
 * <li><b>R10 action completeness</b> (MAJOR): a CALL action needs an expression.</li>
 * </ul>
 */
public final class BehaviorChecks {

    private BehaviorChecks() {
    }

    public static List<Issue> evaluate(Iterator<? extends org.eclipse.emf.common.notify.Notifier> contents) {
        List<Issue> issues = new ArrayList<>();
        contents.forEachRemaining(object -> {
            if (object instanceof StateMachine machine) {
                stateMachine(machine, issues);
            } else if (object instanceof Action action && action.getKind() == ActionKind.CALL
                    && (action.getExpression() == null || action.getExpression().isBlank())) {
                issues.add(issue("R10", ConsistencyRules.MAJOR, action, "动作不完整：调用动作" + label(action) + " 没有表达式（CALL 必须写明调用什么）"));
            }
        });
        issues.sort(Comparator.comparing(Issue::rule).thenComparing(Issue::id));
        return issues;
    }

    private static void stateMachine(StateMachine machine, List<Issue> issues) {
        List<State> initial = machine.getStates().stream().filter(State::isIsInitial).toList();
        if (initial.size() != 1) {
            issues.add(issue("R7", ConsistencyRules.BLOCKER, machine, "初始状态不唯一：状态机" + label(machine) + (initial.isEmpty() ? " 没有初始状态"
                    : " 有 " + initial.size() + " 个初始状态（" + initial.stream().map(BehaviorChecks::label).collect(Collectors.joining("、")) + "）")
                    + "；必须有且仅有一个"));
        }
        Reachability.Result reach = Reachability.of(machine);
        if (reach.timedOut()) {
            issues.add(issue("R8", ConsistencyRules.MINOR, machine, "可达性未判定：状态机" + label(machine) + " 有 " + reach.states() + " 个状态，超过 "
                    + Reachability.LARGE + " 个，检查在 " + Reachability.BUDGET.toSeconds() + " 秒内没有完成（只是警告，建议拆分）"));
        }
        for (State state : reach.unreachable()) {
            issues.add(issue("R8", ConsistencyRules.MAJOR, state, "不可达状态：" + label(state) + " 无法从状态机" + label(machine) + " 的初始状态到达"));
        }
        for (Transition transition : machine.getTransitions()) {
            State target = transition.getTarget();
            if (target != null && !target.isIsFinal() && machine.getTransitions().stream().noneMatch(t -> t.getSource() == target)) {
                issues.add(issue("R9", ConsistencyRules.MINOR, transition, "死迁移：迁移" + label(transition) + " 进入状态" + label(target)
                        + "，该状态不是终态又没有出去的迁移，走到这里就停住了"));
            }
        }
    }

    private static Issue issue(String rule, String severity, ArchElement element, String message) {
        String id = com.example.sysarch.ElementIds.of(element);
        return new Issue(rule + ":" + id, rule, severity, id, null, message, false, null, List.of());
    }

    private static String label(ArchElement element) {
        String name = element.getName() == null || element.getName().isBlank() ? "（未命名）" : element.getName();
        return "「" + name + "」（" + com.example.sysarch.ElementIds.of(element) + "）";
    }
}

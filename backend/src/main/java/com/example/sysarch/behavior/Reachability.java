package com.example.sysarch.behavior;

import com.example.sysarch.model.State;
import com.example.sysarch.model.StateMachine;
import com.example.sysarch.model.Transition;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * {@code StateMachine::Reachability} (spec P3 §2.3, T2 step 2): every state must be reachable from the initial state.
 * A breadth-first walk over the transitions, not OCL (spec: OCL times out on large machines). From several initial
 * states the walk starts at all of them; without any, nothing is judged ({@code SingleInitial} reports that). Above
 * {@value #LARGE} states the walk has a budget of 5 seconds; when it runs out the result is {@code timedOut} and the
 * check reports a warning instead of a verdict.
 */
public final class Reachability {

    public static final int LARGE = 200;

    public static final Duration BUDGET = Duration.ofSeconds(5);

    /** The states not reached; {@code judged} false when there is no initial state or the budget ran out. */
    public record Result(List<State> unreachable, boolean judged, boolean timedOut, int states) {
    }

    private Reachability() {
    }

    public static Result of(StateMachine machine) {
        return of(machine, System::nanoTime);
    }

    static Result of(StateMachine machine, LongSupplier clock) {
        List<State> states = machine.getStates();
        List<State> initial = states.stream().filter(State::isIsInitial).toList();
        if (initial.isEmpty()) {
            return new Result(List.of(), false, false, states.size());
        }
        Map<State, List<State>> next = new HashMap<>();
        for (Transition transition : machine.getTransitions()) {
            if (transition.getSource() != null && transition.getTarget() != null) {
                next.computeIfAbsent(transition.getSource(), s -> new java.util.ArrayList<>()).add(transition.getTarget());
            }
        }
        long deadline = states.size() > LARGE ? clock.getAsLong() + BUDGET.toNanos() : Long.MAX_VALUE;
        Set<State> reached = new LinkedHashSet<>(initial);
        Deque<State> queue = new ArrayDeque<>(initial);
        while (!queue.isEmpty()) {
            if (deadline != Long.MAX_VALUE && clock.getAsLong() > deadline) {
                return new Result(List.of(), false, true, states.size());
            }
            for (State target : next.getOrDefault(queue.poll(), List.of())) {
                if (reached.add(target)) {
                    queue.add(target);
                }
            }
        }
        return new Result(states.stream().filter(s -> !reached.contains(s)).toList(), true, false, states.size());
    }
}

package com.example.sysarch.view.api;

import java.util.Objects;

/**
 * VSM {@code filters[]}: hides elements without changing the model (spec §5.1 invariant 3).
 *
 * @param expression boolean expression over the diagram root type's {@code layer}, {@code isAbstract} and
 *        {@code stereotypes}, e.g. {@code not isAbstract} or {@code layer == 'SYSTEM'} (spec P1 §3.5)
 * @param alwaysOn the view's layer scope: the expression names {@code layer}, so the filter is always applied and is
 *        not offered as a toggle (P1 T3, user's decision). The compiler decides this, not the runtime.
 */
public record Filter(String id, String label, String expression, boolean alwaysOn) {

    public Filter {
        Objects.requireNonNull(id);
        Objects.requireNonNull(expression);
    }

    /** A filter the user switches on and off (P0). */
    public Filter(String id, String label, String expression) {
        this(id, label, expression, false);
    }

    public static Filter alwaysOn(String id, String label, String expression) {
        return new Filter(id, label, expression, true);
    }
}

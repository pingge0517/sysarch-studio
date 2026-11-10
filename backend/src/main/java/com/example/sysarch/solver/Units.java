package com.example.sysarch.solver;

import java.util.List;
import java.util.Set;

/**
 * The units a solver understands (spec P3 §3.2): SI base and derived units and a few prefixed forms; no conversion.
 * One list for the modelling check ({@code ConstraintBlock::UnitConsistent}, P3 T2) and the solvers (T4), so a unit
 * that passes the model check is one every solver accepts.
 */
public final class Units {

    /** In the spec's order; {@code 1} is a dimensionless quantity. */
    public static final List<String> WHITELIST = List.of("m", "kg", "s", "A", "K", "mol", "cd", "W", "V", "Hz", "N", "Pa", "J", "C", "rad", "1", "mm",
            "cm", "km", "g", "ms", "us", "kHz", "MHz");

    private static final Set<String> KNOWN = Set.copyOf(WHITELIST);

    private Units() {
    }

    public static boolean supported(String unit) {
        return unit != null && KNOWN.contains(unit);
    }
}

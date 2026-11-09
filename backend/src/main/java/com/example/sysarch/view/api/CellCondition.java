package com.example.sysarch.view.api;

import java.util.Objects;

/**
 * When a table cell is marked (VSM {@code cells.existsExpression}), compiled by vsm-compiler into this tree, so no
 * expression is parsed at runtime. Evaluated on an element {@code self} with the cell's row and column; see
 * {@link TableDescription} for which element {@code self} is.
 */
public sealed interface CellCondition {

    enum Side {
        ROW, COLUMN
    }

    record And(CellCondition left, CellCondition right) implements CellCondition {
    }

    record Or(CellCondition left, CellCondition right) implements CellCondition {
    }

    record Not(CellCondition inner) implements CellCondition {
    }

    /** {@code feature == row|column}: the single-valued reference holds that element. */
    record EqualTo(String feature, Side side) implements CellCondition {
        public EqualTo {
            Objects.requireNonNull(feature);
            Objects.requireNonNull(side);
        }
    }

    /** {@code feature->includes(row|column)}: the many-valued reference contains that element. */
    record Includes(String feature, Side side) implements CellCondition {
        public Includes {
            Objects.requireNonNull(feature);
            Objects.requireNonNull(side);
        }
    }

    static CellCondition and(CellCondition left, CellCondition right) {
        return new And(left, right);
    }

    static CellCondition or(CellCondition left, CellCondition right) {
        return new Or(left, right);
    }

    static CellCondition not(CellCondition inner) {
        return new Not(inner);
    }

    static CellCondition equalTo(String feature, Side side) {
        return new EqualTo(feature, side);
    }

    static CellCondition includes(String feature, Side side) {
        return new Includes(feature, side);
    }
}

package com.example.sysarch.view.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A VSM table (P1 {@code kind: table}, spec P1 §3.3/§3.4): a matrix of {@code rows} × {@code columns} whose cells are
 * marked with {@code markSymbol} when {@code cellCondition} holds.
 *
 * <p>Which element the condition is about ({@code self}): when the rows are of the domain type (requirement trace:
 * rows and domain are {@code Requirement}), {@code self} is the row. Otherwise (allocation matrix: rows are blocks,
 * the domain is {@code Allocate}) a cell is marked when some element of the domain type in the model satisfies the
 * condition, and the actions create or delete such elements.
 *
 * @param cellExpression the YAML text of the condition, for display
 */
public record TableDescription(String id, String label, String domainType, Axis rows, Axis columns, CellCondition cellCondition,
        String cellExpression, String markSymbol, String markColor, List<TableAction> actions) {

    public TableDescription {
        Objects.requireNonNull(id);
        Objects.requireNonNull(domainType);
        Objects.requireNonNull(rows);
        Objects.requireNonNull(columns);
        Objects.requireNonNull(cellCondition);
        actions = List.copyOf(actions);
    }

    public static Builder newTableDescription(String id) {
        return new Builder(id);
    }

    /** The fluent form the generated providers use. */
    public static final class Builder {

        private final String id;

        private String label;

        private String domainType;

        private Axis rows;

        private Axis columns;

        private CellCondition cellCondition;

        private String cellExpression;

        private String markSymbol;

        private String markColor;

        private final List<TableAction> actions = new ArrayList<>();

        private Builder(String id) {
            this.id = Objects.requireNonNull(id);
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder domainType(String domainType) {
            this.domainType = domainType;
            return this;
        }

        public Builder rows(Axis rows) {
            this.rows = rows;
            return this;
        }

        public Builder columns(Axis columns) {
            this.columns = columns;
            return this;
        }

        public Builder cells(CellCondition condition, String expression, String markSymbol, String markColor) {
            this.cellCondition = condition;
            this.cellExpression = expression;
            this.markSymbol = markSymbol;
            this.markColor = markColor;
            return this;
        }

        public Builder addAction(TableAction action) {
            this.actions.add(Objects.requireNonNull(action));
            return this;
        }

        public TableDescription build() {
            return new TableDescription(this.id, this.label, this.domainType, this.rows, this.columns, this.cellCondition, this.cellExpression,
                    this.markSymbol, this.markColor, this.actions);
        }
    }
}

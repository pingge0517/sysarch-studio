package com.example.sysarch.view.table;

import java.util.List;

/**
 * What a VSM table shows for the current model (P1 T4): the JSON the matrix page renders. A pure value, computed by
 * {@link MatrixProjector}; the model stays the only source of truth.
 *
 * @param rows the row headers in display order (grouped: one group's rows are consecutive)
 * @param groups the groups that occur, keyed by {@link Header#group()}, in display order
 * @param cells the marked cells only
 */
public record Matrix(String tableId, String label, String markSymbol, String markColor, List<Group> groups, List<Header> rows,
        List<Header> columns, List<Cell> cells, List<Action> actions) {

    /** A row or column: a model element. {@code group} is the groupBy value (a layer literal) or null. */
    public record Header(String id, String label, String group) {
    }

    /** e.g. {@code LOGICAL} shown as {@code LA 逻辑架构}. */
    public record Group(String key, String label) {
    }

    public record Cell(String rowId, String columnId) {
    }

    /** @param onMarked applies to a marked cell (delete-link) rather than an empty one (create-link, link) */
    public record Action(String id, String label, String kind, boolean onMarked) {
    }
}

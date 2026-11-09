package com.example.sysarch.view.api;

import java.util.Objects;

/**
 * A table action (VSM {@code actions[]}). Only the fields relevant to {@code kind} are set.
 *
 * @param validateOnCreate create-link: run the constraints before anything is stored (spec P1 §3.3: a click in the table
 *        never bypasses them); vsm-compiler requires it
 * @param feature link: the row's many-valued reference that receives the column
 */
public record TableAction(String id, String label, TableActionKind kind, String targetClass, boolean validateOnCreate, String feature) {

    public TableAction {
        Objects.requireNonNull(id);
        Objects.requireNonNull(kind);
    }

    public static TableAction createLink(String id, String label, String targetClass, boolean validateOnCreate) {
        return new TableAction(id, label, TableActionKind.CREATE_LINK, Objects.requireNonNull(targetClass), validateOnCreate, null);
    }

    public static TableAction deleteLink(String id, String label) {
        return new TableAction(id, label, TableActionKind.DELETE_LINK, null, false, null);
    }

    public static TableAction link(String id, String label, String feature) {
        return new TableAction(id, label, TableActionKind.LINK, null, false, Objects.requireNonNull(feature));
    }
}

package com.example.sysarch.view.api;

/** VSM table {@code actions[].action}. */
public enum TableActionKind {
    /** Create an element of the domain class whose {@code ==} features point at the row and the column. */
    CREATE_LINK,
    /** Delete the domain elements that mark the cell. */
    DELETE_LINK,
    /** Add the column to a many-valued reference of the row ({@code feature}). */
    LINK
}

package com.example.sysarch.view.api;

/**
 * VSM {@code filters[]}: hides elements without changing the model (spec §5.1 invariant 3).
 *
 * @param expression boolean expression over the diagram root type's features, e.g. {@code not isAbstract}
 */
public record Filter(String id, String label, String expression) {
}

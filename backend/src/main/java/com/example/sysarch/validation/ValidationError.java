package com.example.sysarch.validation;

import java.util.List;
import java.util.Objects;

/**
 * One violated constraint, in the fixed shape the frontend consumes (spec T6 step 3).
 *
 * @param code the constraint name from the Ecore, e.g. {@code PortTypeCompatible}
 * @param elementId the element the constraint is defined on (for an edge drop: the connection being created,
 *        which has no id yet, so null)
 * @param message user-facing text (Chinese, as in the UI)
 * @param anchors ids of the elements to highlight, e.g. both ports of a rejected connection
 */
public record ValidationError(String code, String elementId, String message, List<String> anchors) {

    public ValidationError {
        Objects.requireNonNull(code);
        Objects.requireNonNull(message);
        anchors = List.copyOf(anchors);
    }
}

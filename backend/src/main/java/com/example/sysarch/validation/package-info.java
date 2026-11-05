/**
 * Constraint checks (spec §3.2, T6). {@code ConstraintValidator} evaluates the Ecore's OCL invariants with
 * Eclipse OCL (Pivot) and returns {@code ValidationError}s ({code, elementId, message, anchors[]}).
 *
 * <p>TODO(T6, needs Sirius Web): call {@code validateAll} before an edit is committed and reject it on errors
 * (property panel marks the field red), and call {@code validateEdgeDrop} from the IBD create-edge tool
 * ({@code validateOnDrop: true}) so a rejected connection is never stored and both anchors are highlighted.
 */
package com.example.sysarch.validation;

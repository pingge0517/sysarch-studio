/**
 * Constraint checks (spec §3.2, T6). {@code ConstraintValidator} evaluates the Ecore's OCL invariants with
 * Eclipse OCL (Pivot) and returns {@code ValidationError}s ({code, elementId, message, anchors[]}).
 *
 * <ul>
 * <li>Edge drop: {@code validateEdgeDrop}, called by the IBD connect tool before it creates anything.</li>
 * <li>Save path, marked: {@code SysarchEValidator} feeds Sirius' properties view (red fields) and Validation view.</li>
 * <li>Save path, vetoed: {@code ChangeGuard} rolls back edits that introduce a {@code ChangeGuard.VETOED}
 * violation; wired into Sirius by {@code config.ConstraintEnforcement}.</li>
 * </ul>
 */
package com.example.sysarch.validation;

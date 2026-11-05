/**
 * Diagram views.
 *
 * <ul>
 * <li>{@code view.api}: hand-written descriptor API that generated code targets (spec §4.5).</li>
 * <li>{@code view.generated}: produced by vsm-compiler from {@code views/*.vsm.yaml}. Never edit it by hand;
 * change the YAML and run {@code ./gradlew :vsm-compiler:generateViews}.</li>
 * </ul>
 *
 * <p>TODO(T4/T5): adapter from the descriptor API to Sirius Web; incremental auto-sync.
 */
package com.example.sysarch.view;

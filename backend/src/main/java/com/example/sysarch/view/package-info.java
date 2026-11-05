/**
 * Diagram views.
 *
 * <ul>
 * <li>{@code view.api}: hand-written descriptor API that generated code targets (spec §4.5).</li>
 * <li>{@code view.generated}: produced by vsm-compiler from {@code views/*.vsm.yaml}. Never edit it by hand;
 * change the YAML and run {@code ./gradlew :vsm-compiler:generateViews}.</li>
 * <li>{@code view.projection}: model + description to {@code DiagramSnapshot} (nodes, edges, labels, filters).
 * The P0 projection rules are documented on {@code DiagramProjector}.</li>
 * <li>{@code view.sirius}: renders the projection through Sirius Web (descriptions, palette, tools).</li>
 * <li>{@code view.refresh}, {@code view.layout}: Sirius-independent snapshot diffing and layout store with delete
 * cascade. Not wired into Sirius, which re-renders whole diagrams and keeps its own layout.</li>
 * </ul>
 */
package com.example.sysarch.view;

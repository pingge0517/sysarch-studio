/**
 * Diagram views.
 *
 * <ul>
 * <li>{@code view.api}: hand-written descriptor API that generated code targets (spec §4.5).</li>
 * <li>{@code view.generated}: produced by vsm-compiler from {@code views/*.vsm.yaml}. Never edit it by hand;
 * change the YAML and run {@code ./gradlew :vsm-compiler:generateViews}.</li>
 * <li>{@code view.projection}: model + description to {@code DiagramSnapshot} (nodes, edges, labels, filters).
 * Sirius-independent; the P0 projection rules are documented on {@code DiagramProjector}.</li>
 * </ul>
 *
 * <p>TODO(T4): adapter that renders a {@code DiagramSnapshot} through Sirius Web, and registering the views in
 * the model tree. TODO(T5): incremental refresh by diffing snapshots.
 */
package com.example.sysarch.view;

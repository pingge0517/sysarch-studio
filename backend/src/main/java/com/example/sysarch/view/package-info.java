/**
 * Diagram views.
 *
 * <ul>
 * <li>{@code view.api}: hand-written descriptor API that generated code targets (spec §4.5).</li>
 * <li>{@code view.generated}: produced by vsm-compiler from {@code views/*.vsm.yaml}. Never edit it by hand;
 * change the YAML and run {@code ./gradlew :vsm-compiler:generateViews}.</li>
 * <li>{@code view.projection}: model + description to {@code DiagramSnapshot} (nodes, edges, labels, filters).
 * The P0 projection rules are documented on {@code DiagramProjector}.</li>
 * <li>{@code view.refresh}: {@code DiagramSynchronizer} re-projects on EMF changes and publishes only the
 * {@code SnapshotDiff} (spec §5.2).</li>
 * <li>{@code view.layout}: {@code DiagramLayoutStore} keeps layout in its own resource; {@code LayoutCascade}
 * removes layout of deleted elements (spec §5.1).</li>
 * </ul>
 *
 * <p>All of the above is Sirius-independent. TODO(T4/T5): Sirius Web adapter that renders snapshots, applies
 * diffs to open canvases, writes layout on drag end, falls back to auto-layout, and registers the views.
 */
package com.example.sysarch.view;

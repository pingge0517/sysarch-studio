package com.example.sysarch.view.refresh;

import com.example.sysarch.view.api.DiagramDescription;
import com.example.sysarch.view.api.SynchronizationKind;
import com.example.sysarch.view.projection.DiagramProjector;
import com.example.sysarch.view.projection.DiagramSnapshot;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EContentAdapter;

/**
 * Keeps one open {@code synchronization: auto} diagram in step with the model (spec §5.2, T5 step 3).
 *
 * <p>It listens to EMF change notifications on the model resource. On a change it re-projects the diagram,
 * diffs the result against the previous snapshot and hands only the {@link SnapshotDiff} to the listener (the
 * canvas subscription). Re-projecting is linear in the document and well inside the budget at 500 elements (see
 * IbdSyncTest's perf report). Diffing against the last snapshot yields exactly the affected nodes and edges, including
 * changes that {@code autoExpand} depth alone would miss (a renamed type changes every part label that shows it).
 *
 * <p>Notifications are coalesced (spec §11.2 #5): the first change schedules one refresh on {@code executor},
 * and further changes before it runs are folded into it. The executor must run on the editing context's thread,
 * because EMF models are not thread-safe.
 *
 * <p>Layout records live in a separate resource ({@code DiagramLayoutStore}), so moving or resizing a node
 * produces no model event here (spec §5.2 last row).
 */
public final class DiagramSynchronizer implements AutoCloseable {

    private final DiagramDescription description;

    private final EObject root;

    private final Resource model;

    private final Executor executor;

    private final Consumer<SnapshotDiff> listener;

    private final EContentAdapter adapter = new EContentAdapter() {
        @Override
        public void notifyChanged(Notification notification) {
            super.notifyChanged(notification);
            if (!notification.isTouch() && notification.getEventType() != Notification.REMOVING_ADAPTER) {
                DiagramSynchronizer.this.modelChanged();
            }
        }
    };

    private Set<String> activeFilters = Set.of();

    private DiagramSnapshot snapshot;

    private boolean refreshPending;

    private boolean closed;

    /**
     * @param executor where refreshes run; the editing context's executor in production, a direct executor in tests
     * @param listener receives each non-empty diff, in order
     */
    public DiagramSynchronizer(DiagramDescription description, EObject root, Executor executor, Consumer<SnapshotDiff> listener) {
        if (description.synchronizationKind() != SynchronizationKind.AUTO) {
            throw new IllegalArgumentException(description.id() + " is not an auto-synchronized diagram");
        }
        this.description = description;
        this.root = root;
        this.model = Objects.requireNonNull(root.eResource(), "the diagram root must be in a resource");
        this.executor = executor;
        this.listener = listener;
        this.snapshot = DiagramProjector.project(description, root, this.activeFilters);
        this.model.eAdapters().add(this.adapter);
    }

    public synchronized DiagramSnapshot snapshot() {
        return this.snapshot;
    }

    /** Switches filters on or off; returns (and publishes) the resulting diff. The model is not touched. */
    public synchronized SnapshotDiff setActiveFilters(Set<String> filterIds) {
        this.activeFilters = Set.copyOf(filterIds);
        return this.refresh();
    }

    /** Re-projects now and publishes the diff if it is not empty. */
    public synchronized SnapshotDiff refresh() {
        this.refreshPending = false;
        if (this.closed) {
            return SnapshotDiff.between(this.snapshot, this.snapshot);
        }
        DiagramSnapshot next = this.root.eResource() == this.model
                ? DiagramProjector.project(this.description, this.root, this.activeFilters)
                : new DiagramSnapshot(this.snapshot.representationId(), this.snapshot.rootId(), List.of(), List.of()); // root deleted
        SnapshotDiff diff = SnapshotDiff.between(this.snapshot, next);
        this.snapshot = next;
        if (!diff.isEmpty()) {
            this.listener.accept(diff);
        }
        return diff;
    }

    @Override
    public synchronized void close() {
        this.closed = true;
        this.model.eAdapters().remove(this.adapter);
    }

    private synchronized void modelChanged() {
        if (!this.refreshPending && !this.closed) {
            this.refreshPending = true;
            this.executor.execute(this::refresh);
        }
    }
}

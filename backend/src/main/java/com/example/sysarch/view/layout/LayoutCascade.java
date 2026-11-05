package com.example.sysarch.view.layout;

import com.example.sysarch.view.projection.DiagramProjector;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EContentAdapter;

/**
 * Deletes layout records when their model element is deleted, in every diagram, whether open or not (spec §5.1
 * invariant 2, T5 step 5).
 *
 * <p>An element counts as deleted when it is removed from its container (or from the resource) and is then in no
 * resource. A move to another container is not a deletion, and the element keeps its layout. Elements nested in a
 * deleted element are deleted with it.
 */
public final class LayoutCascade extends EContentAdapter {

    private final Resource model;

    private final DiagramLayoutStore store;

    private LayoutCascade(Resource model, DiagramLayoutStore store) {
        if (model == store.resource()) {
            throw new IllegalArgumentException("layouts must not be stored in the model resource (spec §5.1)");
        }
        this.model = model;
        this.store = store;
    }

    /** Starts cascading deletions from {@code model} into {@code store}; detach with {@link #close()}. */
    public static LayoutCascade attach(Resource model, DiagramLayoutStore store) {
        LayoutCascade cascade = new LayoutCascade(model, store);
        model.eAdapters().add(cascade);
        return cascade;
    }

    public void close() {
        this.model.eAdapters().remove(this);
    }

    @Override
    public void notifyChanged(Notification notification) {
        super.notifyChanged(notification);
        int type = notification.getEventType();
        if ((type != Notification.REMOVE && type != Notification.REMOVE_MANY && type != Notification.SET) || !this.isContainment(notification)) {
            return;
        }
        Set<String> deleted = new LinkedHashSet<>();
        collect(notification.getOldValue(), deleted);
        if (!deleted.isEmpty()) {
            this.store.deleteForElements(deleted);
        }
    }

    private boolean isContainment(Notification notification) {
        if (notification.getNotifier() instanceof Resource) {
            return notification.getFeatureID(Resource.class) == Resource.RESOURCE__CONTENTS;
        }
        return notification.getFeature() instanceof EReference reference && reference.isContainment();
    }

    private static void collect(Object oldValue, Set<String> deleted) {
        if (oldValue instanceof Collection<?> values) {
            values.forEach(value -> collect(value, deleted));
        } else if (oldValue instanceof EObject element && element.eResource() == null) {
            deleted.add(DiagramProjector.elementId(element));
            element.eAllContents().forEachRemaining(child -> deleted.add(DiagramProjector.elementId(child)));
        }
    }
}

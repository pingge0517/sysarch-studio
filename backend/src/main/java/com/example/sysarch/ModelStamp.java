package com.example.sysarch;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EContentAdapter;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Change counter of a model: of the resource set, else the resource, else the containment tree. It moves on with
 * every real change (not touches, not adapter removal), so "nothing changed since X" is one comparison. Used by the
 * projection cache, and by the view marks that last until the next edit (red ports of a rejected connection, the
 * notice of a vetoed edit).
 */
public final class ModelStamp extends EContentAdapter {

    private volatile long stamp;

    private ModelStamp() {
    }

    /** The notifier whose changes the stamp of {@code element} counts. */
    public static Notifier scope(EObject element) {
        return element.eResource() == null ? EcoreUtil.getRootContainer(element)
                : element.eResource().getResourceSet() != null ? element.eResource().getResourceSet() : element.eResource();
    }

    public static long of(EObject element) {
        return of(scope(element));
    }

    public static long of(Notifier scope) {
        for (Object adapter : scope.eAdapters()) {
            if (adapter instanceof ModelStamp modelStamp) {
                return modelStamp.stamp;
            }
        }
        ModelStamp modelStamp = new ModelStamp();
        scope.eAdapters().add(modelStamp);
        return modelStamp.stamp;
    }

    @Override
    public void notifyChanged(Notification notification) {
        super.notifyChanged(notification);
        if (!notification.isTouch() && notification.getEventType() != Notification.REMOVING_ADAPTER) {
            this.stamp++;
        }
    }
}

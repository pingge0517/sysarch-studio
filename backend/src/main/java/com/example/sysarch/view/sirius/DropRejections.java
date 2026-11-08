package com.example.sysarch.view.sirius;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.ecore.EObject;

/**
 * The ports of the last rejected connection, drawn red (spec AC-5 "拒绝落位，两端端口描红").
 *
 * <p>The mark is the model's change stamp at the time of the rejection ({@link ProjectionCache.ModelStamp}): as soon
 * as anything in the model changes, the stamp moves on and the ports render normally again. So the red lasts until
 * the next edit, as decided with the user, and nothing has to be cleared. The model itself is never touched; this is
 * view state only, kept per resource set (weakly, so a closed editing context leaves nothing behind).
 */
final class DropRejections {

    public static final String REJECTED_BORDER_COLOR = "#D32F2F";

    private record Rejection(long stamp, Set<EObject> ports) {
    }

    private final Map<Notifier, Rejection> byScope = Collections.synchronizedMap(new WeakHashMap<>());

    void reject(EObject source, EObject target) {
        this.byScope.put(ProjectionCache.ModelStamp.scope(source), new Rejection(ProjectionCache.ModelStamp.of(source), Set.of(source, target)));
    }

    boolean isRejected(EObject element) {
        Rejection rejection = this.byScope.get(ProjectionCache.ModelStamp.scope(element));
        return rejection != null && rejection.ports().contains(element) && rejection.stamp() == ProjectionCache.ModelStamp.of(element);
    }
}

package com.example.sysarch.view.sirius;

import com.example.sysarch.view.api.DiagramDescription;
import com.example.sysarch.view.projection.DiagramProjector;
import com.example.sysarch.view.projection.DiagramProjector.ProjectionResult;
import com.example.sysarch.view.projection.DiagramSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EContentAdapter;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.springframework.stereotype.Component;

/**
 * One projection per (diagram description, root, active filters) and model state.
 *
 * <p>Sirius renders a diagram by calling a node description's {@code semanticElementsProvider} once per parent
 * node instance, and the label and style providers once per node. Without a cache each call would re-project
 * the whole diagram. A cached projection is reused until anything in the root's resource set changes; a
 * counter maintained by an {@link EContentAdapter} on the resource set detects that.
 */
@Component
public class ProjectionCache {

    private final Map<EObject, Map<Key, Entry>> cache = new WeakHashMap<>();

    public synchronized Projection get(DiagramDescription description, EObject root, Set<String> activeFilters) {
        long stamp = ModelStamp.of(root);
        Key key = new Key(description.id(), Set.copyOf(activeFilters));
        Map<Key, Entry> perRoot = this.cache.computeIfAbsent(root, r -> new HashMap<>());
        Entry entry = perRoot.get(key);
        if (entry == null || entry.stamp() != stamp) {
            entry = new Entry(stamp, new Projection(DiagramProjector.projectWithElements(description, root, activeFilters)));
            perRoot.put(key, entry);
        }
        return entry.projection();
    }

    /** A projection with lookup indexes for the per-node and per-parent queries Sirius makes. */
    public static final class Projection {

        private final ProjectionResult result;

        private final Map<String, DiagramSnapshot.Node> nodesById = new HashMap<>();

        private final Map<String, List<DiagramSnapshot.Node>> nodesByStyleAndParent = new LinkedHashMap<>();

        Projection(ProjectionResult result) {
            this.result = result;
            for (DiagramSnapshot.Node node : result.snapshot().nodes()) {
                this.nodesById.put(node.id(), node);
                this.nodesByStyleAndParent.computeIfAbsent(key(node.styleId(), node.parentId()), k -> new ArrayList<>()).add(node);
            }
        }

        public ProjectionResult result() {
            return this.result;
        }

        public DiagramSnapshot snapshot() {
            return this.result.snapshot();
        }

        public DiagramSnapshot.Node node(String id) {
            return this.nodesById.get(id);
        }

        /** Nodes of one style under one parent; {@code parentId} null means top level. */
        public List<DiagramSnapshot.Node> nodes(String styleId, String parentId) {
            return this.nodesByStyleAndParent.getOrDefault(key(styleId, parentId), List.of());
        }

        public EObject element(String id) {
            return this.result.element(id);
        }

        private static String key(String styleId, String parentId) {
            return styleId + "\u0000" + parentId;
        }
    }

    private record Key(String descriptionId, Set<String> activeFilters) {
    }

    private record Entry(long stamp, Projection projection) {
    }

    /** Change counter of the root's resource set (or resource, or containment tree). */
    static final class ModelStamp extends EContentAdapter {

        private volatile long stamp;

        /** The notifier whose changes the stamp counts: the resource set, else the resource, else the containment root. */
        static Notifier scope(EObject root) {
            return root.eResource() == null ? EcoreUtil.getRootContainer(root)
                    : root.eResource().getResourceSet() != null ? root.eResource().getResourceSet() : root.eResource();
        }

        static long of(EObject root) {
            Notifier target = scope(root);
            for (Object adapter : target.eAdapters()) {
                if (adapter instanceof ModelStamp modelStamp) {
                    return modelStamp.stamp;
                }
            }
            ModelStamp modelStamp = new ModelStamp();
            target.eAdapters().add(modelStamp);
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
}

package com.example.sysarch.view.layout;

import com.example.sysarch.model.DiagramLayout;
import com.example.sysarch.model.SysarchFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * Diagram layout (position, size, edge waypoints) kept apart from the model (spec §5.1 invariant 2). Records are
 * {@link DiagramLayout} objects in a resource of their own. That resource must never be the model resource: then
 * moving a node writes only layout, raises no model event, and deleting layout cannot touch the model.
 *
 * <p>A record is keyed by (diagram key, element ref). For a node the ref is the element id; for an edge it is the
 * edge id ({@code style:sourceId->targetId}). A node with no record is laid out automatically by the renderer
 * (T5 step 4). When a model element is deleted, {@link LayoutCascade} removes its records.
 */
public final class DiagramLayoutStore {

    private static final String KEY_SEPARATOR = "|";

    private final Resource layouts;

    private final Map<String, DiagramLayout> index = new LinkedHashMap<>();

    /** @param layouts the layout resource; existing {@link DiagramLayout} contents are loaded */
    public DiagramLayoutStore(Resource layouts) {
        this.layouts = Objects.requireNonNull(layouts);
        for (EObject content : layouts.getContents()) {
            if (content instanceof DiagramLayout layout) {
                this.index.put(key(layout.getDiagramKey(), layout.getElementRef()), layout);
            }
        }
    }

    public Resource resource() {
        return this.layouts;
    }

    /** Position and size of a node, written when a drag or resize ends. */
    public void saveNode(String diagramKey, String elementId, int x, int y, int width, int height) {
        DiagramLayout layout = this.record(diagramKey, elementId);
        layout.setX(x);
        layout.setY(y);
        layout.setWidth(width);
        layout.setHeight(height);
    }

    /** Bend points of an edge, as "x,y" strings in order. Kept while the edge exists (spec §5.2). */
    public void saveEdge(String diagramKey, String edgeId, List<String> waypoints) {
        DiagramLayout layout = this.record(diagramKey, edgeId);
        layout.getWaypoints().clear();
        layout.getWaypoints().addAll(waypoints);
    }

    public Optional<DiagramLayout> find(String diagramKey, String elementRef) {
        return Optional.ofNullable(this.index.get(key(diagramKey, elementRef)));
    }

    public List<DiagramLayout> layouts(String diagramKey) {
        return this.index.values().stream().filter(l -> diagramKey.equals(l.getDiagramKey())).toList();
    }

    /** Removes one record. Only layout changes; the model element is untouched. */
    public boolean delete(String diagramKey, String elementRef) {
        DiagramLayout layout = this.index.remove(key(diagramKey, elementRef));
        if (layout != null) {
            this.layouts.getContents().remove(layout);
        }
        return layout != null;
    }

    /**
     * Cascade for deleted model elements: removes, in every diagram, the node records of these elements and the
     * edge records of edges that start or end at one of them.
     *
     * @return number of records removed
     */
    public int deleteForElements(Collection<String> elementIds) {
        List<String> doomed = new ArrayList<>();
        for (Map.Entry<String, DiagramLayout> entry : this.index.entrySet()) {
            String ref = entry.getValue().getElementRef();
            if (elementIds.contains(ref) || edgeTouches(ref, elementIds)) {
                doomed.add(entry.getKey());
            }
        }
        for (String key : doomed) {
            this.layouts.getContents().remove(this.index.remove(key));
        }
        return doomed.size();
    }

    private DiagramLayout record(String diagramKey, String elementRef) {
        return this.index.computeIfAbsent(key(diagramKey, elementRef), k -> {
            DiagramLayout layout = SysarchFactory.eINSTANCE.createDiagramLayout();
            layout.setId("layout" + KEY_SEPARATOR + k);
            layout.setName(elementRef); // DiagramLayout is an ArchElement, so it needs a name (NameNotEmpty)
            layout.setDiagramKey(diagramKey);
            layout.setElementRef(elementRef);
            this.layouts.getContents().add(layout);
            return layout;
        });
    }

    /** Edge refs look like {@code style:sourceId->targetId}. */
    private static boolean edgeTouches(String ref, Collection<String> elementIds) {
        int colon = ref.indexOf(':');
        int arrow = ref.indexOf("->", colon + 1);
        if (colon < 0 || arrow < 0) {
            return false;
        }
        return elementIds.contains(ref.substring(colon + 1, arrow)) || elementIds.contains(ref.substring(arrow + 2));
    }

    private static String key(String diagramKey, String elementRef) {
        return diagramKey + KEY_SEPARATOR + elementRef;
    }
}

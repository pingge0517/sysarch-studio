package com.example.sysarch.baseline;

import com.example.sysarch.ElementIds;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.DiagramLayout;
import com.example.sysarch.model.SysarchPackage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * The state of a model that a baseline stores and a diff compares (P1 T7): per element its id, type, {@code name},
 * {@code layer} and {@code attributes}, matching the spec's {@code baseline_element} columns.
 *
 * <p>{@code attributes} holds every other model feature of the element, sorted by name: attribute values as text
 * (enums by literal name), references as element ids. Left out, so that a diff reports each change once and only there:
 * <ul>
 * <li>containment and container references ({@code ownedParts}, {@code owner}...): a contained element is an element
 * of its own, so adding a part is one "added" and not also a "modified" owner (acceptance: "无多余项");</li>
 * <li>derived and transient features, and {@code DiagramLayout} elements: layout is not compared (spec P1 T7 step 2;
 * Sirius keeps the real layout outside the model anyway);</li>
 * <li>empty values, so "unset" and "empty" are the same.</li>
 * </ul>
 */
public final class ModelSnapshot {

    /** One element as stored in {@code baseline_element}. */
    public record ElementState(String id, String type, String name, String layer, Map<String, Object> attributes) {

        public ElementState {
            attributes = new TreeMap<>(attributes);
        }
    }

    private static final Set<String> COLUMNS = Set.of("id", "name", "layer");

    private ModelSnapshot() {
    }

    /** Every model element of the resources, by element id (sorted). */
    public static Map<String, ElementState> capture(Collection<Resource> resources) {
        Map<String, ElementState> elements = new TreeMap<>();
        for (Resource resource : resources) {
            for (TreeIterator<EObject> it = resource.getAllContents(); it.hasNext();) {
                if (it.next() instanceof ArchElement element && !(element instanceof DiagramLayout)) {
                    ElementState state = state(element);
                    elements.put(state.id(), state);
                }
            }
        }
        return elements;
    }

    static ElementState state(ArchElement element) {
        Map<String, Object> attributes = new TreeMap<>();
        for (EStructuralFeature feature : element.eClass().getEAllStructuralFeatures()) {
            if (COLUMNS.contains(feature.getName()) || feature.isDerived() || feature.isTransient()
                    || feature instanceof EReference reference && (reference.isContainment() || reference.isContainer())) {
                continue;
            }
            Object value = value(element, feature);
            if (value != null && !(value instanceof List<?> list && list.isEmpty())) {
                attributes.put(feature.getName(), value);
            }
        }
        return new ElementState(ElementIds.of(element), element.eClass().getName(), element.getName(),
                element.getLayer() == null ? null : element.getLayer().getName(), attributes);
    }

    private static Object value(EObject element, EStructuralFeature feature) {
        if (!element.eIsSet(feature) && !(feature instanceof EAttribute attribute && attribute.getDefaultValue() != null)) {
            return null;
        }
        Object raw = element.eGet(feature);
        if (feature.isMany()) {
            List<String> values = new ArrayList<>();
            for (Object item : (Collection<?>) raw) {
                values.add(text(item));
            }
            return values;
        }
        return raw == null ? null : text(raw);
    }

    private static String text(Object value) {
        if (value instanceof EObject other) {
            return other.eClass().getEPackage() == SysarchPackage.eINSTANCE ? ElementIds.of(other) : String.valueOf(other);
        }
        return value instanceof Enumerator enumerator ? enumerator.getName() : Objects.toString(value);
    }
}

package com.example.sysarch.baseline;

import com.example.sysarch.baseline.ModelSnapshot.ElementState;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.SysarchPackage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Builds model elements back from stored element states (P2 T3: a branch workspace from its baseline; T4 applies
 * merged states the same way). The inverse of {@link ModelSnapshot#state}: generic over the metamodel, by reflection,
 * so a new feature needs no code here.
 *
 * <p>Every element gets its element id as {@code ArchElement.id}, also those whose id was their position in the
 * document (elements created in Sirius' explorer without an id), so it keeps its identity in the new document.
 * Siblings come in element id order: a snapshot does not record the order within a containment list.
 */
public final class SnapshotModels {

    /** A state that cannot be rebuilt (unknown type or feature, a dangling reference). */
    public static class UnbuildableException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public UnbuildableException(String message) {
            super(message);
        }
    }

    private SnapshotModels() {
    }

    /**
     * New elements for the states, contained as recorded.
     *
     * @return the top-level elements (to add to a document), in element id order
     * @throws UnbuildableException when a state cannot be rebuilt; nothing is half-built
     */
    public static List<EObject> build(Map<String, ElementState> states) {
        Map<String, EObject> elements = new LinkedHashMap<>();
        for (ElementState state : new TreeMap<>(states).values()) {
            EObject element = EcoreUtil.create(eClass(state));
            elements.put(state.id(), element);
        }
        List<EObject> roots = new ArrayList<>();
        for (ElementState state : new TreeMap<>(states).values()) {
            EObject element = elements.get(state.id());
            set(element, state, elements);
            Object container = state.attributes().get(ModelSnapshot.CONTAINER);
            if (container == null || "".equals(container)) {
                roots.add(element);
            } else {
                contain(elements, state, element, container.toString());
            }
        }
        return roots;
    }

    /** Sets an element's features to a state (references resolved among {@code elements}). Containment is left alone. */
    public static void set(EObject element, ElementState state, Map<String, EObject> elements) {
        ArchElement arch = (ArchElement) element;
        arch.setId(state.id());
        arch.setName(state.name());
        arch.setLayer(state.layer() == null ? null : Layer.getByName(state.layer()));
        EClass eClass = element.eClass();
        for (EStructuralFeature feature : eClass.getEAllStructuralFeatures()) {
            if (!settable(feature)) {
                continue;
            }
            Object value = state.attributes().get(feature.getName());
            if (value == null) {
                element.eUnset(feature);
            } else if (feature.isMany()) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) element.eGet(feature);
                List<Object> values = new ArrayList<>();
                for (Object item : (Collection<?>) value) {
                    values.add(value(feature, item.toString(), elements, state));
                }
                list.clear();
                list.addAll(values);
            } else {
                element.eSet(feature, value(feature, value.toString(), elements, state));
            }
        }
        for (String key : state.attributes().keySet()) {
            if (!key.startsWith("$") && eClass.getEStructuralFeature(key) == null) {
                throw new UnbuildableException(state.id() + ": " + eClass.getName() + " has no feature '" + key + "'");
            }
        }
    }

    /** The features a state holds (see {@link ModelSnapshot}): not id/name/layer, not derived, transient or containment. */
    static boolean settable(EStructuralFeature feature) {
        String name = feature.getName();
        return !("id".equals(name) || "name".equals(name) || "layer".equals(name)) && !feature.isDerived() && !feature.isTransient()
                && feature.isChangeable() && !(feature instanceof EReference reference && (reference.isContainment() || reference.isContainer()));
    }

    private static Object value(EStructuralFeature feature, String text, Map<String, EObject> elements, ElementState state) {
        if (feature instanceof EReference) {
            EObject target = elements.get(text);
            if (target == null) {
                throw new UnbuildableException(state.id() + "." + feature.getName() + " refers to " + text + ", which is not in the snapshot");
            }
            return target;
        }
        EDataType type = ((EAttribute) feature).getEAttributeType();
        if (type instanceof EEnum eEnum) {
            EEnumLiteral literal = eEnum.getEEnumLiteral(text);
            if (literal == null) {
                throw new UnbuildableException(state.id() + "." + feature.getName() + ": no literal '" + text + "' in " + eEnum.getName());
            }
            return literal.getInstance();
        }
        return EcoreUtil.createFromString(type, text);
    }

    @SuppressWarnings("unchecked")
    private static void contain(Map<String, EObject> elements, ElementState state, EObject element, String containerId) {
        EObject container = elements.get(containerId);
        Object featureName = state.attributes().get(ModelSnapshot.FEATURE);
        EStructuralFeature feature = container == null || featureName == null ? null : container.eClass().getEStructuralFeature(featureName.toString());
        if (!(feature instanceof EReference reference) || !reference.isContainment()) {
            throw new UnbuildableException(state.id() + ": cannot be placed in " + containerId + "." + featureName);
        }
        if (reference.isMany()) {
            ((List<Object>) container.eGet(reference)).add(element);
        } else {
            container.eSet(reference, element);
        }
    }

    private static EClass eClass(ElementState state) {
        if (SysarchPackage.eINSTANCE.getEClassifier(state.type()) instanceof EClass eClass && !eClass.isAbstract()) {
            return eClass;
        }
        throw new UnbuildableException(state.id() + ": unknown element type '" + state.type() + "'");
    }
}

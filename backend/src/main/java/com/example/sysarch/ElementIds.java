package com.example.sysarch;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * The id of a model element everywhere outside the model: diagrams, Polarion bindings, consistency reports. It is
 * the element's ID attribute ({@code ArchElement.id}) if set, otherwise its id in the document (the Sirius Web
 * object id, which elements created in the explorer have).
 */
public final class ElementIds {

    private ElementIds() {
    }

    public static String of(EObject element) {
        for (var adapter : element.eAdapters()) if (adapter instanceof ProjectionIdentity identity) return identity.id;
        String id = EcoreUtil.getID(element);
        if (id != null) {
            return id;
        }
        return element.eResource() != null ? element.eResource().getURIFragment(element) : EcoreUtil.getURI(element).fragment();
    }
    /** Request-local decoded objects only: give projection engines the persistent UUID without changing business id attributes. */
    public static void bindProjectionId(EObject element, java.util.UUID uuid) {
        java.util.Objects.requireNonNull(uuid);
        for (var adapter : element.eAdapters()) if (adapter instanceof ProjectionIdentity identity) {
            if (!identity.id.equals(uuid.toString())) throw new IllegalArgumentException("Projection identity already bound");
            return;
        }
        element.eAdapters().add(new ProjectionIdentity(uuid.toString()));
    }
    private static final class ProjectionIdentity extends org.eclipse.emf.common.notify.impl.AdapterImpl {
        private final String id;
        private ProjectionIdentity(String id) { this.id = id; }
    }
}

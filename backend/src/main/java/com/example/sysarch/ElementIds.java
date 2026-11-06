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
        String id = EcoreUtil.getID(element);
        if (id != null) {
            return id;
        }
        return element.eResource() != null ? element.eResource().getURIFragment(element) : EcoreUtil.getURI(element).fragment();
    }
}

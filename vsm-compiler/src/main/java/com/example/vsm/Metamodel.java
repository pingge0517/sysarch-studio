package com.example.vsm;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;

/**
 * The metamodel VSM files are checked against, read from {@code metamodel/sysarch.ecore} (not from the generated
 * Java), so vsm-compiler does not depend on the backend.
 */
final class Metamodel {

    private final EPackage ePackage;

    private Metamodel(EPackage ePackage) {
        this.ePackage = ePackage;
    }

    static Metamodel load(Path ecoreFile) {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        resourceSet.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
        Resource resource = resourceSet.getResource(URI.createFileURI(ecoreFile.toAbsolutePath().toString()), true);
        return new Metamodel((EPackage) resource.getContents().get(0));
    }

    String nsUri() {
        return this.ePackage.getNsURI();
    }

    String nsPrefix() {
        return this.ePackage.getNsPrefix();
    }

    /** Resolves {@code nsPrefix:EClass}; null if the prefix or the class is unknown. */
    EClass resolveClass(String ref) {
        int colon = ref.indexOf(':');
        if (colon < 0 || !ref.substring(0, colon).equals(this.nsPrefix())) {
            return null;
        }
        return this.ePackage.getEClassifier(ref.substring(colon + 1)) instanceof EClass eClass ? eClass : null;
    }

    /** {@code sysarch:PartDefinition} becomes {@code sysarch::PartDefinition}, Sirius Web's type notation. */
    static String domainType(String ref) {
        return ref.replace(":", "::");
    }

    String ref(EClass eClass) {
        return this.nsPrefix() + ":" + eClass.getName();
    }

    static EStructuralFeature feature(EClass eClass, String name) {
        return eClass.getEStructuralFeature(name);
    }

    static EReference reference(EClass eClass, String name) {
        return eClass.getEStructuralFeature(name) instanceof EReference reference ? reference : null;
    }

    /**
     * The containment references of the package that can hold an {@code eClass} (P3 T3: a border node with
     * {@code affiliationFeature: eContainer} is drawn on whatever contains its element).
     */
    List<EReference> containmentsOf(EClass eClass) {
        return this.ePackage.getEClassifiers().stream()
                .filter(EClass.class::isInstance)
                .map(EClass.class::cast)
                .flatMap(c -> c.getEReferences().stream())
                .filter(r -> r.isContainment() && r.getEReferenceType().isSuperTypeOf(eClass))
                .toList();
    }

    /** {@code eClass} and every class in the package that inherits from it. */
    List<EClass> selfAndSubclasses(EClass eClass) {
        return this.ePackage.getEClassifiers().stream()
                .filter(EClass.class::isInstance)
                .map(EClass.class::cast)
                .filter(eClass::isSuperTypeOf)
                .toList();
    }
}

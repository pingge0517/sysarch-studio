package com.example.sysarch.validation;

import com.example.sysarch.model.ArchElement;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Save-time veto (spec §3.2, T6 step 2), for the constraints where an edit must be refused rather than flagged:
 * layer skipping and self-allocation on Allocate, and the three connection rules when ends change outside the
 * canvas (the canvas already checks them on drop).
 *
 * <p>{@link #check} gets one recorded edit. It validates every element the edit touched, plus every element that
 * references one of them (changing a block's layer can break an Allocate that points at it). If the edit
 * <i>introduced</i> a veto-level violation, it is rolled back and the violations are returned. Violations that
 * already existed before the edit never cause a rollback, so old data does not block unrelated edits.
 *
 * <p>Incomplete elements are not vetoed: a constraint over ends that are still empty (a new Allocate from the
 * explorer) only takes effect once they are all set.
 *
 * <p>NameNotEmpty and InterfaceTypeRequired are not vetoed: those fields are being typed in, and they are marked
 * red in the properties view instead ({@link SysarchEValidator}).
 */
public final class ChangeGuard {

    public static final Set<String> VETOED = Set.of("LayerAdjacentOnly", "NoSelfAllocate", "PortInterfaceAssignable", "NoSelfLoop", "DirectionCompatible",
            "UnitsMatch"); // P3 T3 (user's decision): a binding between different units is rolled back like a port connection

    private final ConstraintValidator constraints;

    public ChangeGuard(ConstraintValidator constraints) {
        this.constraints = Objects.requireNonNull(constraints);
    }

    /**
     * @param change the edit, as recorded by an EMF {@code ChangeRecorder}; the model is in the "after" state
     * @return the violations the edit introduced. Empty: the edit is kept. Not empty: the edit has been rolled
     *         back and the model is in the "before" state.
     */
    public List<ValidationError> check(ResourceSet resourceSet, ChangeDescription change) {
        Set<EObject> touched = touched(resourceSet, change);
        Map<EObject, List<ValidationError>> after = this.vetoedViolations(touched);
        if (after.isEmpty()) {
            return List.of();
        }

        change.applyAndReverse(); // model in the "before" state now
        Map<EObject, List<ValidationError>> before = this.vetoedViolations(touched);
        List<ValidationError> introduced = new ArrayList<>();
        after.forEach((element, errors) -> {
            Set<String> existing = codes(before.getOrDefault(element, List.of()));
            errors.stream().filter(error -> !existing.contains(error.code())).forEach(introduced::add);
        });

        if (introduced.isEmpty()) {
            change.applyAndReverse(); // nothing new: back to "after", the edit is kept
        }
        return introduced;
    }

    /**
     * The features each existing element had changed by the edit, for the notice of a veto ({@link VetoNotices}). Take
     * it before {@link #check}: a rollback reverses the change description. Elements created by the edit are left
     * out, because a rollback removes them.
     */
    public static Map<EObject, Set<EStructuralFeature>> edited(ChangeDescription change) {
        Map<EObject, Set<EStructuralFeature>> edited = new IdentityHashMap<>();
        Set<EObject> created = new java.util.HashSet<>(change.getObjectsToDetach());
        change.getObjectChanges().map().forEach((element, featureChanges) -> {
            if (!created.contains(element)) {
                Set<EStructuralFeature> features = new LinkedHashSet<>();
                featureChanges.forEach(featureChange -> features.add(featureChange.getFeature()));
                edited.put(element, features);
            }
        });
        return edited;
    }

    /** Elements the edit changed or created, and the elements that reference any of them. */
    static Set<EObject> touched(ResourceSet resourceSet, ChangeDescription change) {
        Set<EObject> changed = new LinkedHashSet<>(change.getObjectChanges().keySet());
        changed.addAll(change.getObjectsToDetach()); // objects created during the edit
        Set<EObject> touched = new LinkedHashSet<>(changed);
        EcoreUtil.UsageCrossReferencer.findAll(changed, resourceSet).values()
                .forEach(settings -> settings.forEach(setting -> touched.add(setting.getEObject())));
        return touched;
    }

    /** Veto-level violations per element, for elements that are still in a resource. */
    private Map<EObject, List<ValidationError>> vetoedViolations(Set<EObject> elements) {
        Map<EObject, List<ValidationError>> result = new IdentityHashMap<>();
        for (EObject element : elements) {
            if (element instanceof ArchElement archElement && element.eResource() != null) {
                List<ValidationError> vetoed = this.constraints.validate(archElement).stream()
                        .filter(e -> VETOED.contains(e.code()) && complete(element, e.code()))
                        .toList();
                if (!vetoed.isEmpty()) {
                    result.put(element, vetoed);
                }
            }
        }
        return result;
    }

    /**
     * Incomplete is not wrong: a new Allocate or Connection has empty ends until the user fills them in, and the
     * OCL invariants evaluate to false on empty ends. Only veto once every feature the constraint is about is set;
     * until then EMF's required-feature check marks the empty fields red.
     */
    public static boolean complete(EObject element, String code) {
        for (String name : SysarchEValidator.FEATURES.getOrDefault(code, List.of())) {
            EStructuralFeature feature = element.eClass().getEStructuralFeature(name);
            if (feature != null && !feature.isMany() && element.eGet(feature) == null) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> codes(List<ValidationError> errors) {
        Set<String> codes = new LinkedHashSet<>();
        errors.forEach(error -> codes.add(error.code()));
        return codes;
    }
}

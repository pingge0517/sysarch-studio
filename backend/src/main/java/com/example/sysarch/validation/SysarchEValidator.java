package com.example.sysarch.validation;

import com.example.sysarch.model.ArchElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.DiagnosticChain;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.util.EObjectValidator;

/**
 * EMF validator for the sysarch package: EMF's basic checks (required features, multiplicities) plus our OCL
 * invariants from {@link ConstraintValidator}.
 *
 * <p>Registered in Sirius Web's {@code EValidator.Registry}, it drives Sirius' own validation UI. A diagnostic's
 * data holds the element and the feature(s) the constraint is about, so the properties view marks exactly those
 * fields red (spec §3.2 "属性面板字段标红"), and the Validation view lists every violation in the model.
 */
public final class SysarchEValidator implements EValidator {

    public static final String SOURCE = "com.example.sysarch";

    /** Feature(s) each constraint is about, i.e. the properties fields to mark, in a fixed order (diagnostic codes). */
    static final Map<String, List<String>> FEATURES = orderedFeatures();

    /** Diagnostic code of a vetoed-edit notice ({@link VetoNotices}), after the constraint codes. */
    public static final int VETO_NOTICE = 100;

    private final ConstraintValidator constraints;

    private final VetoNotices notices;

    public SysarchEValidator(ConstraintValidator constraints, VetoNotices notices) {
        this.constraints = Objects.requireNonNull(constraints);
        this.notices = Objects.requireNonNull(notices);
    }

    @Override
    public boolean validate(EObject eObject, DiagnosticChain diagnostics, Map<Object, Object> context) {
        return this.validate(eObject.eClass(), eObject, diagnostics, context);
    }

    @Override
    public boolean validate(EClass eClass, EObject eObject, DiagnosticChain diagnostics, Map<Object, Object> context) {
        boolean valid = EObjectValidator.INSTANCE.validate(eClass, eObject, diagnostics, context);
        if (eObject instanceof ArchElement element) {
            for (ValidationError error : this.constraints.validate(element)) {
                valid = false;
                if (diagnostics != null) {
                    diagnostics.add(new BasicDiagnostic(Diagnostic.ERROR, SOURCE, code(error.code()), error.message(), data(eObject, error.code())));
                }
            }
            for (var notice : this.notices.noticesFor(eObject).entrySet()) {
                valid = false;
                if (diagnostics != null) {
                    List<Object> data = new ArrayList<>();
                    data.add(eObject);
                    data.addAll(notice.getValue());
                    diagnostics.add(new BasicDiagnostic(Diagnostic.ERROR, SOURCE, VETO_NOTICE, notice.getKey(), data.toArray()));
                }
            }
        }
        return valid;
    }

    @Override
    public boolean validate(EDataType eDataType, Object value, DiagnosticChain diagnostics, Map<Object, Object> context) {
        return EObjectValidator.INSTANCE.validate(eDataType, value, diagnostics, context);
    }

    /** The element first, then the features: Sirius matches diagnostics to widgets by these. */
    private static Object[] data(EObject element, String code) {
        List<Object> data = new ArrayList<>();
        data.add(element);
        for (String name : FEATURES.getOrDefault(code, List.of())) {
            EStructuralFeature feature = element.eClass().getEStructuralFeature(name);
            if (feature != null) {
                data.add(feature);
            }
        }
        return data.toArray();
    }

    /** Diagnostic code: 1-based position of the constraint in {@link #FEATURES}. */
    private static int code(String constraint) {
        return List.copyOf(FEATURES.keySet()).indexOf(constraint) + 1;
    }

    private static Map<String, List<String>> orderedFeatures() {
        Map<String, List<String>> features = new LinkedHashMap<>();
        features.put("NameNotEmpty", List.of("name"));
        features.put("InterfaceTypeRequired", List.of("interfaceType", "direction"));
        features.put("PortInterfaceAssignable", List.of("source", "target"));
        features.put("NoSelfLoop", List.of("source", "target"));
        features.put("DirectionCompatible", List.of("source", "target"));
        features.put("LayerAdjacentOnly", List.of("from", "to"));
        features.put("NoSelfAllocate", List.of("from", "to"));
        features.put("ProtocolRequired", List.of("protocol"));
        // P3 T1 (metamodel v1.3); appended, so the codes of the constraints above stay the same
        features.put("HasExpression", List.of("expression"));
        features.put("ParamsHaveUnit", List.of("parameters"));
        features.put("DirectionKnown", List.of("direction"));
        // P3 T2 (spec §2.3): three OCL, two Java (UnitConsistent, Reachability); appended
        features.put("NoDuplicateParamName", List.of("parameters"));
        features.put("SingleInitial", List.of("states"));
        features.put("GuardNotEmpty", List.of("guard", "trigger"));
        features.put("UnitConsistent", List.of("parameters"));
        features.put("Reachability", List.of("states"));
        return Collections.unmodifiableMap(features);
    }
}

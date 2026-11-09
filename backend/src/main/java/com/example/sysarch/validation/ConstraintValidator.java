package com.example.sysarch.validation;

import com.example.sysarch.model.Allocate;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.ConnectionKind;
import com.example.sysarch.model.InterfaceDef;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.model.SysarchPackage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.ocl.pivot.ExpressionInOCL;
import org.eclipse.ocl.pivot.utilities.OCL;
import org.eclipse.ocl.pivot.utilities.ParserException;
import org.eclipse.ocl.xtext.essentialocl.EssentialOCLStandaloneSetup;

/**
 * Constraint checks for sysarch models (spec §3.2, T6), evaluated by Eclipse OCL (Pivot).
 *
 * <p>The invariants are not re-implemented in Java. They are read from the
 * {@code http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot} annotations of {@code metamodel/sysarch.ecore} (via
 * the generated package), compiled once, and evaluated on the element's class and all its superclasses. This
 * class only adds what the UI needs on top: the message and which elements to highlight.
 *
 * <p>Two entry points match the two trigger points of spec §3.2:
 * <ul>
 * <li>{@link #validate(ArchElement)}: every invariant. Called before an edit is committed (save).</li>
 * <li>{@link #validateEdgeDrop(Port, Port, ConnectionKind)}: the constraints marked "on edge drop" in §3.2,
 * checked on a connection that is not yet in the model. A rejected edge is therefore never stored.</li>
 * </ul>
 *
 * <p>{@code PortInterfaceAssignable} (P1 T2) replaced P0's string comparison {@code PortTypeCompatible}: both ports
 * need an interface, and the two must be the same {@code InterfaceDef} or one an ancestor of the other
 * ({@link InterfaceCompatibility}). A port without an interface is never assignable.
 *
 * <p>Not thread-safe internally; the public methods are synchronized. Call {@link #close()} to release OCL.
 */
public final class ConstraintValidator implements AutoCloseable {

    static final String PIVOT_ANNOTATION = "http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot";

    /** Constraints checked when a connection is dropped on the canvas (spec §3.2 "连线落位时"). */
    static final Set<String> EDGE_DROP_CONSTRAINTS = Set.of("PortInterfaceAssignable", "NoSelfLoop", "DirectionCompatible");

    private final OCL ocl;

    /** EClass -> (constraint name -> compiled invariant), in Ecore order. */
    private final Map<EClass, Map<String, ExpressionInOCL>> invariants = new LinkedHashMap<>();

    private boolean closed;

    public ConstraintValidator() {
        this(SysarchPackage.eINSTANCE);
    }

    ConstraintValidator(EPackage ePackage) {
        EssentialOCLStandaloneSetup.doSetup();
        this.ocl = OCL.newInstance(EPackage.Registry.INSTANCE);
        for (EClassifier classifier : ePackage.getEClassifiers()) {
            if (classifier instanceof EClass eClass) {
                EAnnotation annotation = eClass.getEAnnotation(PIVOT_ANNOTATION);
                if (annotation != null) {
                    Map<String, ExpressionInOCL> compiled = new LinkedHashMap<>();
                    annotation.getDetails().forEach(detail -> compiled.put(detail.getKey(), this.compile(eClass, detail.getKey(), detail.getValue())));
                    this.invariants.put(eClass, compiled);
                }
            }
        }
    }

    /** Names of all loaded invariants, per defining class (for diagnostics and tests). */
    public synchronized Map<String, List<String>> constraintNames() {
        Map<String, List<String>> names = new LinkedHashMap<>();
        this.invariants.forEach((eClass, compiled) -> names.put(eClass.getName(), List.copyOf(compiled.keySet())));
        return names;
    }

    /** Unified entry point (spec T6 step 1): every invariant of the element's class and superclasses. */
    public synchronized List<ValidationError> validate(ArchElement element) {
        return this.check(element, null);
    }

    /** Validates several elements, e.g. everything an edit touched, before it is committed. */
    public synchronized List<ValidationError> validateAll(Collection<? extends EObject> elements) {
        List<ValidationError> errors = new ArrayList<>();
        for (EObject element : elements) {
            if (element instanceof ArchElement archElement) {
                errors.addAll(this.check(archElement, null));
            }
        }
        return errors;
    }

    /**
     * Checks a connection the user is about to drop, without adding anything to the model. Only the edge-drop
     * constraints run here; name and other save-time checks follow when the edit is committed.
     */
    public synchronized List<ValidationError> validateEdgeDrop(Port source, Port target, ConnectionKind kind) {
        Connection candidate = SysarchFactory.eINSTANCE.createConnection(); // never added to a resource
        candidate.setSource(source); // Connection.source/target have no opposite, so the ports are not modified
        candidate.setTarget(target);
        candidate.setKind(kind);
        return this.check(candidate, EDGE_DROP_CONSTRAINTS);
    }

    @Override
    public synchronized void close() {
        if (!this.closed) {
            this.closed = true;
            this.ocl.dispose();
        }
    }

    private List<ValidationError> check(ArchElement element, Set<String> only) {
        if (this.closed) {
            throw new IllegalStateException("validator is closed");
        }
        List<ValidationError> errors = new ArrayList<>();
        EClass eClass = element.eClass();
        List<EClass> hierarchy = Stream.concat(eClass.getEAllSuperTypes().stream(), Stream.of(eClass)).toList();
        for (EClass type : hierarchy) {
            Map<String, ExpressionInOCL> compiled = this.invariants.getOrDefault(type, Map.of());
            for (Map.Entry<String, ExpressionInOCL> invariant : compiled.entrySet()) {
                if ((only == null || only.contains(invariant.getKey())) && !this.holds(element, invariant.getValue())) {
                    errors.add(error(invariant.getKey(), element));
                }
            }
        }
        return errors;
    }

    /** An invariant that evaluates to false, null or invalid (e.g. a missing port) is violated. */
    private boolean holds(EObject element, ExpressionInOCL invariant) {
        try {
            return this.ocl.check(element, invariant);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private ExpressionInOCL compile(EClass eClass, String name, String expression) {
        try {
            return this.ocl.createInvariant(eClass, expression);
        } catch (ParserException e) {
            throw new IllegalStateException("invalid OCL invariant " + eClass.getName() + "::" + name + ": " + e.getMessage(), e);
        }
    }

    private static ValidationError error(String code, ArchElement element) {
        String id = element.getId();
        return switch (element) {
            case Connection connection -> new ValidationError(code, id, connectionMessage(code, connection),
                    ids(connection.getSource(), connection.getTarget()));
            case Allocate allocate -> new ValidationError(code, id, allocateMessage(code, allocate), ids(allocate.getFrom(), allocate.getTo()));
            case InterfaceDef interfaceDef when code.equals("ProtocolRequired") -> new ValidationError(code, id,
                    "接口「" + name(interfaceDef) + "」未设置协议", ids(interfaceDef));
            case Port port when code.equals("InterfaceTypeRequired") -> new ValidationError(code, id,
                    "端口「" + name(port) + "」未设置接口类型（仅 INOUT 端口可以不设）", ids(port));
            default -> code.equals("NameNotEmpty") ? new ValidationError(code, id, "名称不能为空", ids(element))
                    : new ValidationError(code, id, "违反约束 " + code, ids(element));
        };
    }

    private static String connectionMessage(String code, Connection connection) {
        Port source = connection.getSource();
        Port target = connection.getTarget();
        return switch (code) {
            case "PortInterfaceAssignable" -> assignableMessage(source, target);
            case "NoSelfLoop" -> "连接的两端不能是同一个端口";
            case "DirectionCompatible" -> "端口方向不兼容：" + direction(source) + " → " + direction(target);
            case "NameNotEmpty" -> "名称不能为空";
            default -> "违反约束 " + code;
        };
    }

    /** Why two ports are not assignable: a missing interface first, otherwise the two unrelated interfaces. */
    private static String assignableMessage(Port source, Port target) {
        if (source != null && source.getInterfaceType() == null) {
            return "端口接口不可赋值：源端口未设置接口，请先给端口设置接口";
        }
        if (target != null && target.getInterfaceType() == null) {
            return "端口接口不可赋值：目标端口未设置接口，请先给端口设置接口";
        }
        return "端口接口不可赋值：「" + type(source) + "」与「" + type(target) + "」不在同一继承链上";
    }

    /** Spec §3.2: a rejected allocation says e.g. "跨层跳过：LA → EPBS". */
    private static String allocateMessage(String code, Allocate allocate) {
        Layer from = allocate.getFrom() == null ? null : allocate.getFrom().getLayer();
        Layer to = allocate.getTo() == null ? null : allocate.getTo().getLayer();
        return switch (code) {
            case "LayerAdjacentOnly" -> from != null && to != null && to.getValue() > from.getValue() + 1
                    ? "跨层跳过：" + abbreviation(from) + " → " + abbreviation(to)
                    : "分配只能指向相邻的下一层：" + abbreviation(from) + " → " + abbreviation(to);
            case "NoSelfAllocate" -> "不能把元素分配给它自己";
            case "NameNotEmpty" -> "名称不能为空";
            default -> "违反约束 " + code;
        };
    }

    /** Arcadia layer abbreviations used in messages. */
    static String abbreviation(Layer layer) {
        if (layer == null) {
            return "?";
        }
        return switch (layer) {
            case OPERATIONAL -> "OA";
            case SYSTEM -> "SA";
            case LOGICAL -> "LA";
            case PHYSICAL -> "PA";
            case EPBS -> "EPBS";
        };
    }

    private static List<String> ids(ArchElement... elements) {
        List<String> ids = new ArrayList<>();
        for (ArchElement element : elements) {
            if (element != null && element.getId() != null && !ids.contains(element.getId())) {
                ids.add(element.getId());
            }
        }
        return ids;
    }

    private static String name(ArchElement element) {
        return element.getName() == null ? "?" : element.getName();
    }

    private static String type(Port port) {
        if (port == null || port.getInterfaceType() == null) {
            return "未设置";
        }
        return port.getInterfaceType().getName() == null ? "?" : port.getInterfaceType().getName();
    }

    private static String direction(Port port) {
        return port == null ? "?" : port.getDirection().getLiteral();
    }
}

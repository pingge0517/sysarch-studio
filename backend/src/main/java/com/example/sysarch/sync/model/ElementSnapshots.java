package com.example.sysarch.sync.model;

import com.example.sysarch.ElementIds;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.sync.model.ModelGateway.ElementSnapshot;
import com.example.sysarch.validation.ChangeGuard;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.validation.ValidationError;

import java.util.List;

/** Reads the {@link ElementSnapshot} of an element; must run where the model may be read (the editing context thread). */
public final class ElementSnapshots {

    private ElementSnapshots() {
    }

    public static ElementSnapshot of(ArchElement element, ConstraintValidator constraints) {
        String source = element instanceof Connection connection && connection.getSource() != null ? ElementIds.of(connection.getSource()) : null;
        String target = element instanceof Connection connection && connection.getTarget() != null ? ElementIds.of(connection.getTarget()) : null;
        String text = element instanceof Requirement requirement ? requirement.getText() : null;
        Requirement requirement = element instanceof Requirement r ? r : null;
        java.util.List<String> satisfiedBy = requirement == null ? java.util.List.of() : requirement.getSatisfiedBy().stream().map(ElementIds::of).toList();
        java.util.List<String> verifiedBy = requirement == null ? java.util.List.of() : requirement.getVerifiedBy().stream().map(ElementIds::of).toList();
        return new ElementSnapshot(ElementIds.of(element), element.eClass().getName(), element.getName(), element.getLayer().getLiteral(),
                element.getStereotypes(), source, target, text, element.getRev(), errors(element, constraints), satisfiedBy, verifiedBy,
                requirement == null ? null : requirement.getReqId());
    }

    /**
     * The element's constraint violations, where incomplete is not wrong (as on the save path, {@link ChangeGuard#complete}):
     * a vetoed constraint over ends that are not all set yet, such as a new Allocate, is no violation. Without this the
     * consistency report showed an unfinished Allocate as R4 (found in the browser acceptance check).
     */
    private static List<ValidationError> errors(ArchElement element, ConstraintValidator constraints) {
        return constraints.validate(element).stream()
                .filter(error -> !ChangeGuard.VETOED.contains(error.code()) || ChangeGuard.complete(element, error.code()))
                .toList();
    }
}

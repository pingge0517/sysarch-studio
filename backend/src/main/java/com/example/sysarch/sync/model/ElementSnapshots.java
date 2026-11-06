package com.example.sysarch.sync.model;

import com.example.sysarch.ElementIds;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.sync.model.ModelGateway.ElementSnapshot;
import com.example.sysarch.validation.ConstraintValidator;

/** Reads the {@link ElementSnapshot} of an element; must run where the model may be read (the editing context thread). */
public final class ElementSnapshots {

    private ElementSnapshots() {
    }

    public static ElementSnapshot of(ArchElement element, ConstraintValidator constraints) {
        String source = element instanceof Connection connection && connection.getSource() != null ? ElementIds.of(connection.getSource()) : null;
        String target = element instanceof Connection connection && connection.getTarget() != null ? ElementIds.of(connection.getTarget()) : null;
        String text = element instanceof Requirement requirement ? requirement.getText() : null;
        return new ElementSnapshot(ElementIds.of(element), element.eClass().getName(), element.getName(), element.getLayer().getLiteral(),
                element.getStereotypes(), source, target, text, element.getRev(), constraints.validate(element));
    }
}

package com.example.sysarch.access;

/** A user's role in a project does not allow an action (403; P2 T8). Already audited when thrown. */
public class ForbiddenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ToolRole role;

    public ForbiddenException(String message, ToolRole role) {
        super(message);
        this.role = role;
    }

    public ToolRole role() {
        return this.role;
    }
}

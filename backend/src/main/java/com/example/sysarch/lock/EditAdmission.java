package com.example.sysarch.lock;

import com.example.sysarch.identity.Editors.Editor;
import com.example.sysarch.session.SessionService;
import com.example.sysarch.validation.ValidationError;

import java.util.List;
import java.util.Objects;

import org.eclipse.emf.ecore.change.ChangeDescription;
import org.springframework.stereotype.Component;

/**
 * Whether a user's edit may go through (P2 T1, T2): the element locks first, then the project's limit of concurrent
 * editors. Called for Sirius edits ({@code ConstraintEnforcement.Guard}, which rolls the edit back) and for our own
 * model tasks ({@code ProjectModels}, which undoes the task and answers 423 / 429). System edits are never checked.
 */
@Component
public class EditAdmission {

    public static final String LOCKED = "LOCKED";

    public static final String SESSIONS_FULL = "SESSIONS_FULL";

    /** P2 T8: the user's role in the project does not allow modelling (403). */
    public static final String FORBIDDEN = "FORBIDDEN";

    private final com.example.sysarch.access.Access access;

    private final LockEnforcement locks;

    private final SessionService sessions;

    private final com.example.sysarch.config.FeatureProperties features;

    public EditAdmission(LockEnforcement locks, SessionService sessions, com.example.sysarch.access.Access access) {
        this(locks, sessions, access, new com.example.sysarch.config.FeatureProperties());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EditAdmission(LockEnforcement locks, SessionService sessions, com.example.sysarch.access.Access access, com.example.sysarch.config.FeatureProperties features) {
        this.features = Objects.requireNonNull(features);
        this.access = Objects.requireNonNull(access);
        this.locks = Objects.requireNonNull(locks);
        this.sessions = Objects.requireNonNull(sessions);
    }

    /**
     * Checks a recorded edit and, when it may go through, opens or keeps the user's session. First (P2 T8) the user's
     * role must allow modelling: an anonymous editor never may; an editor with a principal is checked; an editor with
     * only a name was authorized by its caller (a merge).
     *
     * @throws com.example.sysarch.access.ForbiddenException when the role does not allow modelling (audited)
     * @throws LockService.LockedException when it changed elements someone else holds
     * @throws SessionService.FullException when the user has no session and the project is full (or the edit is
     *         anonymous and the project is full)
     */
    public void check(String projectId, Editor editor, ChangeDescription change, String clientInfo) {
        if (editor.user() == null || editor.principal() != null) {
            this.access.require(editor.principal(), projectId, com.example.sysarch.access.Permission.MODEL_EDIT, null);
        }
        List<LockService.Conflict> held = this.features.isLocks() ? this.locks.conflicts(projectId, editor, change) : List.of();
        if (!held.isEmpty()) {
            throw new LockService.LockedException(held);
        }
        if (editor.user() == null) {
            this.sessions.checkAnonymous(projectId);
        } else {
            this.sessions.touch(projectId, editor.user(), clientInfo);
        }
    }

    /** After an edit went through: the user's lease on what it changed, no locks on what it deleted. */
    public void committed(String projectId, Editor editor, ChangeDescription change) {
        if (this.features.isLocks()) {
            this.locks.committed(projectId, editor, change);
        }
    }

    /** The same checks' refusal as the errors shown on the edited fields. */
    public static List<ValidationError> reasons(RuntimeException refusal) {
        if (refusal instanceof LockService.LockedException locked) {
            return locked.conflicts().stream().map(c -> new ValidationError(LOCKED, c.elementId(), LockService.describe(c), List.of(c.elementId()))).toList();
        }
        if (refusal instanceof com.example.sysarch.access.ForbiddenException) {
            return List.of(new ValidationError(FORBIDDEN, null, refusal.getMessage(), List.of()));
        }
        return List.of(new ValidationError(SESSIONS_FULL, null, refusal.getMessage(), List.of()));
    }
}

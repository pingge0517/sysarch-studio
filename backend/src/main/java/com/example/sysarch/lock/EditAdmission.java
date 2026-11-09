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

    private final LockEnforcement locks;

    private final SessionService sessions;

    public EditAdmission(LockEnforcement locks, SessionService sessions) {
        this.locks = Objects.requireNonNull(locks);
        this.sessions = Objects.requireNonNull(sessions);
    }

    /**
     * Checks a recorded edit and, when it may go through, opens or keeps the user's session.
     *
     * @throws LockService.LockedException when it changed elements someone else holds
     * @throws SessionService.FullException when the user has no session and the project is full (or the edit is
     *         anonymous and the project is full)
     */
    public void check(String projectId, Editor editor, ChangeDescription change, String clientInfo) {
        List<LockService.Conflict> held = this.locks.conflicts(projectId, editor, change);
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
        this.locks.committed(projectId, editor, change);
    }

    /** The same checks' refusal as the errors shown on the edited fields. */
    public static List<ValidationError> reasons(RuntimeException refusal) {
        if (refusal instanceof LockService.LockedException locked) {
            return locked.conflicts().stream().map(c -> new ValidationError(LOCKED, c.elementId(), LockService.describe(c), List.of(c.elementId()))).toList();
        }
        return List.of(new ValidationError(SESSIONS_FULL, null, refusal.getMessage(), List.of()));
    }
}

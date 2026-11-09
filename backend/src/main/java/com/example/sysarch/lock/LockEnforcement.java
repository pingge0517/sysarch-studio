package com.example.sysarch.lock;

import com.example.sysarch.identity.Editors.Editor;
import com.example.sysarch.model.ArchElement;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Applies the element locks to a recorded model edit (P2 T1, user's decisions "服务端否决并提示" and "编辑即自动加锁").
 * Sirius cannot show an element read-only to one user only, so the edit is checked on the server instead: an edit
 * that changes an element someone else holds is rolled back (by the caller) with the holder named; an edit that goes
 * through gives its user a server-managed lease on what it changed.
 *
 * <p>The protected elements of an edit are the existing elements whose own features it changed (so adding a part to
 * a block changes the block) and the elements it deleted. Elements it created are new, nobody can hold them.
 */
@Component
public class LockEnforcement {

    private final LockService locks;

    private final JdbcClient jdbc;

    private final Map<String, String> projectByEditingContext = new ConcurrentHashMap<>();

    public LockEnforcement(LockService locks, JdbcClient jdbc) {
        this.locks = Objects.requireNonNull(locks);
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    /** The edit's elements that someone other than the editor holds; empty: the edit may go through. */
    public List<LockService.Conflict> conflicts(String projectId, Editor editor, ChangeDescription change) {
        Set<String> ids = protectedElements(change);
        return this.locks.held(projectId, ids).values().stream().filter(lock -> !editor.holds(lock.ownerUser()))
                .map(lock -> new LockService.Conflict(lock.elementId(), lock.ownerUser(), lock.expiresAt())).sorted(
                        java.util.Comparator.comparing(LockService.Conflict::elementId))
                .toList();
    }

    /** After an edit that went through: the editor's lease on what it changed, and no locks on what it deleted. */
    public void committed(String projectId, Editor editor, ChangeDescription change) {
        Set<String> deleted = deletedElements(change);
        this.locks.dropElements(projectId, deleted);
        if (editor != null && editor.user() != null) {
            Set<String> changed = protectedElements(change);
            changed.removeAll(deleted);
            this.locks.touch(projectId, changed, editor.user());
        }
    }

    /** Only the deletions, for edits nobody is accountable for (system inputs). */
    public void deletedBySystem(String projectId, ChangeDescription change) {
        this.locks.dropElements(projectId, deletedElements(change));
    }

    /** The Sirius project of an editing context (cached; Sirius' {@code project_semantic_data}, read only). */
    public Optional<String> projectOf(String editingContextId) {
        String cached = this.projectByEditingContext.get(editingContextId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<String> project = this.jdbc.sql("SELECT CAST(project_id AS VARCHAR) FROM project_semantic_data WHERE CAST(semantic_data_id AS VARCHAR) = :ec")
                .param("ec", editingContextId).query(String.class).optional();
        project.ifPresent(p -> this.projectByEditingContext.put(editingContextId, p));
        return project;
    }

    /** Existing elements the edit changed, and elements it deleted, by element id. */
    static Set<String> protectedElements(ChangeDescription change) {
        Set<EObject> created = Set.copyOf(change.getObjectsToDetach());
        Set<String> ids = new LinkedHashSet<>();
        change.getObjectChanges().keySet().stream().filter(e -> !created.contains(e)).forEach(e -> id(e).ifPresent(ids::add));
        ids.addAll(deletedElements(change));
        return ids;
    }

    /** Elements the edit removed from the model (they would be re-attached by reversing it). */
    static Set<String> deletedElements(ChangeDescription change) {
        Set<String> ids = new LinkedHashSet<>();
        change.getObjectsToAttach().stream().filter(e -> e.eResource() == null).forEach(e -> id(e).ifPresent(ids::add));
        return ids;
    }

    /** Only sysarch elements with an id can be locked (the lock API names elements by {@code ArchElement.id}). */
    private static Optional<String> id(EObject element) {
        if (!(element instanceof ArchElement)) {
            return Optional.empty();
        }
        String id = EcoreUtil.getID(element);
        return id == null || id.isBlank() ? Optional.empty() : Optional.of(id);
    }
}

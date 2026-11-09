package com.example.sysarch.project;

import com.example.sysarch.identity.Editors;
import com.example.sysarch.identity.Editors.Editor;
import com.example.sysarch.lock.EditAdmission;
import com.example.sysarch.lock.LockService;
import com.example.sysarch.session.SessionService;
import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.sync.SyncProperties;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.components.collaborative.api.ChangeDescription;
import org.eclipse.sirius.components.collaborative.api.ChangeKind;
import org.eclipse.sirius.components.collaborative.api.IEditingContextEventHandler;
import org.eclipse.sirius.components.collaborative.api.IEditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.ErrorPayload;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.web.application.project.services.api.IProjectEditingContextService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Sinks;

/**
 * Runs code on a project's sysarch model inside its Sirius editing context (P1 tables and trace): the context's own
 * thread, as every model access (spec §2.3: never write around the EditingContext). A task that changed the model is
 * answered with a semantic change, so Sirius saves it, refreshes the open views and applies the save-time veto.
 *
 * <p>A project is named by a Polarion project id mapped in {@code sysarch.polarion.projects} (the OpenAPI's
 * {@code projectId}), or else by a Sirius project id or unique name.
 *
 * <p>A write on behalf of a user ({@link #write(String, Editor, Function)}, P2 T1) is checked against the element locks
 * inside the task: when it changed an element someone else holds, it is undone there and fails with
 * {@link LockService.LockedException} (or {@link SessionService.FullException} when the project has its maximum of
 * editors, P2 T2), so the caller can answer 423 / 429 (a veto after the task would come too late, the
 * answer would already be out). Writes without an editor are system writes; locks do not apply to them.
 */
@Service
public class ProjectModels {

    static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** What a write task returns: its value, and whether the model changed. */
    public record Outcome<T>(T value, boolean changed) {
    }

    /** Thrown for an unknown project (404 at the API). */
    public static class UnknownProjectException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public UnknownProjectException(String message) {
            super(message);
        }
    }

    private final IEditingContextEventProcessorRegistry editingContexts;

    private final IProjectEditingContextService projectEditingContexts;

    private final SyncProperties properties;

    private final JdbcClient jdbc;

    private final Editors editors;

    public ProjectModels(IEditingContextEventProcessorRegistry editingContexts, IProjectEditingContextService projectEditingContexts,
            SyncProperties properties, JdbcClient jdbc, Editors editors) {
        this.editingContexts = Objects.requireNonNull(editingContexts);
        this.projectEditingContexts = Objects.requireNonNull(projectEditingContexts);
        this.properties = Objects.requireNonNull(properties);
        this.jdbc = Objects.requireNonNull(jdbc);
        this.editors = Objects.requireNonNull(editors);
    }

    /** Reads the project's sysarch resources; changes nothing. */
    public <T> T read(String project, Function<List<Resource>, T> task) {
        return this.write(project, resources -> new Outcome<>(task.apply(resources), false));
    }

    /** Runs a task that may change the model, as the system (no lock applies). Exceptions thrown by the task are rethrown here. */
    public <T> T write(String project, Function<List<Resource>, Outcome<T>> task) {
        return this.write(project, null, task);
    }

    /**
     * Runs a task that may change the model on behalf of {@code editor} (null: the system).
     *
     * @throws LockService.LockedException when the task changed elements someone else holds; the model is unchanged
     */
    @SuppressWarnings("unchecked")
    public <T> T write(String project, Editor editor, Function<List<Resource>, Outcome<T>> task) {
        String projectId = this.siriusProjectId(project);
        String editingContextId = this.projectEditingContexts.getEditingContextId(projectId)
                .orElseThrow(() -> new UnknownProjectException("project '" + project + "' has no model"));
        TaskInput input = new TaskInput(UUID.randomUUID(), task, projectId, editor);
        if (editor != null) {
            this.editors.record(input.id().toString(), editor); // the lease after a successful write (ConstraintEnforcement.Guard)
        }
        IPayload payload = this.editingContexts.dispatchEvent(editingContextId, input).block(TIMEOUT);
        if (payload instanceof TaskPayload result) {
            if (result.failure() != null) {
                throw result.failure();
            }
            return (T) result.value();
        }
        if (payload instanceof ErrorPayload error) {
            throw new IllegalStateException("model access failed: " + error.messages());
        }
        throw new IllegalStateException("model access timed out after " + TIMEOUT);
    }

    /** The Sirius project id for a Polarion project id (mapped), a Sirius project id, or a unique project name. */
    public String siriusProjectId(String project) {
        String mapped = this.properties.getProjects().get(project);
        String name = mapped == null || mapped.isBlank() ? project : mapped;
        List<String> ids = this.jdbc.sql("SELECT CAST(id AS VARCHAR) FROM project WHERE CAST(id AS VARCHAR) = :p OR name = :p").param("p", name)
                .query(String.class).list();
        if (ids.size() != 1) {
            throw new UnknownProjectException(ids.isEmpty() ? "no Sirius project '" + name + "'" : "the name '" + name + "' matches " + ids.size() + " projects");
        }
        return ids.get(0);
    }

    /** The Sirius project's name (read-only, Sirius' {@code project} table). */
    public String projectName(String project) {
        String id = this.siriusProjectId(project);
        return this.jdbc.sql("SELECT name FROM project WHERE CAST(id AS VARCHAR) = :id").param("id", id).query(String.class).optional().orElse(id);
    }

    record TaskInput(UUID id, Function<List<Resource>, ? extends Outcome<?>> task, String projectId, Editor editor) implements IInput {
    }

    /** {@code failure} is set when the task threw. */
    record TaskPayload(UUID id, Object value, RuntimeException failure) implements IPayload {
    }

    /** Runs the tasks on the editing context thread. */
    @Service
    public static class Handler implements IEditingContextEventHandler {

        private final EditAdmission admission;

        public Handler(EditAdmission admission) {
            this.admission = Objects.requireNonNull(admission);
        }

        @Override
        public boolean canHandle(IEditingContext editingContext, IInput input) {
            return editingContext instanceof IEMFEditingContext && input instanceof TaskInput;
        }

        @Override
        public void handle(Sinks.One<IPayload> payloadSink, Sinks.Many<ChangeDescription> changeDescriptionSink, IEditingContext editingContext,
                IInput input) {
            TaskInput task = (TaskInput) input;
            boolean changed = false;
            var resourceSet = ((IEMFEditingContext) editingContext).getDomain().getResourceSet();
            ChangeRecorder recorder = task.editor() == null ? null : new ChangeRecorder(resourceSet);
            try {
                Outcome<?> outcome = task.task().apply(sysarchResources((IEMFEditingContext) editingContext));
                if (recorder != null) {
                    var change = recorder.endRecording();
                    try {
                        this.admission.check(task.projectId(), task.editor(), change, "SysArch API");
                    } catch (RuntimeException refusal) {
                        change.applyAndReverse();
                        throw refusal;
                    }
                }
                changed = outcome.changed();
                payloadSink.tryEmitValue(new TaskPayload(input.id(), outcome.value(), null));
            } catch (RuntimeException e) {
                payloadSink.tryEmitValue(new TaskPayload(input.id(), null, e));
            } finally {
                if (recorder != null) {
                    recorder.dispose();
                }
            }
            changeDescriptionSink.tryEmitNext(new ChangeDescription(changed ? ChangeKind.SEMANTIC_CHANGE : ChangeKind.NOTHING, editingContext.getId(), input));
        }

        /** The documents that hold sysarch elements. */
        static List<Resource> sysarchResources(IEMFEditingContext editingContext) {
            return editingContext.getDomain().getResourceSet().getResources().stream()
                    .filter(r -> r.getContents().stream().anyMatch(e -> e.eClass().getEPackage() == SysarchPackage.eINSTANCE))
                    .toList();
        }
    }
}

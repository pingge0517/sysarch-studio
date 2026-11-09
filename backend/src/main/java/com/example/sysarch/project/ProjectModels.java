package com.example.sysarch.project;

import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.sync.SyncProperties;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

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

    public ProjectModels(IEditingContextEventProcessorRegistry editingContexts, IProjectEditingContextService projectEditingContexts,
            SyncProperties properties, JdbcClient jdbc) {
        this.editingContexts = Objects.requireNonNull(editingContexts);
        this.projectEditingContexts = Objects.requireNonNull(projectEditingContexts);
        this.properties = Objects.requireNonNull(properties);
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    /** Reads the project's sysarch resources; changes nothing. */
    public <T> T read(String project, Function<List<Resource>, T> task) {
        return this.write(project, resources -> new Outcome<>(task.apply(resources), false));
    }

    /** Runs a task that may change the model. Exceptions thrown by the task are rethrown here. */
    @SuppressWarnings("unchecked")
    public <T> T write(String project, Function<List<Resource>, Outcome<T>> task) {
        String editingContextId = this.projectEditingContexts.getEditingContextId(this.siriusProjectId(project))
                .orElseThrow(() -> new UnknownProjectException("project '" + project + "' has no model"));
        IPayload payload = this.editingContexts.dispatchEvent(editingContextId, new TaskInput(UUID.randomUUID(), task)).block(TIMEOUT);
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

    record TaskInput(UUID id, Function<List<Resource>, ? extends Outcome<?>> task) implements IInput {
    }

    /** {@code failure} is set when the task threw. */
    record TaskPayload(UUID id, Object value, RuntimeException failure) implements IPayload {
    }

    /** Runs the tasks on the editing context thread. */
    @Service
    public static class Handler implements IEditingContextEventHandler {

        @Override
        public boolean canHandle(IEditingContext editingContext, IInput input) {
            return editingContext instanceof IEMFEditingContext && input instanceof TaskInput;
        }

        @Override
        public void handle(Sinks.One<IPayload> payloadSink, Sinks.Many<ChangeDescription> changeDescriptionSink, IEditingContext editingContext,
                IInput input) {
            TaskInput task = (TaskInput) input;
            boolean changed = false;
            try {
                Outcome<?> outcome = task.task().apply(sysarchResources((IEMFEditingContext) editingContext));
                changed = outcome.changed();
                payloadSink.tryEmitValue(new TaskPayload(input.id(), outcome.value(), null));
            } catch (RuntimeException e) {
                payloadSink.tryEmitValue(new TaskPayload(input.id(), null, e));
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

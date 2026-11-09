package com.example.sysarch.view.table;

import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.view.api.TableDescription;
import com.example.sysarch.view.generated.ViewpointRegistry;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
 * Tables on Sirius Web (P1 T4): reads and actions run as inputs on the project's editing context, like every model
 * access (spec §2.3: never write around the EditingContext). An action that changed the model answers with a semantic
 * change, so Sirius saves it, refreshes the open diagrams and runs the save-time veto ({@code ChangeGuard}) on it.
 */
@Service
public class TableService {

    static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final ViewpointRegistry registry;

    private final IEditingContextEventProcessorRegistry editingContexts;

    private final IProjectEditingContextService projectEditingContexts;

    private final JdbcClient jdbc;

    public TableService(ViewpointRegistry registry, IEditingContextEventProcessorRegistry editingContexts, IProjectEditingContextService projectEditingContexts,
            JdbcClient jdbc) {
        this.registry = Objects.requireNonNull(registry);
        this.editingContexts = Objects.requireNonNull(editingContexts);
        this.projectEditingContexts = Objects.requireNonNull(projectEditingContexts);
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    /** Thrown for an unknown project, table, action, row or column (404/400 at the API). */
    public static class NotFoundException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public NotFoundException(String message) {
            super(message);
        }
    }

    public List<TableDescription> tables() {
        return this.registry.tableDescriptions();
    }

    public Matrix matrix(String project, String tableId) {
        TableDescription table = this.table(tableId);
        return ((MatrixPayload) this.dispatch(project, new ReadMatrixInput(UUID.randomUUID(), table))).matrix();
    }

    /** Runs the action; the result carries the refused reasons, or the matrix after the change. */
    public ActionResult act(String project, String tableId, String actionId, String rowId, String columnId) {
        TableDescription table = this.table(tableId);
        return ((ActionPayload) this.dispatch(project, new CellActionInput(UUID.randomUUID(), table, actionId, rowId, columnId))).result();
    }

    public record ActionResult(TableActions.Outcome outcome, Matrix matrix) {
    }

    private TableDescription table(String tableId) {
        return this.registry.tableDescription(tableId).orElseThrow(() -> new NotFoundException("unknown table '" + tableId + "'"));
    }

    private IPayload dispatch(String project, IInput input) {
        String editingContextId = this.projectEditingContexts.getEditingContextId(this.siriusProjectId(project))
                .orElseThrow(() -> new NotFoundException("project '" + project + "' has no model"));
        IPayload payload = this.editingContexts.dispatchEvent(editingContextId, input).block(TIMEOUT);
        if (payload instanceof FailedPayload failed) {
            throw failed.notFound() ? new NotFoundException(failed.message()) : new IllegalStateException(failed.message());
        }
        if (payload instanceof ErrorPayload error) {
            throw new IllegalStateException("table access failed: " + error.messages());
        }
        if (payload == null) {
            throw new IllegalStateException("table access timed out after " + TIMEOUT);
        }
        return payload;
    }

    /** A Sirius project id, or else a unique project name (read-only lookup in Sirius' {@code project} table). */
    private String siriusProjectId(String project) {
        List<String> ids = this.jdbc.sql("SELECT CAST(id AS VARCHAR) FROM project WHERE CAST(id AS VARCHAR) = :p OR name = :p").param("p", project)
                .query(String.class).list();
        if (ids.size() != 1) {
            throw new NotFoundException(ids.isEmpty() ? "no Sirius project '" + project + "'" : "the name '" + project + "' matches " + ids.size() + " projects");
        }
        return ids.get(0);
    }

    // ---- inputs, payloads and their handler (run inside the editing context) ----

    record ReadMatrixInput(UUID id, TableDescription table) implements IInput {
    }

    record CellActionInput(UUID id, TableDescription table, String actionId, String rowId, String columnId) implements IInput {
    }

    record MatrixPayload(UUID id, Matrix matrix) implements IPayload {
    }

    record ActionPayload(UUID id, ActionResult result) implements IPayload {
    }

    record FailedPayload(UUID id, String message, boolean notFound) implements IPayload {
    }

    /** Executes the table inputs on the editing context thread. */
    @Service
    public static class Handler implements IEditingContextEventHandler {

        private final ConstraintValidator constraints;

        public Handler(ConstraintValidator constraints) {
            this.constraints = Objects.requireNonNull(constraints);
        }

        @Override
        public boolean canHandle(IEditingContext editingContext, IInput input) {
            return editingContext instanceof IEMFEditingContext && (input instanceof ReadMatrixInput || input instanceof CellActionInput);
        }

        @Override
        public void handle(Sinks.One<IPayload> payloadSink, Sinks.Many<ChangeDescription> changeDescriptionSink, IEditingContext editingContext,
                IInput input) {
            Collection<Resource> resources = sysarchResources((IEMFEditingContext) editingContext);
            boolean changed = false;
            try {
                if (input instanceof ReadMatrixInput read) {
                    payloadSink.tryEmitValue(new MatrixPayload(input.id(), MatrixProjector.project(read.table(), resources)));
                } else if (input instanceof CellActionInput act) {
                    TableActions.Outcome outcome = TableActions.apply(act.table(), resources, act.actionId(), act.rowId(), act.columnId(), this.constraints);
                    changed = outcome.changed();
                    payloadSink.tryEmitValue(new ActionPayload(input.id(), new ActionResult(outcome, MatrixProjector.project(act.table(), resources))));
                }
            } catch (IllegalArgumentException e) {
                payloadSink.tryEmitValue(new FailedPayload(input.id(), e.getMessage(), true));
            } catch (RuntimeException e) {
                payloadSink.tryEmitValue(new FailedPayload(input.id(), String.valueOf(e.getMessage()), false));
            }
            changeDescriptionSink.tryEmitNext(new ChangeDescription(changed ? ChangeKind.SEMANTIC_CHANGE : ChangeKind.NOTHING, editingContext.getId(), input));
        }

        private static List<Resource> sysarchResources(IEMFEditingContext editingContext) {
            return editingContext.getDomain().getResourceSet().getResources().stream()
                    .filter(r -> r.getContents().stream().anyMatch(e -> e.eClass().getEPackage() == SysarchPackage.eINSTANCE))
                    .toList();
        }
    }
}

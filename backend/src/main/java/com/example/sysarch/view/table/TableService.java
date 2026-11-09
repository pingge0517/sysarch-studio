package com.example.sysarch.view.table;

import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.view.api.TableDescription;
import com.example.sysarch.view.generated.ViewpointRegistry;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * Tables on Sirius Web (P1 T4/T5): reads and actions run on the project's editing context ({@link ProjectModels}), so
 * a change is saved, refreshes the open diagrams and passes the save-time veto ({@code ChangeGuard}) like any edit.
 */
@Service
public class TableService {

    private final ViewpointRegistry registry;

    private final ProjectModels models;

    private final ConstraintValidator constraints;

    public TableService(ViewpointRegistry registry, ProjectModels models, ConstraintValidator constraints) {
        this.registry = Objects.requireNonNull(registry);
        this.models = Objects.requireNonNull(models);
        this.constraints = Objects.requireNonNull(constraints);
    }

    /** Thrown for an unknown table, action, row or column (404 at the API). */
    public static class NotFoundException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public NotFoundException(String message) {
            super(message);
        }
    }

    public record ActionResult(TableActions.Outcome outcome, Matrix matrix) {
    }

    public List<TableDescription> tables() {
        return this.registry.tableDescriptions();
    }

    public Matrix matrix(String project, String tableId) {
        TableDescription table = this.table(tableId);
        return this.models.read(project, resources -> MatrixProjector.project(table, resources));
    }

    /**
     * Runs the action; the result carries the refusal reasons, or the matrix after the change.
     *
     * @param remove for a link action: remove the relation instead of adding it
     */
    public ActionResult act(String project, String tableId, String actionId, String rowId, String columnId, boolean remove) {
        TableDescription table = this.table(tableId);
        try {
            return this.models.write(project, resources -> {
                TableActions.Outcome outcome = TableActions.apply(table, resources, actionId, rowId, columnId, remove, this.constraints);
                return new ProjectModels.Outcome<>(new ActionResult(outcome, MatrixProjector.project(table, resources)), outcome.changed());
            });
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    private TableDescription table(String tableId) {
        return this.registry.tableDescription(tableId).orElseThrow(() -> new NotFoundException("unknown table '" + tableId + "'"));
    }
}

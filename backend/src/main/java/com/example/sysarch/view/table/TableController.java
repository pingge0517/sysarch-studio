package com.example.sysarch.view.table;

import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.validation.ValidationError;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The matrix page's API (P1 T4; not in the spec's OpenAPI, which has no table endpoints).
 *
 * <ul>
 * <li>{@code GET /api/v1/tables}: the VSM tables ({@code id}, {@code label}).</li>
 * <li>{@code GET /api/v1/projects/{project}/tables/{table}}: the {@link Matrix} of a Sirius project (id or name).</li>
 * <li>{@code POST /api/v1/projects/{project}/tables/{table}/actions} with {@code {actionId, rowId, columnId}}: 200 and
 * the new matrix, or 422 with {@code errors}; {@code remove: true} undoes a link action (P1 T5) ({@code {code, elementId, message, anchors}}, spec §3.2's shape) and the
 * unchanged matrix when the constraints refuse it; 423 with {@code LOCKED} errors (the holder in the message) when
 * the action would change an element someone else holds (P2 T1); 429 with {@code SESSIONS_FULL} when the project
 * has its maximum of editors (P2 T2).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class TableController {

    public record TableInfo(String id, String label) {
    }

    /** @param remove for a link action: remove the relation (P1 T5) */
    public record ActionRequest(String actionId, String rowId, String columnId, Boolean remove) {
    }

    public record ActionResponse(boolean changed, List<ValidationError> errors, Matrix matrix) {
    }

    private final TableService tables;

    public TableController(TableService tables) {
        this.tables = Objects.requireNonNull(tables);
    }

    @GetMapping("/tables")
    public List<TableInfo> tables() {
        return this.tables.tables().stream().map(t -> new TableInfo(t.id(), t.label())).toList();
    }

    @GetMapping("/projects/{project}/tables/{table}")
    public Matrix matrix(@PathVariable String project, @PathVariable String table) {
        try {
            return this.tables.matrix(project, table);
        } catch (TableService.NotFoundException | ProjectModels.UnknownProjectException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/projects/{project}/tables/{table}/actions")
    public ResponseEntity<ActionResponse> act(@PathVariable String project, @PathVariable String table, @RequestBody ActionRequest request,
            jakarta.servlet.http.HttpServletRequest http) {
        if (request == null || blank(request.actionId()) || blank(request.rowId()) || blank(request.columnId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "actionId, rowId and columnId are required");
        }
        TableService.ActionResult result;
        try {
            result = this.tables.act(project, table, request.actionId(), request.rowId(), request.columnId(), Boolean.TRUE.equals(request.remove()),
                    com.example.sysarch.identity.CurrentUser.of(http).map(com.example.sysarch.identity.Editors.Editor::new)
                            .orElse(com.example.sysarch.identity.Editors.Editor.ANONYMOUS));
        } catch (TableService.NotFoundException | ProjectModels.UnknownProjectException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        ActionResponse body = new ActionResponse(result.outcome().changed(), result.outcome().errors(), result.matrix());
        HttpStatus status = com.example.sysarch.lock.EditAdmission.LOCKED.equals(result.admission()) ? HttpStatus.LOCKED
                : com.example.sysarch.lock.EditAdmission.SESSIONS_FULL.equals(result.admission()) ? HttpStatus.TOO_MANY_REQUESTS
                        : result.outcome().errors().isEmpty() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(body);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

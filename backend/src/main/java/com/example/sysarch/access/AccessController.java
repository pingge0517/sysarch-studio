package com.example.sysarch.access;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.identity.CurrentUser;
import com.example.sysarch.project.ProjectModels;

import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * P2 T8 endpoints (not in the spec's OpenAPI):
 * <ul>
 * <li>{@code GET /api/v1/access?projectId=}: the current user's role in a project and what it allows (the shell shows
 * it and disables what it does not allow; the server checks again in any case).</li>
 * <li>{@code GET /api/v1/role-mappings}; {@code POST {polarionProject, polarionRole, toolRole}} (adds or replaces);
 * {@code DELETE /{id}}: changes need the global {@link Permission#MANAGE_ROLES} and are audited.</li>
 * <li>{@code GET /api/v1/audit?projectId=&action=&limit=}: administrators of the project (without a project: global
 * administrators).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class AccessController {

    public record MappingRequest(String polarionProject, String polarionRole, String toolRole) {
    }

    private final Access access;

    private final RoleMappings mappings;

    private final AuditLog audit;

    private final ProjectModels models;

    public AccessController(Access access, RoleMappings mappings, AuditLog audit, ProjectModels models) {
        this.access = Objects.requireNonNull(access);
        this.mappings = Objects.requireNonNull(mappings);
        this.audit = Objects.requireNonNull(audit);
        this.models = Objects.requireNonNull(models);
    }

    @GetMapping("/access")
    public Map<String, Object> access(@RequestParam(value = "projectId", required = false) String projectId, HttpServletRequest http) {
        var principal = CurrentUser.principal(http).orElse(null);
        Access.Grant grant = projectId == null || projectId.isBlank() ? this.access.globalGrant(principal)
                : this.access.grant(principal, this.project(projectId));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", grant.user());
        body.put("projectId", grant.projectId());
        body.put("polarionProject", grant.polarionProject());
        body.put("role", grant.role().name());
        body.put("roleLabel", grant.role().label());
        body.put("permissions", grant.role().permissions().stream().map(Enum::name).sorted().toList());
        return body;
    }

    @GetMapping("/role-mappings")
    public List<RoleMappings.Row> mappings() {
        return this.mappings.list();
    }

    @PostMapping("/role-mappings")
    public ResponseEntity<RoleMappings.Row> put(@RequestBody MappingRequest request, HttpServletRequest http) {
        Access.Grant grant = this.access.requireGlobal(http, Permission.MANAGE_ROLES);
        if (request == null || blank(request.polarionProject()) || blank(request.polarionRole()) || blank(request.toolRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "polarionProject, polarionRole and toolRole are required");
        }
        ToolRole role;
        try {
            role = ToolRole.valueOf(request.toolRole().strip());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "toolRole must be ADMIN, ARCHITECT, REVIEWER or OBSERVER");
        }
        if (request.polarionProject().length() > 64 || request.polarionRole().length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "polarionProject is at most 64 characters, polarionRole at most 128");
        }
        RoleMappings.Row row = this.mappings.put(request.polarionProject().strip(), request.polarionRole().strip(), role, grant.user());
        this.audit.append(null, grant.user(), AuditLog.ROLE_MAPPING_CHANGE, null, "put " + row.polarionProject() + " / " + row.polarionRole() + " → " + role);
        return ResponseEntity.status(HttpStatus.CREATED).body(row);
    }

    @DeleteMapping("/role-mappings/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, HttpServletRequest http) {
        Access.Grant grant = this.access.requireGlobal(http, Permission.MANAGE_ROLES);
        RoleMappings.Row row = this.mappings.delete(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no role mapping " + id));
        this.audit.append(null, grant.user(), AuditLog.ROLE_MAPPING_CHANGE, null,
                "delete " + row.polarionProject() + " / " + row.polarionRole() + " → " + row.toolRole());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit")
    public List<AuditLog.Entry> audit(@RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "action", required = false) String action, @RequestParam(value = "limit", defaultValue = "200") int limit,
            HttpServletRequest http) {
        String project = blank(projectId) ? null : this.project(projectId);
        if (project == null) {
            this.access.requireGlobal(http, Permission.AUDIT_READ);
        } else {
            this.access.require(http, project, Permission.AUDIT_READ);
        }
        return this.audit.recent(project, blank(action) ? null : action.strip(), limit);
    }

    private String project(String project) {
        try {
            return this.models.siriusProjectId(project);
        } catch (ProjectModels.UnknownProjectException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

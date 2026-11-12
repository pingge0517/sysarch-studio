package com.example.sysarch.access;

import com.example.sysarch.audit.AuditLog;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Polarion project → model bindings as data (P4 T1, spec P4 §2.1), on the admin page next to the role mappings:
 * <ul>
 * <li>{@code GET /api/v1/project-bindings}: the effective bindings (table, then environment), signed-in users;</li>
 * <li>{@code PUT /api/v1/project-bindings/{polarionProject}} {@code {siriusProject}}: bind (id or name of the model);</li>
 * <li>{@code DELETE /api/v1/project-bindings/{polarionProject}}: remove the table's binding (the environment's default,
 * if any, applies again).</li>
 * </ul>
 * Changes need the global {@link Permission#MANAGE_ROLES} (administrators), take effect without a restart and are audited
 * ({@code PROJECT_BINDING_CHANGE}). A model belongs to one Polarion project at most (409). Users signed in before a new
 * binding see their roles in that Polarion project after their next sign-in (the session keeps the roles of bound
 * projects only).
 */
@RestController
@RequestMapping("/api/v1/project-bindings")
public class ProjectBindingController {

    public record BindRequest(String siriusProject) {
    }

    private final ProjectBindings bindings;

    private final Access access;

    private final AuditLog audit;

    public ProjectBindingController(ProjectBindings bindings, Access access, AuditLog audit) {
        this.bindings = Objects.requireNonNull(bindings);
        this.access = Objects.requireNonNull(access);
        this.audit = Objects.requireNonNull(audit);
    }

    @GetMapping
    public List<ProjectBindings.Binding> list(HttpServletRequest http) {
        if (this.access.principal(http).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return this.bindings.list();
    }

    @PutMapping("/{polarionProject}")
    public ProjectBindings.Binding bind(@PathVariable String polarionProject, @RequestBody BindRequest request, HttpServletRequest http) {
        Access.Grant grant = this.access.requireGlobal(http, Permission.MANAGE_ROLES);
        ProjectBindings.Binding binding;
        try {
            binding = this.bindings.bind(polarionProject, request == null ? null : request.siriusProject(), grant.user());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (ProjectBindings.BindingException e) {
            throw new ResponseStatusException(e.conflict() ? HttpStatus.CONFLICT : HttpStatus.NOT_FOUND, e.getMessage());
        }
        this.audit.append(binding.siriusProject(), grant.user(), AuditLog.PROJECT_BINDING_CHANGE, null, "bind " + polarionProject + " → " + binding.siriusProject());
        return binding;
    }

    @DeleteMapping("/{polarionProject}")
    public ResponseEntity<Void> unbind(@PathVariable String polarionProject, HttpServletRequest http) {
        Access.Grant grant = this.access.requireGlobal(http, Permission.MANAGE_ROLES);
        if (!this.bindings.unbind(polarionProject)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Polarion 项目 " + polarionProject + " 没有在管理页设置的绑定（环境变量中的绑定只能改配置）");
        }
        this.audit.append(null, grant.user(), AuditLog.PROJECT_BINDING_CHANGE, null, "unbind " + polarionProject);
        return ResponseEntity.noContent().build();
    }
}

package com.example.sysarch.access;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.identity.CurrentUser;
import com.example.sysarch.identity.Principal;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Permission checks (P2 T8 step 1, §7.1): a principal's role in a Sirius project comes from {@link RoleMappings} and the
 * project's Polarion binding ({@link ProjectBindings}); every refusal is written to {@code audit_log} as
 * {@value AuditLog#ACCESS_DENIED} and thrown as {@link ForbiddenException}. Used by the REST controllers, by
 * {@code EditAdmission} (Sirius edits and our own model tasks) and by the GraphQL filter (project deletion).
 */
@Component
public class Access {

    /** A user's role in a project, with what it allows. */
    public record Grant(String user, String projectId, String polarionProject, ToolRole role) {

        public boolean can(Permission permission) {
            return this.role.can(permission);
        }
    }

    private final Logger logger = LoggerFactory.getLogger(Access.class);

    private final RoleMappings mappings;

    private final ProjectBindings bindings;

    private final AuditLog audit;

    public Access(RoleMappings mappings, ProjectBindings bindings, AuditLog audit) {
        this.mappings = Objects.requireNonNull(mappings);
        this.bindings = Objects.requireNonNull(bindings);
        this.audit = Objects.requireNonNull(audit);
    }

    /** The role in a Sirius project ({@code siriusProjectId} null: a project not created yet, i.e. unbound). */
    public Grant grant(Principal principal, String siriusProjectId) {
        String polarion = this.bindings.polarionProjectOf(siriusProjectId).orElse(null);
        return new Grant(principal == null ? null : principal.user(), siriusProjectId, polarion,
                RoleMappings.roleOf(this.mappings.list(), principal, polarion, false));
    }

    /** The role for actions outside any project (the role mapping, the whole audit log). */
    public Grant globalGrant(Principal principal) {
        return new Grant(principal == null ? null : principal.user(), null, null, RoleMappings.roleOf(this.mappings.list(), principal, null, true));
    }

    public Grant require(HttpServletRequest request, String siriusProjectId, Permission permission) {
        return this.require(CurrentUser.principal(request).orElse(null), siriusProjectId, permission, null);
    }

    public Grant requireGlobal(HttpServletRequest request, Permission permission) {
        Principal principal = CurrentUser.principal(request).orElse(null);
        Grant grant = this.globalGrant(principal);
        if (!grant.can(permission)) {
            throw this.refuse(principal, grant, permission, null);
        }
        return grant;
    }

    /**
     * @param elementId the element the action was about, for the audit row (may be null)
     * @throws ForbiddenException (audited) when the role does not allow it
     */
    public Grant require(Principal principal, String siriusProjectId, Permission permission, String elementId) {
        Grant grant = this.grant(principal, siriusProjectId);
        if (!grant.can(permission)) {
            throw this.refuse(principal, grant, permission, elementId);
        }
        return grant;
    }

    private ForbiddenException refuse(Principal principal, Grant grant, Permission permission, String elementId) {
        String who = principal == null ? "未登录用户" : principal.name();
        String message = principal == null ? "权限不足：请先登录（未登录用户只能查看），不能" + permission.label()
                : "权限不足：" + who + " 在本项目的角色是" + grant.role().label() + "，不能" + permission.label();
        this.audit.append(grant.projectId(), principal == null ? null : principal.user(), AuditLog.ACCESS_DENIED, elementId,
                permission.name() + " refused, role " + grant.role().name() + (grant.polarionProject() == null ? "" : " (Polarion " + grant.polarionProject() + ")"));
        this.logger.warn("Refused {} to {} in project {}: role {}", permission, principal == null ? "anonymous" : principal.user(), grant.projectId(),
                grant.role());
        return new ForbiddenException(message, grant.role());
    }

    /**
     * For the Polarion-facing endpoints, whose {@code projectId} is a Polarion project id (sync, consistency): the role
     * in that Polarion project.
     */
    public Grant requirePolarion(HttpServletRequest request, String polarionProject, Permission permission) {
        Principal principal = CurrentUser.principal(request).orElse(null);
        Grant grant = new Grant(principal == null ? null : principal.user(), polarionProject, polarionProject,
                RoleMappings.roleOf(this.mappings.list(), principal, polarionProject, false));
        if (!grant.can(permission)) {
            throw this.refuse(principal, grant, permission, null);
        }
        return grant;
    }

    /** For the shell: the role and permissions of the current user in a project. */
    public List<Permission> permissions(Grant grant) {
        return grant.role().permissions().stream().toList();
    }

    public Optional<Principal> principal(HttpServletRequest request) {
        return CurrentUser.principal(request);
    }
}

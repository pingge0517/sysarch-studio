package com.example.sysarch.access;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.identity.AuthProperties;
import com.example.sysarch.identity.Principal;
import com.example.sysarch.sync.SyncProperties;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Project membership is separate from the default OBSERVER role, which by itself grants no project visibility. */
@Service
public class ProjectReadAccess {
    public static final class Denied extends RuntimeException {
        public Denied() { super("无权读取该项目，请联系项目管理员授权"); }
    }

    private final ProjectBindings bindings;
    private final RoleMappings roles;
    private final SyncProperties sync;
    private final JdbcClient jdbc;
    private final AuthProperties auth;
    private final AuditLog audit;

    public ProjectReadAccess(ProjectBindings bindings, RoleMappings roles, SyncProperties sync, JdbcClient jdbc,
            AuthProperties auth, AuditLog audit) {
        this.bindings = bindings;
        this.roles = roles;
        this.sync = sync;
        this.jdbc = jdbc;
        this.auth = auth;
        this.audit = audit;
    }

    /** Explicit anonymous-read is a deployment opt-out; dev keeps its existing anonymous fixture access. */
    public boolean anonymousRead() {
        return this.auth.getAnonymousRead() != null ? this.auth.getAnonymousRead() : this.auth.dev();
    }

    public boolean canRead(Principal principal, String project) {
        if (principal == null) return this.anonymousRead();
        return readable(this.roles.list(), principal, this.bindings.polarionProjectOf(project).orElse(null));
    }

    static boolean readable(List<RoleMappings.Row> rows, Principal principal, String polarion) {
        if (principal == null) return false;
        if (polarion != null && !principal.rolesIn(polarion).isEmpty()) return true;
        return rows.stream().anyMatch(row -> {
            boolean scope = row.polarionProject().equals(RoleMappings.ANY)
                    || row.polarionProject().equals(polarion == null ? RoleMappings.UNBOUND : polarion);
            if (!scope) return false;
            // A named global role is an explicit grant. The generic * / * OBSERVER row is not membership.
            if (row.polarionRole().startsWith("global:")) {
                return principal.globalRoles().contains(row.polarionRole().substring(7));
            }
            // Unbound projects are shared only if an explicit (unbound) mapping says so.
            return polarion == null && row.polarionProject().equals(RoleMappings.UNBOUND)
                    && row.polarionRole().equals(RoleMappings.ANY);
        });
    }

    public void require(Principal principal, String project) {
        if (!this.canRead(principal, project)) {
            this.audit.append(project, principal == null ? null : principal.user(), AuditLog.ACCESS_DENIED, null, "PROJECT_READ refused");
            throw new Denied();
        }
    }

    /** Accept the same project id / unique name / Polarion id aliases as the modeling API. */
    public String resolve(String reference) {
        if (reference == null || reference.isBlank()) throw new Denied();
        String target = this.sync.getProjects().getOrDefault(reference, reference);
        List<String> ids = this.jdbc.sql("SELECT CAST(id AS VARCHAR) FROM project WHERE CAST(id AS VARCHAR) = :p OR name = :p")
                .param("p", target).query(String.class).list();
        if (ids.size() != 1) throw new Denied();
        return ids.getFirst();
    }

    public void reference(Principal principal, String reference) { this.require(principal, this.resolve(reference)); }

    public boolean visibleReference(Principal principal, String reference) {
        try { return this.canRead(principal, this.resolve(reference)); } catch (Denied e) { return false; }
    }

    public String editingContext(String id) {
        return this.jdbc.sql("SELECT CAST(project_id AS VARCHAR) FROM project_semantic_data WHERE CAST(semantic_data_id AS VARCHAR) = :id")
                .param("id", id).query(String.class).optional().orElseThrow(Denied::new);
    }

    public void resource(Principal principal, String kind, String id) {
        String table = switch (kind) { case "baseline" -> "baseline"; case "branch" -> "branch"; case "report" -> "report_job"; default -> throw new Denied(); };
        String project = this.jdbc.sql("SELECT project_id FROM " + table + " WHERE id = :id")
                .param("id", id).query(String.class).optional().orElseThrow(Denied::new);
        this.reference(principal, project);
    }

    public boolean canReadImport(Principal principal, String jobId) {
        if (principal == null) return this.anonymousRead();
        String author = this.jdbc.sql("SELECT requested_by FROM import_job WHERE id = :id")
                .param("id", jobId).query(String.class).optional().orElse(null);
        if (author == null) return false;
        // Failed imports can contain source text in diagnostics. Only their author or an explicit global admin reads them.
        if (principal.user().equals(author)) return true;
        return this.globalAdministrator(principal);
    }

    public boolean globalAdministrator(Principal principal) {
        return principal != null && RoleMappings.roleOf(this.roles.list(), principal, null, true).can(Permission.MANAGE_ROLES);
    }
}

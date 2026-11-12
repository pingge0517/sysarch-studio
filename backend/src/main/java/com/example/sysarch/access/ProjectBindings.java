package com.example.sysarch.access;

import com.example.sysarch.branch.BranchWorkspaces;
import com.example.sysarch.sync.SyncProperties;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Which Polarion project a Sirius project is bound to (P2 T8): {@code sysarch.polarion.projects} maps a Polarion id to
 * a Sirius project id or name. A branch workspace is bound like its trunk. JDBC only (no Sirius service), because the
 * Sirius edit guard depends on it. Names are resolved every 30 s at most.
 */
@Component
public class ProjectBindings {

    private static final long REFRESH_MS = 30_000;

    private record Resolved(Map<String, String> bySiriusId, long at) {
    }

    private final SyncProperties properties;

    private final JdbcClient jdbc;

    private final BranchWorkspaces workspaces;

    private final Map<String, Resolved> cache = new ConcurrentHashMap<>();

    public ProjectBindings(SyncProperties properties, JdbcClient jdbc, BranchWorkspaces workspaces) {
        this.properties = Objects.requireNonNull(properties);
        this.jdbc = Objects.requireNonNull(jdbc);
        this.workspaces = Objects.requireNonNull(workspaces);
    }

    /** The Polarion project of a Sirius project (or of a workspace's trunk); empty when unbound. */
    public Optional<String> polarionProjectOf(String siriusProjectId) {
        if (siriusProjectId == null) {
            return Optional.empty();
        }
        String project = this.workspaces.branchOf(siriusProjectId).map(BranchWorkspaces.Branch::projectId).orElse(siriusProjectId);
        return Optional.ofNullable(this.bySiriusId().get(project));
    }

    private Map<String, String> bySiriusId() {
        Resolved resolved = this.cache.get("");
        long now = System.currentTimeMillis();
        if (resolved != null && now - resolved.at() < REFRESH_MS) {
            return resolved.bySiriusId();
        }
        Map<String, String> map = new ConcurrentHashMap<>();
        this.properties.getProjects().forEach((polarion, sirius) -> {
            if (sirius == null || sirius.isBlank()) {
                return;
            }
            List<String> ids = this.jdbc.sql("SELECT CAST(id AS VARCHAR) FROM project WHERE CAST(id AS VARCHAR) = :p OR name = :p").param("p", sirius)
                    .query(String.class).list();
            if (ids.size() == 1) {
                map.put(ids.get(0), polarion);
            }
        });
        this.cache.put("", new Resolved(map, now));
        return map;
    }

    // ---- P4 T1: bindings as data (table project_binding), the environment as the default

    /** A binding as the admin page shows it; {@code source} = table or environment. */
    public record Binding(String polarionProject, String siriusProject, String source, String updatedBy, java.time.OffsetDateTime updatedAt) {
    }

    /** A request that cannot be served (409: the model is already bound to another Polarion project; 404: no such model). */
    public static class BindingException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final boolean conflict;

        public BindingException(String message, boolean conflict) {
            super(message);
            this.conflict = conflict;
        }

        public boolean conflict() {
            return this.conflict;
        }
    }

    /** The environment's mapping (sysarch.polarion.projects as configured at start). */
    private final Map<String, String> defaults = new ConcurrentHashMap<>();

    /**
     * At start: the configured mapping becomes the default and the table's rows override it, in the one map every reader
     * uses ({@code sysarch.polarion.projects}), so a change needs no restart.
     */
    @jakarta.annotation.PostConstruct
    public void load() {
        this.defaults.clear();
        this.defaults.putAll(this.properties.getProjects());
        this.properties.setProjects(new ConcurrentHashMap<>(this.defaults));
        this.rows().forEach(row -> this.properties.getProjects().put(row.polarionProject(), row.siriusProject()));
        this.forget();
    }

    /** The effective bindings: the table's, then the environment's that the table does not override. */
    public List<Binding> list() {
        Map<String, Binding> all = new java.util.TreeMap<>();
        this.defaults.forEach((polarion, sirius) -> all.put(polarion, new Binding(polarion, sirius, "environment", null, null)));
        this.rows().forEach(row -> all.put(row.polarionProject(), row));
        return List.copyOf(all.values());
    }

    /**
     * Binds a Polarion project to a model (a Sirius project id or name, stored as its id). One model belongs to one Polarion
     * project at most.
     */
    public Binding bind(String polarionProject, String siriusProject, String actor) {
        if (polarionProject == null || polarionProject.isBlank() || siriusProject == null || siriusProject.isBlank()) {
            throw new IllegalArgumentException("polarionProject 和 siriusProject 都不能为空");
        }
        String polarion = polarionProject.strip();
        List<String> ids = this.jdbc.sql("SELECT CAST(id AS VARCHAR) FROM project WHERE CAST(id AS VARCHAR) = :p OR name = :p").param("p", siriusProject.strip())
                .query(String.class).list();
        if (ids.size() != 1) {
            throw new BindingException(ids.isEmpty() ? "没有这个模型：" + siriusProject : "有 " + ids.size() + " 个模型叫「" + siriusProject + "」，请用项目 ID", false);
        }
        String sirius = ids.get(0);
        this.forget();
        String holder = this.bySiriusId().get(sirius);
        if (holder != null && !holder.equals(polarion)) {
            throw new BindingException("这个模型已绑定到 Polarion 项目 " + holder + "，一个模型只能绑定一个 Polarion 项目", true);
        }
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        int updated = this.jdbc.sql("UPDATE project_binding SET sirius_project = :s, updated_by = :u, updated_at = :t WHERE polarion_project = :p")
                .param("s", sirius).param("u", actor).param("t", now).param("p", polarion).update();
        if (updated == 0) {
            this.jdbc.sql("INSERT INTO project_binding (polarion_project, sirius_project, created_by, created_at) VALUES (:p, :s, :u, :t)").param("p", polarion)
                    .param("s", sirius).param("u", actor).param("t", now).update();
        }
        this.properties.getProjects().put(polarion, sirius);
        this.forget();
        return new Binding(polarion, sirius, "table", actor, now);
    }

    /** Removes the table's binding; the environment's default, if any, applies again. True if a row was removed. */
    public boolean unbind(String polarionProject) {
        int removed = this.jdbc.sql("DELETE FROM project_binding WHERE polarion_project = :p").param("p", polarionProject).update();
        String fallback = this.defaults.get(polarionProject);
        if (fallback != null) {
            this.properties.getProjects().put(polarionProject, fallback);
        } else {
            this.properties.getProjects().remove(polarionProject);
        }
        this.forget();
        return removed > 0;
    }

    /** A Sirius project was deleted: the table's bindings to it go (P4 spec §2.1). Returns how many. */
    public int dropSiriusProject(String siriusProjectId) {
        List<String> polarion = this.jdbc.sql("SELECT polarion_project FROM project_binding WHERE sirius_project = :s").param("s", siriusProjectId)
                .query(String.class).list();
        polarion.forEach(this::unbind);
        return polarion.size();
    }

    /** Resolution is computed again on the next question. */
    public void forget() {
        this.cache.clear();
    }

    private List<Binding> rows() {
        return this.jdbc.sql("SELECT polarion_project, sirius_project, created_by, created_at, updated_by, updated_at FROM project_binding ORDER BY polarion_project")
                .query((rs, n) -> new Binding(rs.getString(1), rs.getString(2), "table", rs.getString(5) != null ? rs.getString(5) : rs.getString(3),
                        rs.getObject(6, java.time.OffsetDateTime.class) != null ? rs.getObject(6, java.time.OffsetDateTime.class)
                                : rs.getObject(4, java.time.OffsetDateTime.class)))
                .list();
    }
}

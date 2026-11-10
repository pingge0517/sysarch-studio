package com.example.sysarch.branch;

import com.example.sysarch.session.ProjectLimits;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Which Sirius projects are branch workspaces (P2 T3), read from {@code branch}. Kept apart from {@link BranchService}
 * so that sessions and edits can ask without depending on the whole branch service. Cached; {@link #forget} on any
 * change of a branch's status.
 */
@Component
public class BranchWorkspaces implements ProjectLimits {

    /** One writer per branch (spec P2 §3.1: "同一分支同时只允许一个写会话"). */
    public static final int WRITERS_PER_BRANCH = 1;

    /** A branch row. */
    public record Branch(String id, String projectId, String name, String baseBaseline, String createdBy, OffsetDateTime createdAt, String status,
            String workspaceProjectId) {

        public boolean open() {
            return "OPEN".equals(this.status);
        }
    }

    private final JdbcClient jdbc;

    private final Map<String, Optional<Branch>> byWorkspace = new ConcurrentHashMap<>();

    public BranchWorkspaces(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    /** The open branch edited in this Sirius project, if it is a workspace. */
    public Optional<Branch> branchOf(String workspaceProjectId) {
        return this.byWorkspace.computeIfAbsent(workspaceProjectId, id -> this.jdbc
                .sql("SELECT * FROM branch WHERE workspace_project_id = :w AND status = 'OPEN'").param("w", id).query(BranchWorkspaces::branch).optional());
    }

    @Override
    public OptionalInt limit(String projectId) {
        return this.branchOf(projectId).isPresent() ? OptionalInt.of(WRITERS_PER_BRANCH) : OptionalInt.empty();
    }

    public void forget(String workspaceProjectId) {
        if (workspaceProjectId != null) {
            this.byWorkspace.remove(workspaceProjectId);
        }
    }

    static Branch branch(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Branch(rs.getString("id"), rs.getString("project_id"), rs.getString("name"), rs.getString("base_baseline"), rs.getString("created_by"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getString("status"), rs.getString("workspace_project_id"));
    }
}

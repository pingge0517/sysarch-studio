package com.example.sysarch.access;

import com.example.sysarch.identity.Principal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Table {@code role_mapping} (P2 §7.1: "映射表落库，不写死在代码里"; change set 015): Polarion role → tool role, per
 * Polarion project. Rows:
 * <ul>
 * <li>{@code polarion_project}: a Polarion project id, {@value #ANY} (every project) or {@value #UNBOUND} (Sirius
 * projects bound to no Polarion project: imported, new, or not yet mapped);</li>
 * <li>{@code polarion_role}: a project role id of that project ({@code project_admin}), {@code global:<id>} (a
 * Polarion global role) or {@value #ANY} (every signed-in user).</li>
 * </ul>
 * A user's role in a project is the highest role of the rows that match; none → {@link ToolRole#OBSERVER}. An anonymous
 * request matches no row.
 */
@Repository
public class RoleMappings {

    public static final String ANY = "*";

    public static final String UNBOUND = "(unbound)";

    public record Row(long id, String polarionProject, String polarionRole, ToolRole toolRole, String createdBy, OffsetDateTime createdAt) {
    }

    private final JdbcClient jdbc;

    public RoleMappings(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public List<Row> list() {
        return this.jdbc.sql("SELECT id, polarion_project, polarion_role, tool_role, created_by, created_at FROM role_mapping ORDER BY polarion_project, polarion_role")
                .query((rs, n) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), ToolRole.valueOf(rs.getString(4)), rs.getString(5),
                        rs.getObject(6, OffsetDateTime.class)))
                .list();
    }

    /** Adds or replaces the row of (project, role). */
    public Row put(String polarionProject, String polarionRole, ToolRole toolRole, String by) {
        this.jdbc.sql("DELETE FROM role_mapping WHERE polarion_project = :p AND polarion_role = :r").param("p", polarionProject).param("r", polarionRole).update();
        this.jdbc.sql("INSERT INTO role_mapping (polarion_project, polarion_role, tool_role, created_by) VALUES (:p, :r, :t, :by)")
                .param("p", polarionProject).param("r", polarionRole).param("t", toolRole.name()).param("by", by).update();
        return this.list().stream().filter(r -> r.polarionProject().equals(polarionProject) && r.polarionRole().equals(polarionRole)).findFirst().orElseThrow();
    }

    public Optional<Row> delete(long id) {
        Optional<Row> row = this.list().stream().filter(r -> r.id() == id).findFirst();
        row.ifPresent(r -> this.jdbc.sql("DELETE FROM role_mapping WHERE id = :id").param("id", id).update());
        return row;
    }

    /**
     * The role of a principal in a project bound to {@code polarionProject} (null: an unbound project).
     *
     * @param global true for actions that belong to no project (the role mapping itself): only {@value #ANY}-project
     *        rows with global or {@value #ANY} roles count
     */
    public static ToolRole roleOf(List<Row> rows, Principal principal, String polarionProject, boolean global) {
        if (principal == null) {
            return ToolRole.OBSERVER;
        }
        ToolRole role = null;
        for (Row row : rows) {
            boolean project = row.polarionProject().equals(ANY)
                    || (!global && (polarionProject == null ? row.polarionProject().equals(UNBOUND) : row.polarionProject().equals(polarionProject)));
            if (project && matches(row.polarionRole(), principal, global ? null : polarionProject)) {
                role = row.toolRole().max(role);
            }
        }
        return role == null ? ToolRole.OBSERVER : role;
    }

    private static boolean matches(String role, Principal principal, String polarionProject) {
        if (role.equals(ANY)) {
            return true;
        }
        if (role.startsWith("global:")) {
            return principal.globalRoles().contains(role.substring(7));
        }
        return principal.rolesIn(polarionProject).contains(role);
    }
}

package com.example.sysarch.access;

import java.util.EnumSet;
import java.util.Set;

/**
 * The four tool roles of P2 §7.1, highest first. A user's role in a project is the highest one any matching
 * {@code role_mapping} row gives; none matches → {@link #OBSERVER}.
 */
public enum ToolRole {

    /** 项目管理员: everything, plus forced unlock, deleting branches and baselines, the audit log. */
    ADMIN("项目管理员", EnumSet.allOf(Permission.class)),

    /** 架构师: modelling, baselines, branches, merges. */
    ARCHITECT("架构师", EnumSet.of(Permission.MODEL_EDIT, Permission.BASELINE_CREATE, Permission.BRANCH_CREATE, Permission.MERGE, Permission.COMMENT,
            Permission.SYNC, Permission.IMPORT, Permission.REPORT)),

    /** 评审人: read and comment, no modelling. */
    REVIEWER("评审人", EnumSet.of(Permission.COMMENT, Permission.REPORT)),

    /** 观察者: read only (a report is reading). */
    OBSERVER("观察者", EnumSet.of(Permission.REPORT));

    private final String label;

    private final Set<Permission> permissions;

    ToolRole(String label, Set<Permission> permissions) {
        this.label = label;
        this.permissions = permissions;
    }

    public String label() {
        return this.label;
    }

    public Set<Permission> permissions() {
        return this.permissions;
    }

    public boolean can(Permission permission) {
        return this.permissions.contains(permission);
    }

    /** The higher of two roles (declaration order is rank order). */
    public ToolRole max(ToolRole other) {
        return other == null || this.ordinal() <= other.ordinal() ? this : other;
    }
}

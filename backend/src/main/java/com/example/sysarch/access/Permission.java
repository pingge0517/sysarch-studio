package com.example.sysarch.access;

/** What a tool role allows (P2 §7.1, {@link ToolRole}); the label names the action in refusals. */
public enum Permission {

    MODEL_EDIT("建模"),
    BASELINE_CREATE("提交基线"),
    BASELINE_DELETE("删除基线"),
    BRANCH_CREATE("建分支"),
    BRANCH_DELETE("删除分支"),
    MERGE("合并分支"),
    LOCK_FORCE("强制解锁"),
    COMMENT("评论"),
    SYNC("与 Polarion 同步"),
    IMPORT("导入模型"),
    REPORT("生成报告"),
    MANAGE_ROLES("管理角色映射"),
    PROJECT_ADMIN("删除或重命名项目"),
    AUDIT_READ("查看审计日志");

    private final String label;

    Permission(String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }
}

package com.example.sysarch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code sysarch.features} (P2 T8, spec §10 "三个回滚点", plus the import switch of the risk table): each can be turned
 * off on its own without taking the release down; the runbook says when.
 * <ul>
 * <li>{@code locks=false}: no element lock is checked or taken (back to single-user editing); acquiring answers 503.</li>
 * <li>{@code branches=false}: no new branch, no merge (back to editing the trunk); 503.</li>
 * <li>{@code reports=false}: no new report (back to writing them by hand); 503.</li>
 * <li>{@code imports=false}: the SysML v2 import refuses (503).</li>
 * </ul>
 */
@ConfigurationProperties("sysarch.features")
public class FeatureProperties {

    private boolean locks = true;

    private boolean branches = true;

    private boolean reports = true;

    private boolean imports = true;

    /** P3 T4 (spec P3 §9 "关掉 /solve 与求解菜单即可"): parameter solving. */
    private boolean solver = true;

    public boolean isSolver() {
        return this.solver;
    }

    public void setSolver(boolean solver) {
        this.solver = solver;
    }

    public boolean isLocks() {
        return this.locks;
    }

    public void setLocks(boolean locks) {
        this.locks = locks;
    }

    public boolean isBranches() {
        return this.branches;
    }

    public void setBranches(boolean branches) {
        this.branches = branches;
    }

    public boolean isReports() {
        return this.reports;
    }

    public void setReports(boolean reports) {
        this.reports = reports;
    }

    public boolean isImports() {
        return this.imports;
    }

    public void setImports(boolean imports) {
        this.imports = imports;
    }

    /** 503 for a switched-off feature. */
    public static void require(boolean enabled, String feature) {
        if (!enabled) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    feature + "已由运维关闭（sysarch.features），请联系管理员");
        }
    }
}

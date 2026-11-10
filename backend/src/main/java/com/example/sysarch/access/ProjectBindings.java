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
}

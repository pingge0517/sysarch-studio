package com.example.sysarch.identity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Who a request is (P2 T8): the Polarion user and the Polarion roles they had at login, global role ids and project
 * roles per Polarion project. What they may do is decided per project from {@code role_mapping} (package
 * {@code access}), never from this record alone. {@code source} is {@code polarion} (a verified token) or {@code dev}
 * (a typed name, only in {@code sysarch.auth.mode=dev}).
 */
public record Principal(String user, String name, Set<String> globalRoles, Map<String, Set<String>> projectRoles, String source) {

    public Principal {
        Objects.requireNonNull(user);
        name = name == null || name.isBlank() ? user : name;
        globalRoles = Set.copyOf(globalRoles == null ? Set.of() : globalRoles);
        projectRoles = projectRoles == null ? Map.of() : Map.copyOf(projectRoles);
        source = source == null ? "dev" : source;
    }

    /** A dev-mode user: roles written as {@code global:<id>} or {@code <polarion project>/<role>}. */
    public static Principal dev(String user, List<String> roles) {
        return new Principal(user, user, global(roles), projects(roles), "dev");
    }

    /** Global role ids of {@code global:<id>} entries. */
    static Set<String> global(List<String> roles) {
        Set<String> global = new TreeSet<>();
        for (String role : roles == null ? List.<String>of() : roles) {
            String r = role.strip();
            if (r.startsWith("global:")) {
                global.add(r.substring(7));
            }
        }
        return global;
    }

    /** Project roles of {@code <project>/<role>} entries (Polarion's own form of a project role id). */
    static Map<String, Set<String>> projects(List<String> roles) {
        Map<String, Set<String>> projects = new TreeMap<>();
        for (String role : roles == null ? List.<String>of() : roles) {
            String r = role.strip();
            int slash = r.indexOf('/');
            if (!r.startsWith("global:") && slash > 0 && slash < r.length() - 1) {
                projects.computeIfAbsent(r.substring(0, slash), k -> new TreeSet<>()).add(r.substring(slash + 1));
            }
        }
        projects.replaceAll((k, v) -> Set.copyOf(v));
        return projects;
    }

    public Set<String> rolesIn(String polarionProject) {
        return polarionProject == null ? Set.of() : this.projectRoles.getOrDefault(polarionProject, Set.of());
    }
}

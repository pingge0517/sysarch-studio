package com.example.sysarch.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Preparatory configuration only. No mode-switch or remote-write endpoint is exposed in M0/M1. */
@Component
@ConfigurationProperties(prefix = "sysarch.polarion-store")
public class PolarionStoreProperties {
    private boolean enabled;
    private java.util.Map<String,java.util.Set<String>> requirementProjects=java.util.Map.of();
    public boolean isEnabled() { return this.enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public java.util.Map<String,java.util.Set<String>> getRequirementProjects() { return this.requirementProjects; }
    public void setRequirementProjects(java.util.Map<String,java.util.Set<String>> projects) {
        var copy=new java.util.HashMap<String,java.util.Set<String>>();
        projects.forEach((source,targets)->{
            validProject(source); targets.forEach(PolarionStoreProperties::validProject); copy.put(source,java.util.Set.copyOf(targets));
        });
        this.requirementProjects=java.util.Map.copyOf(copy);
    }
    public boolean canReferenceRequirement(String modelProject,String requirementProject) {
        return modelProject.equals(requirementProject) || this.requirementProjects.getOrDefault(modelProject,java.util.Set.of()).contains(requirementProject);
    }
    private static void validProject(String project) {
        if(project==null || !project.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("Invalid requirement project scope");
    }

    public boolean writesAllowed() { return false; }
    public ModelStorageMode defaultMode() { return ModelStorageMode.LOCAL; }
}

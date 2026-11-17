package com.example.sysarch.sync;

import com.example.sysarch.access.Access;
import com.example.sysarch.access.Permission;
import com.example.sysarch.access.ProjectBindings;
import com.example.sysarch.branch.BranchWorkspaces;
import com.example.sysarch.identity.CurrentUser;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.sync.model.ModelGateway;
import com.example.sysarch.validation.ValidationError;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Browser sync selection: resolve the model to its Polarion project, never silently sync a branch's trunk. */
@RestController
public class SyncWorkspaceController {

    public record Element(String id, String name, String type, String layer, String workItemType, List<ValidationError> errors) { }

    public record Workspace(String siriusProjectId, String polarionProject, List<Element> elements) { }

    private final ProjectModels projects;
    private final ProjectBindings bindings;
    private final BranchWorkspaces branches;
    private final Access access;
    private final ModelGateway model;
    private final SyncProperties properties;

    public SyncWorkspaceController(ProjectModels projects, ProjectBindings bindings, BranchWorkspaces branches,
            Access access, ModelGateway model, SyncProperties properties) {
        this.projects = projects;
        this.bindings = bindings;
        this.branches = branches;
        this.access = access;
        this.model = model;
        this.properties = properties;
    }

    @GetMapping("/api/v1/projects/{project}/sync-workspace")
    public Workspace workspace(@PathVariable String project, HttpServletRequest request) {
        if (CurrentUser.principal(request).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        String sirius;
        try {
            sirius = this.projects.siriusProjectId(project);
        } catch (ProjectModels.UnknownProjectException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        if (this.branches.branchOf(sirius).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "分支不能直接同步到 Polarion；请合并到主干后，在主干执行同步");
        }
        String polarion = this.bindings.polarionProjectOf(sirius)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模型尚未绑定 Polarion 项目，请在角色与审计页配置"));
        this.access.requirePolarion(request, polarion, Permission.SYNC);
        List<Element> elements = this.model.readAll(polarion).values().stream()
                .filter(e -> this.properties.getWorkItemTypes().containsKey(e.type()))
                .map(e -> new Element(e.id(), e.name(), e.type(), e.layer(), this.properties.getWorkItemTypes().get(e.type()), e.errors()))
                .sorted(Comparator.comparing(Element::id)).toList();
        return new Workspace(sirius, polarion, elements);
    }
}

package com.example.sysarch.project;

import com.example.sysarch.access.Access;
import com.example.sysarch.access.Permission;
import com.example.sysarch.link.LinkRepository;
import com.example.sysarch.link.LinkRepository.StoredLink;
import com.example.sysarch.sync.SyncProperties;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Objects;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Embedding in Polarion: where to go from a Polarion page. {@code GET /api/v1/embed/target?polarionProject=&workItem=}
 * gives the model of the Polarion project ({@code sysarch.polarion.projects}) and, with a Work Item, the element bound
 * to it and its place in Sirius Web (as {@code /elements/{id}/location}). A Work Item bound to nothing still gives the
 * model, with {@code elementId} null and the reason, so the page can open the model instead. Signed-in users with read access in the
 * Polarion project (any signed-in role); 404 for a Polarion project bound to no model.
 */
@RestController
@RequestMapping("/api/v1/embed")
public class EmbedController {

    public record Target(String polarionProject, String siriusProjectId, String projectName, String workItemId, String elementId, String linkState,
            String objectId, String path, String note) {
    }

    private final SyncProperties polarion;

    private final ProjectModels models;

    private final LinkRepository links;

    private final Access access;

    private final HttpServletRequest request;

    public EmbedController(SyncProperties polarion, ProjectModels models, LinkRepository links, Access access, HttpServletRequest request) {
        this.polarion = Objects.requireNonNull(polarion);
        this.models = Objects.requireNonNull(models);
        this.links = Objects.requireNonNull(links);
        this.access = access;
        this.request = request;
    }

    @GetMapping("/target")
    public Target target(@RequestParam String polarionProject, @RequestParam(name = "workItem", required = false) String workItemId) {
        if (this.access != null) {
            if (this.access.principal(this.request).isEmpty()) {
                // REPORT is the observer's permission, which everybody has: the embedded page is for signed-in users only
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录（从 Polarion 打开会自动登录）");
            }
            this.access.requirePolarion(this.request, polarionProject, Permission.REPORT);
        }
        String model = this.polarion.getProjects().get(polarionProject);
        if (model == null || model.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Polarion 项目 " + polarionProject + " 没有绑定架构模型（sysarch.polarion.projects）");
        }
        String siriusProjectId;
        String name;
        try {
            siriusProjectId = this.models.siriusProjectId(model);
            name = this.models.projectName(siriusProjectId);
        } catch (ProjectModels.UnknownProjectException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Polarion 项目 " + polarionProject + " 绑定的模型 " + model + " 不存在");
        }
        String projectPath = "/projects/" + siriusProjectId + "/edit";
        if (workItemId == null || workItemId.isBlank()) {
            return new Target(polarionProject, siriusProjectId, name, null, null, null, null, projectPath, null);
        }
        Optional<StoredLink> link = this.links.findByProject(polarionProject, null).stream().filter(l -> workItemId.equals(l.workItemId())).findFirst();
        if (link.isEmpty()) {
            return new Target(polarionProject, siriusProjectId, name, workItemId, null, null, null, projectPath, "Work Item " + workItemId + " 没有绑定模型元素");
        }
        String elementId = link.get().elementId();
        Optional<String> objectId = this.models.read(siriusProjectId, resources -> ElementLocationController.find(resources, elementId));
        if (objectId.isEmpty()) {
            return new Target(polarionProject, siriusProjectId, name, workItemId, elementId, link.get().state(), null, projectPath,
                    "绑定的元素 " + elementId + " 已不在模型中");
        }
        return new Target(polarionProject, siriusProjectId, name, workItemId, elementId, link.get().state(), objectId.get(),
                projectPath + "?selection=" + objectId.get(), null);
    }
}

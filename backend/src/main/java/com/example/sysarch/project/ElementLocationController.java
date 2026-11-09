package com.example.sysarch.project;

import com.example.sysarch.ElementIds;
import com.example.sysarch.model.ArchElement;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Where an element is in Sirius Web (P1 T7 step 3: a diff row jumps to its element):
 * {@code GET /api/v1/projects/{project}/elements/{elementId}/location} gives the Sirius project id, the element's Sirius
 * object id (its id in the JSON document) and the path of Sirius Web's editor with the element selected.
 */
@RestController
@RequestMapping("/api/v1/projects/{project}/elements")
public class ElementLocationController {

    public record Location(String elementId, String siriusProjectId, String objectId, String path) {
    }

    private final ProjectModels models;

    public ElementLocationController(ProjectModels models) {
        this.models = Objects.requireNonNull(models);
    }

    @GetMapping("/{elementId}/location")
    public Location locate(@PathVariable String project, @PathVariable String elementId) {
        String siriusProjectId;
        Optional<String> objectId;
        try {
            siriusProjectId = this.models.siriusProjectId(project);
            objectId = this.models.read(project, resources -> find(resources, elementId));
        } catch (ProjectModels.UnknownProjectException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        String object = objectId.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no element '" + elementId + "' in " + project));
        return new Location(elementId, siriusProjectId, object, "/projects/" + siriusProjectId + "/edit?selection=" + object);
    }

    /** The element's id in its Sirius JSON document (the URI fragment), which Sirius Web selects by. */
    static Optional<String> find(Iterable<Resource> resources, String elementId) {
        for (Resource resource : resources) {
            for (TreeIterator<EObject> it = resource.getAllContents(); it.hasNext();) {
                if (it.next() instanceof ArchElement element && elementId.equals(ElementIds.of(element))) {
                    return Optional.of(EcoreUtil.getURI(element).fragment());
                }
            }
        }
        return Optional.empty();
    }
}

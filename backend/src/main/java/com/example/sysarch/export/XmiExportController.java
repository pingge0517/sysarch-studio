package com.example.sysarch.export;

import com.example.sysarch.config.FeatureProperties;
import com.example.sysarch.project.ProjectModels;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code POST /api/v1/export/xmi} (P3 T7, spec §5.1; not in the spec's OpenAPI, so hand-written like the other P3
 * endpoints): body {@code {"projectId": "...", "umlVersion": "2.5.1"|"2.5"}} (umlVersion optional: the UML namespace; 2.5 for
 * importers that do not know UML 2.5.1; "flavour": "papyrus" writes the form Eclipse Papyrus SysML 1.6 stores, as {@code <project>.uml}) (a mapped Polarion project id, or a Sirius project id or name), answer
 * the whole project as XMI 2.5.1 ({@link XmiExporter}), read on the project's editing context, as a download
 * {@code <project>.xmi}. Switch {@code sysarch.features.xmi} (spec §9 "XMI（关导出入口）"): off = 503.
 */
@RestController
@RequestMapping("/api/v1")
public class XmiExportController {

    private final ProjectModels models;

    private final FeatureProperties features;

    public XmiExportController(ProjectModels models, FeatureProperties features) {
        this.models = Objects.requireNonNull(models);
        this.features = Objects.requireNonNull(features);
    }

    @PostMapping(path = "/export/xmi", produces = "application/xml")
    public ResponseEntity<String> export(@RequestBody Map<String, Object> body) {
        FeatureProperties.require(this.features.isXmi(), "XMI 导出");
        Object projectId = body == null ? null : body.get("projectId");
        if (!(projectId instanceof String project) || project.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectId is required");
        }
        Object requested = body.get("umlVersion");
        XmiExporter.UmlVersion version = XmiExporter.UmlVersion.of(requested == null ? null : requested.toString());
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "umlVersion 只能是 2.5.1（默认）或 2.5");
        }
        Object requestedFlavour = body.get("flavour");
        XmiExporter.Flavour flavour = XmiExporter.Flavour.of(requestedFlavour == null ? null : requestedFlavour.toString());
        if (flavour == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "flavour 只能是 omg（默认）或 papyrus");
        }
        try {
            String name = this.models.projectName(project);
            String xmi = this.models.read(project, resources -> XmiExporter.export(name, resources, version, flavour));
            return ResponseEntity.ok()
                    .contentType(new MediaType("application", "xml", StandardCharsets.UTF_8))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name + (flavour == XmiExporter.Flavour.PAPYRUS ? ".uml" : ".xmi"), StandardCharsets.UTF_8).build().toString())
                    .body(xmi);
        } catch (ProjectModels.UnknownProjectException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}

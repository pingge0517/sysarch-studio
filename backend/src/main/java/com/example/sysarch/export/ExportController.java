package com.example.sysarch.export;

import com.example.sysarch.model.Layer;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.sync.api.ExportApi;
import com.example.sysarch.sync.api.model.ExportSysmlv2PostRequest;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code POST /api/v1/export/sysmlv2} (spec P1 §4.1, T8): the generated {@link ExportApi}. {@code projectId} is a
 * mapped Polarion project id, or a Sirius project id or name; the package is named after the Sirius project. The text
 * is read on the project's editing context, so it is a consistent state of the model.
 */
@RestController
@RequestMapping("/api/v1")
public class ExportController implements ExportApi {

    private final ProjectModels models;

    public ExportController(ProjectModels models) {
        this.models = Objects.requireNonNull(models);
    }

    @Override
    public ResponseEntity<String> exportSysmlv2Post(ExportSysmlv2PostRequest request) {
        if (request == null || request.getProjectId() == null || request.getProjectId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectId is required");
        }
        SysmlV2Exporter.Scope scope = request.getScope() == null ? SysmlV2Exporter.Scope.PROJECT : SysmlV2Exporter.Scope.valueOf(request.getScope().getValue());
        Layer layer = null;
        if (request.getLayer() != null && !request.getLayer().isBlank()) {
            layer = Layer.get(request.getLayer());
            if (layer == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown layer '" + request.getLayer() + "'");
            }
        }
        Layer selectedLayer = layer;
        try {
            String name = this.models.projectName(request.getProjectId());
            String text = this.models.read(request.getProjectId(),
                    resources -> SysmlV2Exporter.export(name, resources, scope, selectedLayer, request.getElementIds()));
            return ResponseEntity.ok().contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8)).body(text);
        } catch (SysmlV2Exporter.InvalidExportException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (ProjectModels.UnknownProjectException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}

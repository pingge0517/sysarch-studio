package com.example.sysarch.trace;

import com.example.sysarch.project.ProjectModels;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code GET /api/v1/trace/matrix?projectId=} (spec P1 §4.1). Hand-written rather than implementing the generated
 * {@code TraceApi}, whose return type is {@code ResponseEntity<Void>} because the OpenAPI gives no response schema.
 * {@code projectId} is a Polarion project id mapped in {@code sysarch.polarion.projects}, or a Sirius project id or name.
 */
@RestController
@RequestMapping("/api/v1")
public class TraceController {

    private final ProjectModels models;

    public TraceController(ProjectModels models) {
        this.models = Objects.requireNonNull(models);
    }

    @GetMapping("/trace/matrix")
    public TraceMatrix matrix(@RequestParam("projectId") String projectId) {
        try {
            return this.models.read(projectId, resources -> TraceMatrix.of(projectId, resources));
        } catch (ProjectModels.UnknownProjectException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}

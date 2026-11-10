package com.example.sysarch.baseline;

import com.example.sysarch.baseline.BaselineRepository.StoredBaseline;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.sync.api.model.Baseline;
import com.example.sysarch.sync.api.model.BaselinesPostRequest;
import com.example.sysarch.sync.api.model.ModelDiff;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code /api/v1/baselines} (spec P1 §4.1). Hand-written rather than implementing the generated {@code BaselinesApi}:
 * its list endpoint returns {@code ResponseEntity<Void>} because the OpenAPI gives the list no schema; this returns
 * {@code Baseline[]}. Request and response models are the generated ones. {@code projectId} is a mapped Polarion
 * project id, or a Sirius project id or name; responses carry the Sirius project id.
 */
@RestController
@RequestMapping("/api/v1/baselines")
public class BaselineController {

    private final BaselineService baselines;

    private final ProjectModels models;

    private final com.example.sysarch.access.Access access;

    public BaselineController(BaselineService baselines, ProjectModels models, com.example.sysarch.access.Access access) {
        this.baselines = Objects.requireNonNull(baselines);
        this.models = Objects.requireNonNull(models);
        this.access = Objects.requireNonNull(access);
    }

    @PostMapping
    public ResponseEntity<Baseline> create(@RequestBody BaselinesPostRequest request, jakarta.servlet.http.HttpServletRequest http) {
        if (request == null || request.getProjectId() == null || request.getProjectId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectId is required");
        }
        String project = this.call(() -> this.models.siriusProjectId(request.getProjectId()));
        String actor = this.access.require(http, project, com.example.sysarch.access.Permission.BASELINE_CREATE).user();
        StoredBaseline created = this.call(() -> this.baselines.create(request.getProjectId(), request.getLabel(), request.getDescription(), actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(toApi(created));
    }

    /** P2 T8: deleting a baseline is for administrators and audited ({@code BASELINE_DELETE}); 409 while a branch uses it. */
    @org.springframework.web.bind.annotation.DeleteMapping("/{baselineId}")
    public ResponseEntity<Void> delete(@PathVariable String baselineId, jakarta.servlet.http.HttpServletRequest http) {
        StoredBaseline baseline = this.call(() -> this.baselines.find(baselineId));
        String actor = this.access.require(http, baseline.projectId(), com.example.sysarch.access.Permission.BASELINE_DELETE).user();
        try {
            this.baselines.delete(baselineId, actor);
        } catch (BaselineService.InUseException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<Baseline> list(@RequestParam("projectId") String projectId) {
        return this.call(() -> this.baselines.list(projectId)).stream().map(BaselineController::toApi).toList();
    }

    @GetMapping("/{baselineId}/diff")
    public ModelDiff diff(@PathVariable String baselineId, @RequestParam(value = "against", required = false) String against,
            @RequestParam(value = "otherBaselineId", required = false) String otherBaselineId) {
        return this.call(() -> this.baselines.diff(baselineId, against, otherBaselineId));
    }

    private <T> T call(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (BaselineService.InvalidRequestException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (BaselineService.UnknownBaselineException | ProjectModels.UnknownProjectException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    static Baseline toApi(StoredBaseline baseline) {
        return new Baseline().id(baseline.id()).projectId(baseline.projectId()).label(baseline.label()).createdAt(baseline.createdAt())
                .elementCount(baseline.elementCount()).modelRev(baseline.modelRev());
    }
}

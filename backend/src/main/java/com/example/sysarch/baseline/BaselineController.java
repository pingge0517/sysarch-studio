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
import org.springframework.web.bind.annotation.RequestHeader;
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

    public BaselineController(BaselineService baselines) {
        this.baselines = Objects.requireNonNull(baselines);
    }

    @PostMapping
    public ResponseEntity<Baseline> create(@RequestBody BaselinesPostRequest request,
            @RequestHeader(value = "X-Sysarch-User", required = false) String user) {
        if (request == null || request.getProjectId() == null || request.getProjectId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectId is required");
        }
        String actor = user == null || user.isBlank() ? "anonymous" : user;
        StoredBaseline created = this.call(() -> this.baselines.create(request.getProjectId(), request.getLabel(), request.getDescription(), actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(toApi(created));
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

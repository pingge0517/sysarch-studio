package com.example.sysarch.baseline;

import com.example.sysarch.ModelStamp;
import com.example.sysarch.baseline.BaselineRepository.StoredBaseline;
import com.example.sysarch.baseline.ModelSnapshot.ElementState;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.sync.api.model.ModelDiff;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Baselines and model diffs (P1 T7).
 *
 * <ul>
 * <li><b>Create</b>: the snapshot is taken as one task on the project's editing context ({@link ProjectModels}), which
 * runs edits one at a time, so a baseline never sees half an edit (T7 step 4: "禁止在编辑事务中途创建"). It is bound
 * to the model version by {@code model_rev}: the model's change counter ({@link ModelStamp}) at that moment. The rows
 * are written in one transaction.</li>
 * <li><b>Projects</b>: {@code project_id} is the resolved Sirius project id, so a project named by its Polarion id,
 * Sirius id or name always finds the same baselines.</li>
 * <li><b>Diff</b>: baseline → current model ({@code against=CURRENT}, the default) or baseline → another baseline of the
 * same project ({@code against=BASELINE&otherBaselineId=}); see {@link ModelDiffs}.</li>
 * </ul>
 */
@Service
public class BaselineService {

    /** A bad request (400 at the API). */
    public static class InvalidRequestException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public InvalidRequestException(String message) {
            super(message);
        }
    }

    /** An unknown baseline (404 at the API). */
    public static class UnknownBaselineException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public UnknownBaselineException(String message) {
            super(message);
        }
    }

    private record Capture(Map<String, ElementState> elements, long stamp) {
    }

    private final ProjectModels models;

    private final BaselineRepository repository;

    private final TransactionTemplate transaction;

    public BaselineService(ProjectModels models, BaselineRepository repository, TransactionTemplate transaction) {
        this.models = Objects.requireNonNull(models);
        this.repository = Objects.requireNonNull(repository);
        this.transaction = Objects.requireNonNull(transaction);
    }

    public StoredBaseline create(String project, String label, String description, String actor) {
        if (label == null || label.isBlank()) {
            throw new InvalidRequestException("label is required");
        }
        if (label.length() > 200) {
            throw new InvalidRequestException("label is longer than 200 characters");
        }
        String siriusProjectId = this.models.siriusProjectId(project);
        Capture capture = this.models.read(project, resources -> new Capture(ModelSnapshot.capture(resources),
                resources.isEmpty() ? 0 : ModelStamp.of(resources.get(0).getResourceSet() != null ? resources.get(0).getResourceSet() : resources.get(0))));
        StoredBaseline baseline = new StoredBaseline(UUID.randomUUID().toString(), siriusProjectId, label, description, (int) capture.stamp(),
                capture.elements().size(), actor, OffsetDateTime.now());
        this.transaction.executeWithoutResult(status -> this.repository.insert(baseline, capture.elements().values()));
        return baseline;
    }

    public List<StoredBaseline> list(String project) {
        return this.repository.list(this.models.siriusProjectId(project));
    }

    /**
     * @param against {@code CURRENT} (default) or {@code BASELINE}
     * @param otherBaselineId for {@code BASELINE}: the baseline to compare with
     */
    public ModelDiff diff(String baselineId, String against, String otherBaselineId) {
        StoredBaseline baseline = this.repository.find(baselineId).orElseThrow(() -> new UnknownBaselineException("no baseline '" + baselineId + "'"));
        Map<String, ElementState> before = this.repository.elements(baselineId);
        String mode = against == null || against.isBlank() ? (otherBaselineId == null ? "CURRENT" : "BASELINE") : against;
        if ("BASELINE".equals(mode)) {
            if (otherBaselineId == null || otherBaselineId.isBlank()) {
                throw new InvalidRequestException("against=BASELINE needs otherBaselineId");
            }
            StoredBaseline other = this.repository.find(otherBaselineId)
                    .orElseThrow(() -> new UnknownBaselineException("no baseline '" + otherBaselineId + "'"));
            if (!other.projectId().equals(baseline.projectId())) {
                throw new InvalidRequestException("the two baselines belong to different projects");
            }
            return ModelDiffs.diff(before, this.repository.elements(otherBaselineId));
        }
        if (!"CURRENT".equals(mode)) {
            throw new InvalidRequestException("against must be CURRENT or BASELINE");
        }
        return ModelDiffs.diff(before, this.models.read(baseline.projectId(), ModelSnapshot::capture));
    }
}

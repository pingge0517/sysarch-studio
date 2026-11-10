package com.example.sysarch.merge;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.baseline.BaselineRepository;
import com.example.sysarch.baseline.BaselineService;
import com.example.sysarch.baseline.ModelSnapshot;
import com.example.sysarch.baseline.ModelSnapshot.ElementState;
import com.example.sysarch.branch.BranchService;
import com.example.sysarch.identity.Editors.Editor;
import com.example.sysarch.merge.MergeEngine.Resolution;
import com.example.sysarch.merge.MergeEngine.Result;
import com.example.sysarch.merge.MergeEngine.Strategy;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.validation.ChangeGuard;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.validation.ValidationError;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Merging a branch into its trunk (P2 §3.2–3.3, T4): PREVIEW computes and stores the result without writing anything;
 * APPLY names a preview, recomputes it, and refuses if the trunk or the branch changed since, or if a conflict has no
 * choice (user's decision). Then it takes a baseline of the trunk (the way back, risk table), writes the result into the
 * trunk in one task on its editing context (all or nothing: the vetoed constraints are checked on the result, and the
 * trunk must then equal the result exactly), records the run, and closes the branch: MERGED, workspace deleted, changeset
 * and run kept (user's decision). The merging user is the editor, so element locks and the editor limit apply.
 */
@Service
public class MergeService {

    /** A request that cannot be served now (409 at the API), with what stands in the way. */
    public static class MergeRefusedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient View view;

        public MergeRefusedException(String message, View view) {
            super(message);
            this.view = view;
        }

        public View view() {
            return this.view;
        }
    }

    /** Thrown inside the apply task when the merged trunk breaks a vetoed constraint (the task is undone). */
    static class InvalidResultException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        final transient List<ValidationError> errors;

        InvalidResultException(List<ValidationError> errors) {
            super("合并结果违反约束：" + String.join("；", errors.stream().map(ValidationError::message).toList()));
            this.errors = errors;
        }
    }

    /** What the API returns for PREVIEW and APPLY; {@code merged} is the number of changes to the trunk (spec §4.5). */
    public record View(String runId, String mode, String branchId, String status, Strategy strategy, int merged, List<MergeEngine.Conflict> conflicts,
            List<MergeEngine.Conflict> unresolved, List<MergeEngine.AutoResolved> autoResolved, List<MergeEngine.Operation> operations,
            MergeEngine.Stats stats, boolean applicable, boolean autoMergeRefused, String preMergeBaseline, List<ValidationError> errors) {
    }

    private final Logger logger = LoggerFactory.getLogger(MergeService.class);

    private final JdbcClient jdbc;

    private final BranchService branches;

    private final BaselineRepository baselines;

    private final BaselineService baselineService;

    private final ProjectModels models;

    private final ConstraintValidator constraints;

    private final AuditLog audit;

    private final ObjectMapper json;

    public MergeService(JdbcClient jdbc, BranchService branches, BaselineRepository baselines, BaselineService baselineService, ProjectModels models,
            ConstraintValidator constraints, AuditLog audit, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.branches = Objects.requireNonNull(branches);
        this.baselines = Objects.requireNonNull(baselines);
        this.baselineService = Objects.requireNonNull(baselineService);
        this.models = Objects.requireNonNull(models);
        this.constraints = Objects.requireNonNull(constraints);
        this.audit = Objects.requireNonNull(audit);
        this.json = Objects.requireNonNull(json);
    }

    private record Sides(BranchService.BranchView branch, Map<String, ElementState> base, Map<String, ElementState> theirs, Map<String, ElementState> ours) {
    }

    /** Computes the merge and stores it as a preview; writes nothing to any model. */
    public View preview(String branchId, Strategy strategy, List<Resolution> resolutions, String actor) {
        Sides sides = this.sides(branchId);
        Result result = MergeEngine.merge(sides.base(), sides.theirs(), sides.ours(), strategy, resolutions);
        String runId = UUID.randomUUID().toString();
        this.jdbc.sql("INSERT INTO merge_run (id, branch_id, status, strategy, ours_fingerprint, theirs_fingerprint, result, created_by, created_at) "
                + "VALUES (:id, :b, 'PREVIEW', :s, :o, :t, :r, :by, :at)")
                .param("id", runId).param("b", branchId).param("s", strategy.name()).param("o", this.fingerprint(sides.ours()))
                .param("t", this.fingerprint(sides.theirs())).param("r", this.write(summary(result))).param("by", actor)
                .param("at", OffsetDateTime.now(ZoneOffset.UTC)).update();
        return view(runId, "PREVIEW", branchId, "PREVIEW", strategy, result, null, List.of());
    }

    /** Applies a preview (see the class comment). */
    public View apply(String branchId, String runId, Strategy strategy, List<Resolution> resolutions, String actor) {
        if (runId == null || runId.isBlank()) {
            throw new MergeRefusedException("APPLY 需要先 PREVIEW：请带上预演返回的 runId", null);
        }
        Map<String, Object> run = this.jdbc.sql("SELECT * FROM merge_run WHERE id = :id AND branch_id = :b").param("id", runId).param("b", branchId).query()
                .listOfRows().stream().findFirst().orElseThrow(() -> new BranchService.UnknownBranchException("no preview " + runId + " of branch " + branchId));
        if (!"PREVIEW".equals(run.get("status"))) {
            throw new MergeRefusedException("预演 " + runId + " 已是 " + run.get("status"), null);
        }
        Sides sides = this.sides(branchId);
        Result result = MergeEngine.merge(sides.base(), sides.theirs(), sides.ours(), strategy, resolutions);
        if (!this.fingerprint(sides.ours()).equals(run.get("ours_fingerprint")) || !this.fingerprint(sides.theirs()).equals(run.get("theirs_fingerprint"))) {
            throw new MergeRefusedException("预演之后主干或分支又有修改，请重新预演", view(runId, "APPLY", branchId, "STALE", strategy, result, null, List.of()));
        }
        if (!result.applicable()) {
            throw new MergeRefusedException("还有 " + result.unresolved().size() + " 项冲突或悬空引用未处理：每个冲突都要选择取主干或取分支",
                    view(runId, "APPLY", branchId, "PREVIEW", strategy, result, null, List.of()));
        }

        String trunk = sides.branch().projectId();
        String baseline = this.baselineService.create(trunk, "合并前（分支 " + sides.branch().name() + "）", "合并分支前自动建立，可用于回退", actor).id();
        try {
            this.models.write(trunk, new Editor(actor), resources -> {
                var resourceSet = resources.get(0).getResourceSet();
                ChangeRecorder recorder = resourceSet != null ? new ChangeRecorder(resourceSet) : new ChangeRecorder(resources.get(0));
                ChangeDescription change;
                try {
                    MergeApplier.apply(resources, result.merged(), result.operations());
                } catch (RuntimeException e) {
                    recorder.endRecording().applyAndReverse(); // never leave the trunk half-merged
                    recorder.dispose();
                    throw e;
                }
                change = recorder.endRecording();
                recorder.dispose();
                List<ValidationError> introduced = resourceSet == null ? List.of() : new ChangeGuard(this.constraints).check(resourceSet, change);
                if (!introduced.isEmpty()) {
                    throw new InvalidResultException(introduced); // ChangeGuard already undid the change
                }
                Map<String, ElementState> now = ModelSnapshot.capture(resources);
                if (!now.equals(result.merged())) {
                    change.applyAndReverse();
                    throw new IllegalStateException("合并后主干与合并结果不一致，已撤销（" + now.size() + " 个元素，应为 " + result.merged().size() + "）");
                }
                return new ProjectModels.Outcome<>(null, true);
            });
        } catch (InvalidResultException e) {
            this.jdbc.sql("UPDATE merge_run SET status = 'REJECTED', pre_merge_baseline = :bl WHERE id = :id").param("bl", baseline).param("id", runId).update();
            throw new MergeRefusedException(e.getMessage(), view(runId, "APPLY", branchId, "REJECTED", strategy, result, baseline, e.errors));
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.jdbc.sql("UPDATE merge_run SET status = 'APPLIED', strategy = :s, result = :r, pre_merge_baseline = :bl, applied_at = :at WHERE id = :id")
                .param("s", strategy.name()).param("r", this.write(summary(result))).param("bl", baseline).param("at", now).param("id", runId).update();
        this.branches.markMerged(branchId);
        this.audit.append(trunk, actor, com.example.sysarch.audit.AuditLog.MERGE, null, "branch " + sides.branch().name() + " (" + branchId + "): " + result.stats() + ", run " + runId
                + ", pre-merge baseline " + baseline);
        this.logger.info("Merged branch {} into project {} by {}: {}", branchId, trunk, actor, result.stats());
        return view(runId, "APPLY", branchId, "APPLIED", strategy, result, baseline, List.of());
    }

    private Sides sides(String branchId) {
        BranchService.BranchView branch = this.branches.get(branchId);
        if (!BranchService.OPEN.equals(branch.status())) {
            throw new MergeRefusedException("分支「" + branch.name() + "」已是 " + branch.status() + "，不能再合并", null);
        }
        Map<String, ElementState> base = this.baselines.elements(branch.baseBaseline());
        Map<String, ElementState> theirs = this.models.read(branch.workspaceProjectId(), ModelSnapshot::capture);
        Map<String, ElementState> ours = this.models.read(branch.projectId(), ModelSnapshot::capture);
        return new Sides(branch, base, theirs, ours);
    }

    private static View view(String runId, String mode, String branchId, String status, Strategy strategy, Result result, String baseline,
            List<ValidationError> errors) {
        return new View(runId, mode, branchId, status, strategy, result.operations().size(), result.conflicts(), result.unresolved(), result.autoResolved(),
                result.operations(), result.stats(), result.applicable(), result.autoMergeRefused(), baseline, errors);
    }

    /** What is stored for a run: everything but the merged model itself. */
    private static Map<String, Object> summary(Result result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("stats", result.stats());
        summary.put("operations", result.operations());
        summary.put("conflicts", result.conflicts());
        summary.put("unresolved", result.unresolved());
        summary.put("autoResolved", result.autoResolved());
        return summary;
    }

    private String fingerprint(Map<String, ElementState> states) {
        try {
            byte[] bytes = this.json.writeValueAsString(new TreeMap<>(states)).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(Object value) {
        try {
            return this.json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}

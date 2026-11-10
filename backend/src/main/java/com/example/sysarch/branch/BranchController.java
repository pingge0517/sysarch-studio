package com.example.sysarch.branch;

import com.example.sysarch.baseline.SnapshotModels;
import com.example.sysarch.identity.CurrentUser;
import com.example.sysarch.project.ProjectModels;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code /api/v1/branches} (P2 §4.5 {@code /branches} POST/GET, T3). Hand-written: the spec's operations have no
 * request or response schema. {@code /branches/{id}/merge} comes with T4.
 *
 * <ul>
 * <li>{@code POST /branches} {@code {projectId, name, baselineId}}: 201 with the branch and its workspace project; 400
 * for a missing name or baseline, a baseline of another project, or a workspace as trunk; 409 for a taken name or a
 * baseline without structure (from before P2).</li>
 * <li>{@code GET /branches?projectId=}: the trunk's branches; without {@code projectId}: every open branch.</li>
 * <li>{@code GET /branches/{id}} and {@code GET /branches/{id}/changes}: the changeset, one net change per element.</li>
 * <li>{@code POST /branches/{id}/leave}: the user switches away; their locks in the workspace end and their session
 * closes (T3 step 3).</li>
 * <li>{@code DELETE /branches/{id}}: abandon the branch (workspace deleted, changeset kept, audited; T8 restricts it to
 * administrators).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

    public record CreateRequest(String projectId, String name, String baselineId) {
    }

    private final BranchService branches;

    public BranchController(BranchService branches) {
        this.branches = Objects.requireNonNull(branches);
    }

    @PostMapping
    public ResponseEntity<BranchService.BranchView> create(@RequestBody CreateRequest request, HttpServletRequest http) {
        if (request == null || request.projectId() == null || request.projectId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectId is required");
        }
        String actor = CurrentUser.of(http).orElse("anonymous");
        return ResponseEntity.status(HttpStatus.CREATED).body(this.branches.create(request.projectId(), request.name(), request.baselineId(), actor));
    }

    @GetMapping
    public List<BranchService.BranchView> list(@RequestParam(value = "projectId", required = false) String projectId) {
        return projectId == null || projectId.isBlank() ? this.branches.open() : this.branches.list(projectId);
    }

    @GetMapping("/{branchId}")
    public BranchService.BranchView get(@PathVariable String branchId) {
        return this.branches.get(branchId);
    }

    @GetMapping("/{branchId}/changes")
    public List<BranchService.Change> changes(@PathVariable String branchId) {
        return this.branches.changes(branchId);
    }

    @PostMapping("/{branchId}/leave")
    public Map<String, Integer> leave(@PathVariable String branchId, HttpServletRequest http) {
        String user = CurrentUser.of(http).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "leaving a branch needs a user"));
        return Map.of("releasedLocks", this.branches.leave(branchId, user));
    }

    @DeleteMapping("/{branchId}")
    public BranchService.BranchView abandon(@PathVariable String branchId, HttpServletRequest http) {
        return this.branches.abandon(branchId, CurrentUser.of(http).orElse("anonymous"));
    }

    @ExceptionHandler(BranchService.BranchRequestException.class)
    public ResponseEntity<Map<String, String>> refused(BranchService.BranchRequestException e) {
        return ResponseEntity.status(e.conflict() ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler({ BranchService.UnknownBranchException.class, ProjectModels.UnknownProjectException.class })
    public ResponseEntity<Map<String, String>> unknown(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(SnapshotModels.UnbuildableException.class)
    public ResponseEntity<Map<String, String>> unbuildable(SnapshotModels.UnbuildableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "基线无法还原成模型：" + e.getMessage()));
    }
}

package com.example.sysarch.merge;

import com.example.sysarch.branch.BranchService;
import com.example.sysarch.identity.CurrentUser;
import com.example.sysarch.lock.EditAdmission;
import com.example.sysarch.lock.LockService;
import com.example.sysarch.merge.MergeEngine.Resolution;
import com.example.sysarch.merge.MergeEngine.Strategy;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.session.SessionService;

import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code POST /api/v1/branches/{branchId}/merge} (P2 §4.5, T4). Request: the spec's {@code {mode: PREVIEW|APPLY,
 * strategy: MODEL_WINS|POLARION_WINS}} plus {@code runId} (APPLY: the preview being confirmed) and
 * {@code resolutions: [{elementId, field?, take: OURS|THEIRS}]} (field omitted: every conflict of the element). The
 * strategy defaults to POLARION_WINS and only decides Polarion-owned fields (user's decision).
 *
 * <p>Response 200: the spec's {@code {merged, conflicts: [{elementId, field, base, theirs, ours}]}}, each conflict
 * with its {@code kind}, plus {@code runId}, {@code operations}, {@code unresolved}, {@code autoResolved},
 * {@code stats}, {@code applicable}, {@code autoMergeRefused} (> 50 conflicts) and, after APPLY,
 * {@code preMergeBaseline}. 409 with the same body when APPLY is refused (stale preview, unresolved conflicts, a
 * vetoed constraint in the result); 423 / 429 when an element of the trunk is locked or the trunk has its maximum of
 * editors.
 */
@RestController
@RequestMapping("/api/v1/branches")
public class MergeController {

    public record MergeRequest(String mode, String strategy, String runId, List<Resolution> resolutions) {
    }

    private final MergeService merges;

    public MergeController(MergeService merges) {
        this.merges = Objects.requireNonNull(merges);
    }

    @PostMapping("/{branchId}/merge")
    public MergeService.View merge(@PathVariable String branchId, @RequestBody(required = false) MergeRequest request, HttpServletRequest http) {
        String mode = request == null || request.mode() == null ? "PREVIEW" : request.mode();
        Strategy strategy;
        try {
            strategy = request == null || request.strategy() == null ? Strategy.POLARION_WINS : Strategy.valueOf(request.strategy());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "strategy must be MODEL_WINS or POLARION_WINS");
        }
        List<Resolution> resolutions = request == null || request.resolutions() == null ? List.of() : request.resolutions();
        String actor = CurrentUser.of(http).orElse(null);
        return switch (mode) {
            case "PREVIEW" -> this.merges.preview(branchId, strategy, resolutions, actor == null ? "anonymous" : actor);
            case "APPLY" -> {
                if (actor == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "merging writes the trunk: it needs a user (the sysarch_user cookie or X-Sysarch-User)");
                }
                yield this.merges.apply(branchId, request.runId(), strategy, resolutions, actor);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode must be PREVIEW or APPLY");
        };
    }

    @ExceptionHandler(MergeService.MergeRefusedException.class)
    public ResponseEntity<Map<String, Object>> refused(MergeService.MergeRefusedException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", e.getMessage());
        body.put("result", e.view());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(LockService.LockedException.class)
    public ResponseEntity<Map<String, Object>> locked(LockService.LockedException e) {
        return ResponseEntity.status(HttpStatus.LOCKED).body(Map.of("message", "主干上有元素被他人锁定，合并未执行：" + e.getMessage(), "errors", EditAdmission.reasons(e)));
    }

    @ExceptionHandler(SessionService.FullException.class)
    public ResponseEntity<Map<String, Object>> full(SessionService.FullException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler({ BranchService.UnknownBranchException.class, ProjectModels.UnknownProjectException.class })
    public ResponseEntity<Map<String, String>> unknown(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }
}

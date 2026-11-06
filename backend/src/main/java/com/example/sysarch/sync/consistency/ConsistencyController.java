package com.example.sysarch.sync.consistency;

import com.example.sysarch.sync.api.model.ConsistencyRepairPostRequest;
import com.example.sysarch.sync.api.model.ConsistencyReport;
import com.example.sysarch.sync.polarion.PolarionToken;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code /api/v1/consistency/report} and {@code /api/v1/consistency/repair} (spec §6.1, §7).
 *
 * <p>Hand-written rather than implementing the generated {@code ConsistencyApi}: the verbatim OpenAPI gives repair's
 * 200 response no schema ("修复结果，含逐条处理动作"), so the generated method returns {@code ResponseEntity<Void>} and
 * could not carry the per-issue actions. Paths, parameters and request bodies are the OpenAPI's, using its models.
 *
 * <p>{@code Authorization: Bearer <Polarion token>} is optional for the report (without it, Polarion is not asked)
 * and needed by repairs that read or write Polarion.
 */
@RestController
@RequestMapping("/api/v1/consistency")
public class ConsistencyController {

    static final String ACTOR_HEADER = "X-Sysarch-User";

    private final ConsistencyService consistency;

    private final HttpServletRequest request;

    public ConsistencyController(ConsistencyService consistency, HttpServletRequest request) {
        this.consistency = Objects.requireNonNull(consistency);
        this.request = Objects.requireNonNull(request);
    }

    @GetMapping(value = "/report", produces = "application/json")
    public ConsistencyReport report(@RequestParam("projectId") String projectId) {
        return this.consistency.report(projectId, this.token());
    }

    @PostMapping(value = "/repair", produces = "application/json", consumes = "application/json")
    public ConsistencyService.RepairResult repair(@RequestBody ConsistencyRepairPostRequest body) {
        if (body.getProjectId() == null || body.getProjectId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectId is required");
        }
        String strategy = body.getStrategy() == null ? null : body.getStrategy().getValue();
        String actor = this.request.getHeader(ACTOR_HEADER);
        return this.consistency.repair(body.getProjectId(), body.getIssueIds(), strategy, this.token(), actor == null || actor.isBlank() ? "anonymous" : actor);
    }

    private PolarionToken token() {
        return PolarionToken.fromAuthorizationHeader(this.request.getHeader(HttpHeaders.AUTHORIZATION));
    }
}

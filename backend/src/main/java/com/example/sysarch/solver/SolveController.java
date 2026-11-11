package com.example.sysarch.solver;

import com.example.sysarch.access.Access;
import com.example.sysarch.access.Permission;
import com.example.sysarch.config.FeatureProperties;
import com.example.sysarch.identity.CurrentUser;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.sync.api.SolveApi;
import com.example.sysarch.sync.api.model.SolvePost200Response;
import com.example.sysarch.sync.api.model.SolvePostRequest;

import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code POST /api/v1/solve} (spec P3 §3.3, the generated {@link SolveApi}): 200 with the result (converged or not),
 * 422 when the system cannot be solved (unit not on the whitelist, overdetermined, underdetermined, an expression
 * refused by the scan, a solver error) with {@code reasons}, 504 after the 30 s limit (nothing written), 503 when the
 * solver is not configured or {@code sysarch.features.solver} is off. Writing back is an edit: the user needs the
 * modelling role.
 */
@RestController
@RequestMapping("/api/v1")
public class SolveController implements SolveApi {

    private final SolveService solve;

    private final ProjectModels models;

    private final Access access;

    private final FeatureProperties features;

    private final HttpServletRequest http;

    public SolveController(SolveService solve, ProjectModels models, Access access, FeatureProperties features, HttpServletRequest http) {
        this.solve = Objects.requireNonNull(solve);
        this.models = Objects.requireNonNull(models);
        this.access = Objects.requireNonNull(access);
        this.features = Objects.requireNonNull(features);
        this.http = Objects.requireNonNull(http);
    }

    @Override
    public ResponseEntity<SolvePost200Response> solvePost(SolvePostRequest request) {
        FeatureProperties.require(this.features.isSolver(), "参数求解");
        String project;
        try {
            project = this.models.siriusProjectId(request.getProjectId());
        } catch (ProjectModels.UnknownProjectException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        this.access.require(this.http, project, Permission.MODEL_EDIT); // the result is written to the model
        Map<String, Double> inputs = new LinkedHashMap<>();
        if (request.getInputs() != null) {
            request.getInputs().forEach((k, v) -> inputs.put(k, v == null ? null : v.doubleValue()));
        }
        double tolerance = request.getOptions() == null || request.getOptions().getTolerance() == null ? 1e-6 : request.getOptions().getTolerance().doubleValue();
        int maxIterations = request.getOptions() == null || request.getOptions().getMaxIterations() == null ? 200 : request.getOptions().getMaxIterations();
        String solver = request.getSolver() == null ? "python" : request.getSolver().getValue();
        SolveService.Answer answer = this.solve.solve(project, request.getConstraintBlockIds(), solver, inputs, request.getTargets(), tolerance, maxIterations,
                CurrentUser.editor(this.http));
        SolvePost200Response body = new SolvePost200Response().converged(answer.converged()).iterations(answer.iterations()).solverLog(answer.solverLog());
        answer.outputs().forEach((k, v) -> body.putOutputsItem(k, BigDecimal.valueOf(v)));
        answer.residuals().forEach((k, v) -> body.putResidualsItem(k, BigDecimal.valueOf(v)));
        return ResponseEntity.ok(body);
    }

    @ExceptionHandler(SolveService.Refused.class)
    public ResponseEntity<ProblemDetail> refused(SolveService.Refused e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(e.status()), e.getMessage());
        problem.setProperty("reasons", e.reasons());
        return ResponseEntity.status(e.status()).body(problem);
    }

    /** Solved, but writing back is an edit and someone else holds a parameter (P2 locks): 423, nothing written. */
    @ExceptionHandler(com.example.sysarch.lock.LockService.LockedException.class)
    public ResponseEntity<ProblemDetail> locked(com.example.sysarch.lock.LockService.LockedException e) {
        String message = "求解已完成，但回写被拒绝：" + e.getMessage() + "；未写回任何值";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED, message);
        problem.setProperty("reasons", List.of(message));
        return ResponseEntity.status(HttpStatus.LOCKED).body(problem);
    }

    /** Solved, but the project has its maximum of editors (P2 T2): 429, nothing written. */
    @ExceptionHandler(com.example.sysarch.session.SessionService.FullException.class)
    public ResponseEntity<ProblemDetail> full(com.example.sysarch.session.SessionService.FullException e) {
        String message = "求解已完成，但回写被拒绝：" + e.getMessage() + "；未写回任何值";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, message);
        problem.setProperty("reasons", List.of(message));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problem);
    }

    @ExceptionHandler(ConstraintSolver.SolverTimeout.class)
    public ResponseEntity<ProblemDetail> timeout(ConstraintSolver.SolverTimeout e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, e.getMessage());
        problem.setProperty("reasons", List.of(e.getMessage()));
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(problem);
    }
}

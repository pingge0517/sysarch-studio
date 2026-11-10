package com.example.sysarch.sync;

import com.example.sysarch.link.LinksController;
import com.example.sysarch.sync.polarion.PolarionException;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Error responses of the {@code /api/v1} REST API, as JSON problem details with the right status.
 *
 * <p>Covers the controllers of this package and its subpackages (consistency, webhooks) and the links controller.
 *
 * <p>Needed because of the Sirius Web frontend: without this, Spring forwards errors to {@code /error}, where the
 * frontend's single-page-app route (GET only) answers instead. A POST error then became a 405 and a GET error a 200
 * with the HTML app page. Found in the live smoke test; unit tests call the controllers directly and cannot see it.
 */
@RestControllerAdvice(basePackageClasses = { SyncController.class, LinksController.class, com.example.sysarch.view.table.TableController.class,
        com.example.sysarch.trace.TraceController.class, com.example.sysarch.baseline.BaselineController.class,
        com.example.sysarch.project.ElementLocationController.class, com.example.sysarch.export.ExportController.class,
        com.example.sysarch.lock.LockController.class, com.example.sysarch.session.SessionController.class,
        com.example.sysarch.branch.BranchController.class, com.example.sysarch.merge.MergeController.class,
        com.example.sysarch.report.ReportController.class, com.example.sysarch.sysmlimport.ImportController.class,
        com.example.sysarch.access.Access.class, com.example.sysarch.identity.Identities.class, com.example.sysarch.comment.CommentController.class })
public class ApiErrorHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> status(ResponseStatusException e) {
        return problem(e.getStatusCode(), e.getReason());
    }

    /** Missing required header (Idempotency-Key) or query parameter (projectId). */
    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ProblemDetail> binding(ServletRequestBindingException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, "unreadable request body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> typeMismatch(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid value for " + e.getName());
    }

    /** Polarion failed while a request needed it (e.g. a live consistency report): 429 passed on, else 502. */
    /** P2 T8: the role does not allow the action (already audited). */
    @ExceptionHandler(com.example.sysarch.access.ForbiddenException.class)
    public ResponseEntity<ProblemDetail> forbidden(com.example.sysarch.access.ForbiddenException e) {
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.FORBIDDEN, e.getMessage());
        response.getBody().setProperty("code", "FORBIDDEN");
        response.getBody().setProperty("message", e.getMessage());
        response.getBody().setProperty("role", e.role() == null ? null : e.role().name());
        return response;
    }

    @ExceptionHandler(PolarionException.class)
    public ResponseEntity<ProblemDetail> polarion(PolarionException e) {
        if (e instanceof PolarionException.RateLimited limited) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(org.springframework.http.HttpHeaders.RETRY_AFTER,
                    Long.toString(Math.max(1, limited.retryAfterSeconds()))).body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage()));
        }
        return problem(HttpStatus.BAD_GATEWAY, "Polarion: " + e.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatusCode status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail == null ? "" : detail));
    }
}

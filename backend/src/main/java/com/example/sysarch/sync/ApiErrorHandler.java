package com.example.sysarch.sync;

import com.example.sysarch.link.LinksController;

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
 * <p>Needed because of the Sirius Web frontend: without this, Spring forwards errors to {@code /error}, where the
 * frontend's single-page-app route (GET only) answers instead. A POST error then became a 405 and a GET error a 200
 * with the HTML app page. Found in the live smoke test; unit tests call the controllers directly and cannot see it.
 */
@RestControllerAdvice(basePackageClasses = { SyncController.class, LinksController.class })
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

    private static ResponseEntity<ProblemDetail> problem(HttpStatusCode status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail == null ? "" : detail));
    }
}

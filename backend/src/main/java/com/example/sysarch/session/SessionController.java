package com.example.sysarch.session;

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
 * {@code /api/v1/sessions} (P2 T2; the spec's OpenAPI has no session paths). Edits open sessions by themselves; this
 * is for pages that open one explicitly, close it when the user leaves, and show who is editing.
 *
 * <ul>
 * <li>{@code POST /sessions} {@code {projectId, clientInfo?}}: 201 with the user's open session (the existing one if
 * any, kept active), or 429 {@code {message, open, limit}} "当前项目编辑人数已满".</li>
 * <li>{@code DELETE /sessions/{id}}: 204; only the session's user.</li>
 * <li>{@code GET /sessions?projectId=}: {@code {limit, sessions}}, the open sessions.</li>
 * </ul>
 * The user is {@link CurrentUser}; anonymous callers cannot open a session (400).
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    public record OpenRequest(String projectId, String clientInfo) {
    }

    public record Sessions(int limit, List<SessionService.Session> sessions) {
    }

    public record Full(String message, int open, int limit) {
    }

    private final SessionService sessions;

    private final ProjectModels models;

    public SessionController(SessionService sessions, ProjectModels models) {
        this.sessions = Objects.requireNonNull(sessions);
        this.models = Objects.requireNonNull(models);
    }

    @PostMapping
    public ResponseEntity<SessionService.Session> open(@RequestBody OpenRequest request, HttpServletRequest http) {
        if (request == null || request.projectId() == null || request.projectId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectId is required");
        }
        String client = request.clientInfo() != null ? request.clientInfo() : http.getHeader("User-Agent");
        return ResponseEntity.status(HttpStatus.CREATED).body(this.sessions.touch(this.models.siriusProjectId(request.projectId()), user(http), client));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> close(@PathVariable String sessionId, HttpServletRequest http) {
        this.sessions.close(sessionId, user(http));
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public Sessions list(@RequestParam("projectId") String projectId) {
        return new Sessions(this.sessions.limit(), this.sessions.list(this.models.siriusProjectId(projectId)));
    }

    @ExceptionHandler(SessionService.FullException.class)
    public ResponseEntity<Full> full(SessionService.FullException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "60").body(new Full(e.getMessage(), e.open(), e.limit()));
    }

    @ExceptionHandler(SessionService.NoSuchSessionException.class)
    public ResponseEntity<Map<String, String>> none(SessionService.NoSuchSessionException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(ProjectModels.UnknownProjectException.class)
    public ResponseEntity<Map<String, String>> unknownProject(ProjectModels.UnknownProjectException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    private static String user(HttpServletRequest http) {
        return CurrentUser.of(http)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "an edit session needs a user (the sysarch_user cookie or X-Sysarch-User)"));
    }
}

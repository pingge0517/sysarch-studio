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

    private final com.example.sysarch.access.Access access;

    public SessionController(SessionService sessions, ProjectModels models, com.example.sysarch.access.Access access) {
        this.sessions = Objects.requireNonNull(sessions);
        this.models = Objects.requireNonNull(models);
        this.access = Objects.requireNonNull(access);
    }

    @PostMapping
    public ResponseEntity<SessionService.Session> open(@RequestBody OpenRequest request, HttpServletRequest http) {
        if (request == null || request.projectId() == null || request.projectId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectId is required");
        }
        String client = request.clientInfo() != null ? request.clientInfo() : http.getHeader("User-Agent");
        String project = this.models.siriusProjectId(request.projectId());
        String user = user(http);
        this.access.require(http, project, com.example.sysarch.access.Permission.MODEL_EDIT); // P2 T8: an edit session is for modellers
        return ResponseEntity.status(HttpStatus.CREATED).body(this.sessions.touch(project, user, client));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> close(@PathVariable String sessionId, HttpServletRequest http) {
        this.sessions.close(sessionId, user(http));
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public Sessions list(@RequestParam("projectId") String projectId) {
        String project = this.models.siriusProjectId(projectId);
        return new Sessions(this.sessions.limit(project), this.sessions.list(project));
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录：编辑会话属于一个用户"));
    }
}

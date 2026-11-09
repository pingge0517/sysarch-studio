package com.example.sysarch.sync;

import com.example.sysarch.sync.api.SyncApi;
import com.example.sysarch.sync.api.model.SyncPullPostRequest;
import com.example.sysarch.sync.api.model.SyncPushPostRequest;
import com.example.sysarch.sync.api.model.SyncResult;
import com.example.sysarch.sync.polarion.PolarionToken;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code /api/v1/sync/push} and {@code /api/v1/sync/pull} (spec §6.1, §6.3).
 *
 * <ul>
 * <li>The caller's {@code Authorization: Bearer <Polarion token>} is passed through to Polarion; without it, 401.</li>
 * <li>{@code Idempotency-Key}: a replay within 24 hours returns the first response, status included. Push requires it
 * (OpenAPI). Spec §6.3 asks the same of pull, but the verbatim OpenAPI has no such parameter for pull, so pull
 * honours the header when it is sent and does not require it.</li>
 * <li>The audit actor is the {@code X-Sysarch-User} header (set by the frontend or an SSO proxy), else
 * {@code anonymous}: the Polarion token itself is never stored, not even as the actor.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class SyncController implements SyncApi {

    static final String ACTOR_HEADER = "X-Sysarch-User";

    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final SyncService sync;

    private final SyncStore store;

    private final ObjectMapper json;

    private final HttpServletRequest request;

    private final SyncStatusStore status;

    public SyncController(SyncService sync, SyncStore store, ObjectMapper json, HttpServletRequest request, SyncStatusStore status) {
        this.status = Objects.requireNonNull(status);
        this.sync = Objects.requireNonNull(sync);
        this.store = Objects.requireNonNull(store);
        this.json = Objects.requireNonNull(json);
        this.request = Objects.requireNonNull(request);
    }

    @Override
    public ResponseEntity<SyncResult> syncPushPost(String idempotencyKey, SyncPushPostRequest body) {
        PolarionToken token = this.token();
        String projectId = required(body.getProjectId(), "projectId");
        List<String> elementIds = body.getElementIds() == null ? List.of() : body.getElementIds();
        boolean dryRun = Boolean.TRUE.equals(body.getDryRun());
        return this.idempotent(idempotencyKey, "push", () -> {
            SyncService.Outcome outcome = this.sync.push(projectId, elementIds, dryRun, token, this.actor());
            if (!dryRun && outcome.status() == 200 && outcome.result().getErrors().isEmpty()) {
                this.status.pushed(projectId, java.time.OffsetDateTime.now()); // P1 T6: the panel's "上次推送"
            }
            return outcome;
        });
    }

    @Override
    public ResponseEntity<SyncResult> syncPullPost(SyncPullPostRequest body) {
        PolarionToken token = this.token();
        String projectId = required(body.getProjectId(), "projectId");
        return this.idempotent(this.request.getHeader(IDEMPOTENCY_HEADER), "pull", () -> {
            SyncService.Outcome outcome = this.sync.pull(projectId, body.getSince(), token, this.actor());
            boolean failed = outcome.status() != 200 || outcome.result().getErrors().stream().anyMatch(e -> ScheduledPull.FAILURES.contains(e.getCode()));
            if (!failed) {
                this.status.pulled(projectId, java.time.OffsetDateTime.now()); // P1 T6: the panel's "上次拉取"
                if (this.status.status(projectId).schedulePaused()) {
                    this.status.resume(projectId); // Polarion answers again: the paused schedule may run
                }
            }
            return outcome;
        });
    }

    private ResponseEntity<SyncResult> idempotent(String key, String endpoint, Supplier<SyncService.Outcome> call) {
        if (key != null && !key.isBlank()) {
            var replay = this.store.replay(key);
            if (replay.isPresent()) {
                return ResponseEntity.status(replay.get().responseStatus()).header("Idempotent-Replayed", "true")
                        .body(this.read(replay.get().responseBody()));
            }
        }
        SyncService.Outcome outcome = call.get();
        if (key != null && !key.isBlank()) {
            this.store.remember(key, endpoint, outcome.status(), this.write(outcome.result()));
        }
        var response = ResponseEntity.status(outcome.status());
        if (outcome.status() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, outcome.retryAfterSeconds())));
        }
        return response.body(outcome.result());
    }

    private PolarionToken token() {
        PolarionToken token = PolarionToken.fromAuthorizationHeader(this.request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "send your Polarion token as Authorization: Bearer <token>");
        }
        return token;
    }

    private String actor() {
        String actor = this.request.getHeader(ACTOR_HEADER);
        return actor == null || actor.isBlank() ? "anonymous" : actor;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return value;
    }

    private String write(SyncResult result) {
        try {
            return this.json.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private SyncResult read(String body) {
        try {
            return this.json.readValue(body, SyncResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unreadable stored response", e);
        }
    }
}

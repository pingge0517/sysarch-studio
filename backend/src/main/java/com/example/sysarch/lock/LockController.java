package com.example.sysarch.lock;

import com.example.sysarch.identity.CurrentUser;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.project.ProjectModels;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.emf.ecore.util.EcoreUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code /api/v1/locks} (P2 §2.2, §4.5, T1). Hand-written: the generated {@code LocksApi} returns
 * {@code ResponseEntity<Void>}, and the spec's rules need more than its two operations.
 *
 * <ul>
 * <li>{@code POST /locks} {@code {projectId, elementIds (≤50), ownerUser, leaseSeconds (30–300, default 90)}}: 201
 * {@code {locks}}, or 423 {@code {message, conflicts: [{elementId, ownerUser, expiresAt}]}} and nothing taken;
 * taking a lock you hold renews it; 404 for elements not in the model.</li>
 * <li>{@code PUT /locks/{id}}: the heartbeat (spec §2.2, every 45 s): 200 with the new expiry, 404 once expired.</li>
 * <li>{@code DELETE /locks?projectId=&elementId=[&force=true]} (the OpenAPI's form; {@code projectId} added because
 * element ids are unique only within a project) and {@code DELETE /locks/{id}[?force=true]} (spec §2.3): 204.
 * {@code force} releases another user's lock and is audited; T8 limits it to administrators.</li>
 * <li>{@code GET /locks?projectId=}: the held locks of a project.</li>
 * </ul>
 *
 * <p>The acting user is {@link CurrentUser} (header or cookie). Without one, {@code ownerUser} (body or query) is
 * taken as given until T8; with one, a different {@code ownerUser} is refused (403). {@code projectId} is a Sirius
 * project id or name or a mapped Polarion project id, as everywhere.
 */
@RestController
@RequestMapping("/api/v1/locks")
public class LockController {

    public record AcquireRequest(String projectId, List<String> elementIds, String ownerUser, Integer leaseSeconds) {
    }

    public record Locks(List<LockService.Lock> locks) {
    }

    public record Refusal(String message, List<LockService.Conflict> conflicts) {
    }

    private final LockService locks;

    private final ProjectModels models;

    private final com.example.sysarch.access.Access access;

    private final com.example.sysarch.config.FeatureProperties features;

    public LockController(LockService locks, ProjectModels models, com.example.sysarch.access.Access access, com.example.sysarch.config.FeatureProperties features) {
        this.features = Objects.requireNonNull(features);
        this.access = Objects.requireNonNull(access);
        this.locks = Objects.requireNonNull(locks);
        this.models = Objects.requireNonNull(models);
    }

    @PostMapping
    public ResponseEntity<Locks> acquire(@RequestBody AcquireRequest request, HttpServletRequest http) {
        if (request == null || request.projectId() == null || request.projectId().isBlank() || request.elementIds() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectId, elementIds and ownerUser are required");
        }
        com.example.sysarch.config.FeatureProperties.require(this.features.isLocks(), "编辑锁");
        String owner = this.actor(http, request.ownerUser());
        String projectId = this.project(request.projectId());
        this.access.require(http, projectId, com.example.sysarch.access.Permission.MODEL_EDIT); // P2 T8
        Set<String> missing = new HashSet<>(request.elementIds());
        missing.removeAll(this.models.read(projectId, LockController::elementIds));
        missing.remove(null);
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not in the model: " + String.join(", ", missing.stream().sorted().toList()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(new Locks(this.locks.acquire(projectId, request.elementIds(), owner, request.leaseSeconds())));
    }

    @PutMapping("/{lockId}")
    public LockService.Lock renew(@PathVariable String lockId, @RequestParam(value = "ownerUser", required = false) String ownerUser, HttpServletRequest http) {
        return this.locks.renew(lockId, this.actor(http, ownerUser));
    }

    @DeleteMapping
    public ResponseEntity<Void> release(@RequestParam("projectId") String projectId, @RequestParam("elementId") String elementId,
            @RequestParam(value = "force", defaultValue = "false") boolean force, @RequestParam(value = "ownerUser", required = false) String ownerUser,
            HttpServletRequest http) {
        String project = this.project(projectId);
        String actor = this.actor(http, ownerUser);
        if (force && this.locks.held(project, List.of(elementId)).values().stream().anyMatch(l -> !l.ownerUser().equals(actor))) {
            this.access.require(http, project, com.example.sysarch.access.Permission.LOCK_FORCE); // P2 T8: administrators
        }
        this.locks.release(project, elementId, actor, force);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{lockId}")
    public ResponseEntity<Void> releaseById(@PathVariable String lockId, @RequestParam(value = "force", defaultValue = "false") boolean force,
            @RequestParam(value = "ownerUser", required = false) String ownerUser, HttpServletRequest http) {
        String actor = this.actor(http, ownerUser);
        var lock = this.locks.find(lockId);
        if (force && lock.isPresent() && !lock.get().ownerUser().equals(actor)) {
            this.access.require(http, lock.get().projectId(), com.example.sysarch.access.Permission.LOCK_FORCE); // P2 T8: administrators
        }
        this.locks.releaseById(lockId, actor, force);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public Locks list(@RequestParam("projectId") String projectId) {
        return new Locks(this.locks.list(this.project(projectId)));
    }

    @ExceptionHandler(LockService.LockedException.class)
    public ResponseEntity<Refusal> locked(LockService.LockedException e) {
        return ResponseEntity.status(HttpStatus.LOCKED).body(new Refusal(e.getMessage(), e.conflicts()));
    }

    @ExceptionHandler(LockService.InvalidLockRequestException.class)
    public ResponseEntity<Map<String, String>> invalid(LockService.InvalidLockRequestException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(LockService.NoSuchLockException.class)
    public ResponseEntity<Map<String, String>> gone(LockService.NoSuchLockException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(LockService.NotOwnerException.class)
    public ResponseEntity<Map<String, String>> notOwner(LockService.NotOwnerException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(ProjectModels.UnknownProjectException.class)
    public ResponseEntity<Map<String, String>> unknownProject(ProjectModels.UnknownProjectException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    /** The acting user: the request's identity (P2 T8: a declared owner alone no longer counts). */
    private String actor(HttpServletRequest http, String declared) {
        String identity = CurrentUser.of(http).orElse(null);
        String owner = declared == null || declared.isBlank() ? null : declared.strip();
        if (identity != null && owner != null && !identity.equals(owner)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ownerUser '" + owner + "' is not the current user '" + identity + "'");
        }
        if (identity == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录：锁属于一个用户");
        }
        return identity;
    }

    private String project(String project) {
        return this.models.siriusProjectId(project);
    }

    private static Set<String> elementIds(List<org.eclipse.emf.ecore.resource.Resource> resources) {
        Set<String> ids = new HashSet<>();
        resources.forEach(r -> r.getAllContents().forEachRemaining(e -> {
            if (e instanceof ArchElement) {
                String id = EcoreUtil.getID(e);
                if (id != null) {
                    ids.add(id);
                }
            }
        }));
        return ids;
    }
}

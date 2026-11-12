package com.example.sysarch.link;

import com.example.sysarch.link.LinkRepository.StoredLink;
import com.example.sysarch.sync.api.LinksApi;
import com.example.sysarch.sync.api.model.Link;
import com.example.sysarch.sync.api.model.LinkRequest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code /api/v1/links} (spec §6.1, T7 step 3): bind a model element to a Work Item, list bindings, unbind.
 *
 * <p>POST is idempotent: binding an element to the Work Item it is already bound to is a 201 again, binding it to a
 * different one is a 409. The Work Item's existence is not checked here: a binding to a Work Item that does not
 * exist (or no longer exists) is what consistency rule R1 reports (T8). Unbinding never touches the Work Item.
 */
@RestController
@RequestMapping("/api/v1")
public class LinksController implements LinksApi {

    private final LinkRepository links;

    private final com.example.sysarch.sync.SyncStore store;

    private final jakarta.servlet.http.HttpServletRequest request;

    private final com.example.sysarch.access.Access access;

    /** Tests: no permission checks, no pool, no audit. */
    public LinksController(LinkRepository links) {
        this(links, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LinksController(LinkRepository links, com.example.sysarch.sync.SyncStore store, jakarta.servlet.http.HttpServletRequest request,
            com.example.sysarch.access.Access access) {
        this.links = Objects.requireNonNull(links);
        this.store = store;
        this.request = request;
        this.access = access;
    }

    /**
     * Binding and unbinding change what sync writes to Polarion: the SYNC permission in the Polarion project, as push and
     * pull (P2 T8; these two endpoints had been left without a check).
     */
    private void require(String polarionProject) {
        if (this.access != null) {
            this.access.requirePolarion(this.request, polarionProject, com.example.sysarch.access.Permission.SYNC);
        }
    }

    private String actor() {
        return this.request == null ? "anonymous" : com.example.sysarch.identity.CurrentUser.of(this.request).orElse("anonymous");
    }

    /**
     * {@code GET /api/v1/sync/unbound?projectId=}: the Work Items created in Polarion that no element is bound to yet
     * (spec §6.2 "未绑定池"), oldest first. Claiming one = {@code POST /links} with its id, which takes it out of the
     * pool; the next push writes the element's model-owned fields to it.
     */
    @org.springframework.web.bind.annotation.GetMapping("/sync/unbound")
    public List<com.example.sysarch.sync.SyncStore.PooledWorkItem> unbound(@org.springframework.web.bind.annotation.RequestParam String projectId) {
        this.require(projectId);
        return this.store == null ? List.of() : this.store.unboundPool(projectId);
    }

    @Override
    public ResponseEntity<List<Link>> linksGet(String projectId, String state) {
        if (state != null) {
            parse(Link.StateEnum::fromValue, state, "state");
        }
        return ResponseEntity.ok(this.links.findByProject(projectId, state).stream().map(LinksController::toDto).toList());
    }

    @Override
    public ResponseEntity<Void> linksPost(LinkRequest request) {
        parse(Link.ElementTypeEnum::fromValue, request.getElementType(), "elementType");
        parse(Link.WorkItemTypeEnum::fromValue, request.getWorkItemType(), "workItemType");
        this.require(request.getProjectId());
        Optional<StoredLink> existing = this.links.find(request.getElementId());
        if (existing.isPresent()) {
            if (!sameBinding(existing.get(), request)) {
                throw conflict(existing.get());
            }
            return ResponseEntity.status(HttpStatus.CREATED).build();
        }
        try {
            this.links.insert(new StoredLink(request.getElementId(), request.getElementType(), request.getWorkItemId(), request.getWorkItemType(),
                    request.getProjectId(), Link.StateEnum.ACTIVE.getValue(), null, null, null, null, null, null, null));
        } catch (DuplicateKeyException e) {
            // Bound concurrently by someone else between the check and the insert.
            return this.links.find(request.getElementId()).filter(l -> sameBinding(l, request))
                    .map(l -> ResponseEntity.status(HttpStatus.CREATED).<Void>build())
                    .orElseThrow(() -> conflict(this.links.find(request.getElementId()).orElseThrow()));
        }
        if (this.store != null) {
            this.store.removeFromUnboundPool(request.getProjectId(), request.getWorkItemId());
            this.store.audit(request.getProjectId(), request.getElementId(), request.getWorkItemId(), "BIND", "link", null, request.getWorkItemId(), this.actor());
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<Void> linksElementIdDelete(String elementId) {
        Optional<StoredLink> existing = this.links.find(elementId);
        if (existing.isEmpty()) {
            return ResponseEntity.noContent().build(); // nothing bound: nothing to check or do
        }
        this.require(existing.get().projectId());
        this.links.delete(elementId);
        if (this.store != null) {
            this.store.audit(existing.get().projectId(), elementId, existing.get().workItemId(), "UNBIND", "link", existing.get().workItemId(), null, this.actor());
        }
        return ResponseEntity.noContent().build();
    }

    static Link toDto(StoredLink link) {
        Link dto = new Link(link.elementId(), Link.ElementTypeEnum.fromValue(link.elementType()), link.workItemId(), link.projectId(),
                Link.StateEnum.fromValue(link.state()));
        if (link.workItemType() != null) {
            dto.setWorkItemType(Link.WorkItemTypeEnum.fromValue(link.workItemType()));
        }
        dto.setModelRev(link.modelRev());
        dto.setWiRev(link.wiRev());
        dto.setEtag(link.etag());
        dto.setLastPushAt(link.lastPushAt());
        dto.setLastPullAt(link.lastPullAt());
        return dto;
    }

    private static boolean sameBinding(StoredLink link, LinkRequest request) {
        return link.workItemId().equals(request.getWorkItemId()) && link.projectId().equals(request.getProjectId());
    }

    private static ResponseStatusException conflict(StoredLink existing) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "element " + existing.elementId() + " is already bound to work item " + existing.workItemId() + " in project " + existing.projectId());
    }

    /** Values outside the OpenAPI enums are a 400, not a 500. */
    private static <T> T parse(Function<String, T> parser, String value, String field) {
        try {
            return parser.apply(value);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid " + field + ": " + value);
        }
    }
}

package com.example.sysarch.branch;

import com.example.sysarch.ElementIds;
import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.baseline.BaselineRepository;
import com.example.sysarch.baseline.ModelDiffs;
import com.example.sysarch.baseline.ModelSnapshot;
import com.example.sysarch.baseline.ModelSnapshot.ElementState;
import com.example.sysarch.baseline.SnapshotModels;
import com.example.sysarch.branch.BranchWorkspaces.Branch;
import com.example.sysarch.lock.LockService;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.DiagramLayout;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.session.SessionService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.web.application.project.dto.CreateProjectInput;
import org.eclipse.sirius.web.application.project.dto.CreateProjectSuccessPayload;
import org.eclipse.sirius.web.application.project.dto.DeleteProjectInput;
import org.eclipse.sirius.web.application.project.services.api.IProjectApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Branches (P2 §3.1, T3). A branch forks from a baseline of its trunk project and is edited in its own Sirius project,
 * the workspace (user's decision "独立工作区项目"): the baseline's snapshot rebuilt into one document, no diagrams (user's
 * decision; views are created as needed). The trunk is never touched.
 *
 * <p>Changeset (user's decision "每个元素一条净变更"): after every edit that went through in a workspace,
 * {@link #recordEdit} compares each affected element with its state in the base baseline and keeps one row per element:
 * ADD (not in the base), MODIFY (differs; {@code fields} = what differs) or DELETE (in the base, gone); an element
 * back in its base state has no row. The payload holds the element's state now (null for DELETE), which is what the
 * three-way merge (T4) aligns.
 */
@Service
public class BranchService {

    public static final String OPEN = "OPEN";

    public static final String ABANDONED = "ABANDONED";

    public static final String MERGED = "MERGED";

    /** A request that cannot be served as asked (400/409 at the API; the message says why). */
    public static class BranchRequestException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final boolean conflict;

        public BranchRequestException(String message, boolean conflict) {
            super(message);
            this.conflict = conflict;
        }

        public boolean conflict() {
            return this.conflict;
        }
    }

    /** No such branch (404). */
    public static class UnknownBranchException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public UnknownBranchException(String message) {
            super(message);
        }
    }

    /** One changeset row: the net change of one element against the base. */
    public record Change(String elementId, String op, List<String> fields, ElementState state, OffsetDateTime createdAt) {
    }

    /** A branch as the API shows it. */
    public record BranchView(String id, String projectId, String name, String baseBaseline, String baselineLabel, String status, String createdBy,
            OffsetDateTime createdAt, String workspaceProjectId, String workspaceName, int changes) {
    }

    private final Logger logger = LoggerFactory.getLogger(BranchService.class);

    private final JdbcClient jdbc;

    private final BranchWorkspaces workspaces;

    private final BaselineRepository baselines;

    private final ProjectModels models;

    private final IProjectApplicationService projects;

    private final LockService locks;

    private final SessionService sessions;

    private final AuditLog audit;

    private final ObjectMapper json;

    private final String payloadValue;

    /** Base states per branch (a baseline never changes). */
    private final Map<String, Map<String, ElementState>> bases = new ConcurrentHashMap<>();

    public BranchService(JdbcClient jdbc, BranchWorkspaces workspaces, BaselineRepository baselines, ProjectModels models, IProjectApplicationService projects,
            LockService locks, SessionService sessions, AuditLog audit, ObjectMapper json, DataSource dataSource) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.workspaces = Objects.requireNonNull(workspaces);
        this.baselines = Objects.requireNonNull(baselines);
        this.models = Objects.requireNonNull(models);
        this.projects = Objects.requireNonNull(projects);
        this.locks = Objects.requireNonNull(locks);
        this.sessions = Objects.requireNonNull(sessions);
        this.audit = Objects.requireNonNull(audit);
        this.json = Objects.requireNonNull(json);
        this.payloadValue = isPostgres(dataSource) ? "CAST(:payload AS jsonb)" : ":payload";
    }

    /**
     * Forks a branch from a baseline of the project: its workspace project, rebuilt from the baseline, and the branch
     * row. On any failure nothing is left behind.
     */
    public BranchView create(String project, String name, String baselineId, String actor) {
        String trunk = this.models.siriusProjectId(project);
        if (this.workspaces.branchOf(trunk).isPresent()) {
            throw new BranchRequestException("这是一个分支工作区：分支只能从主干拉出", false);
        }
        String branchName = name == null ? "" : name.strip();
        if (branchName.isEmpty() || branchName.length() > 200) {
            throw new BranchRequestException("分支名称不能为空，且不超过 200 个字符", false);
        }
        if (baselineId == null || baselineId.isBlank()) {
            throw new BranchRequestException("必须选择一个基线：分支只能从已有基线拉出（不能从未提交的当前状态拉分支）", false);
        }
        var baseline = this.baselines.find(baselineId).orElseThrow(() -> new BranchRequestException("基线 " + baselineId + " 不存在", false));
        if (!baseline.projectId().equals(trunk)) {
            throw new BranchRequestException("基线 " + baselineId + " 不属于这个项目", false);
        }
        Map<String, ElementState> base = this.baselines.elements(baselineId);
        if (!ModelSnapshot.isStructural(base)) {
            throw new BranchRequestException("基线「" + baseline.label() + "」建于 P2 之前，没有记录元素的层级关系，无法还原成分支；请先新建一个基线", true);
        }
        if (this.jdbc.sql("SELECT COUNT(*) FROM branch WHERE project_id = :p AND name = :n").param("p", trunk).param("n", branchName)
                .query(Integer.class).single() > 0) {
            throw new BranchRequestException("分支「" + branchName + "」已存在", true);
        }
        SnapshotModels.build(base); // fails before anything is created if the baseline cannot be rebuilt

        String trunkName = this.models.projectName(trunk);
        String workspaceName = trunkName + " ⎇ " + branchName;
        IPayload created = this.projects.createProject(new CreateProjectInput(UUID.randomUUID(), workspaceName, List.of()));
        if (!(created instanceof CreateProjectSuccessPayload success)) {
            throw new IllegalStateException("could not create the workspace project: " + created);
        }
        String workspace = success.project().id();
        String id = UUID.randomUUID().toString();
        try {
            this.models.addDocument(workspace, trunkName + "（分支 " + branchName + "）", () -> SnapshotModels.build(base));
            this.jdbc.sql("INSERT INTO branch (id, project_id, name, base_baseline, created_by, created_at, status, workspace_project_id) "
                    + "VALUES (:id, :p, :n, :b, :by, :at, :s, :w)")
                    .param("id", id).param("p", trunk).param("n", branchName).param("b", baselineId).param("by", actor)
                    .param("at", OffsetDateTime.now(ZoneOffset.UTC)).param("s", OPEN).param("w", workspace).update();
        } catch (RuntimeException e) {
            this.projects.deleteProject(new DeleteProjectInput(UUID.randomUUID(), workspace));
            throw e;
        }
        this.workspaces.forget(workspace);
        this.logger.info("Branch {} '{}' of project {} from baseline {} by {}: workspace {}", id, branchName, trunk, baselineId, actor, workspace);
        return this.view(this.find(id));
    }

    /** The branches of a trunk project, newest first. */
    public List<BranchView> list(String project) {
        String trunk = this.models.siriusProjectId(project);
        return this.jdbc.sql("SELECT * FROM branch WHERE project_id = :p ORDER BY created_at DESC, id").param("p", trunk).query(BranchWorkspaces::branch).list()
                .stream().map(this::view).toList();
    }

    /** Every open branch (the shell hides their workspaces from the project list and offers them as workspaces). */
    public List<BranchView> open() {
        return this.jdbc.sql("SELECT * FROM branch WHERE status = 'OPEN' ORDER BY created_at DESC, id").query(BranchWorkspaces::branch).list().stream()
                .map(this::view).toList();
    }

    public BranchView get(String branchId) {
        return this.view(this.find(branchId));
    }

    /** The branch's changeset, by element id. */
    public List<Change> changes(String branchId) {
        this.find(branchId);
        return this.jdbc.sql("SELECT element_id, op, payload, created_at FROM changeset WHERE branch_id = :b ORDER BY element_id").param("b", branchId)
                .query((rs, i) -> this.change(rs.getString(1), rs.getString(2), rs.getString(3), rs.getObject(4, OffsetDateTime.class))).list();
    }

    /**
     * The user switches away from the branch (spec T3 step 3): all their element locks in its workspace end, and their
     * edit session there closes, so the branch's one writer place is free.
     *
     * @return how many locks were released
     */
    public int leave(String branchId, String user) {
        Branch branch = this.find(branchId);
        int released = this.locks.releaseAll(branch.workspaceProjectId(), user);
        this.sessions.closeUser(branch.workspaceProjectId(), user);
        return released;
    }

    /** Gives a branch up: status ABANDONED, its workspace project deleted, the changeset kept for the record (audited). */
    public BranchView abandon(String branchId, String actor) {
        Branch branch = this.find(branchId);
        if (!branch.open()) {
            throw new BranchRequestException("分支「" + branch.name() + "」已是 " + branch.status(), true);
        }
        this.close(branch, ABANDONED);
        this.audit.append(branch.projectId(), actor, com.example.sysarch.audit.AuditLog.BRANCH_ABANDON, null, "branch " + branch.name() + " (" + branchId + ")");
        return this.view(this.find(branchId));
    }

    /** What {@link #projectDeleted} removed. */
    public record Removed(int branches, boolean workspaceOfOpenBranch) {
    }

    /**
     * A Sirius project was deleted. If it was the workspace of an open branch (deleted in Sirius rather than through
     * abandon), that branch is ABANDONED (audited; its changeset stays until its trunk goes). If it was a trunk, its open
     * branches are closed as by abandon (their workspace projects deleted), then every branch row of it goes with its
     * changeset and merge runs: nothing can be merged into a project that no longer exists.
     */
    public Removed projectDeleted(String projectId, String actor) {
        Optional<Branch> workspaceOf = this.jdbc.sql("SELECT * FROM branch WHERE workspace_project_id = :p AND status = 'OPEN'").param("p", projectId)
                .query(BranchWorkspaces::branch).optional();
        workspaceOf.ifPresent(branch -> {
            this.jdbc.sql("UPDATE branch SET status = :s WHERE id = :id").param("s", ABANDONED).param("id", branch.id()).update();
            this.audit.append(branch.projectId(), actor, com.example.sysarch.audit.AuditLog.BRANCH_ABANDON, null,
                    "branch " + branch.name() + " (" + branch.id() + "): workspace project deleted");
        });
        this.workspaces.forget(projectId);
        List<Branch> branches = this.jdbc.sql("SELECT * FROM branch WHERE project_id = :p").param("p", projectId).query(BranchWorkspaces::branch).list();
        for (Branch branch : branches) {
            if (branch.open()) {
                this.close(branch, ABANDONED);
            }
            this.bases.remove(branch.id());
        }
        this.jdbc.sql("DELETE FROM branch WHERE project_id = :p").param("p", projectId).update(); // changesets, merge runs: on delete cascade
        return new Removed(branches.size(), workspaceOf.isPresent());
    }

    /** After its merge into the trunk (T4, user's decision): MERGED, workspace deleted, changeset kept. */
    public void markMerged(String branchId) {
        this.close(this.find(branchId), MERGED);
    }

    private void close(Branch branch, String status) {
        this.jdbc.sql("UPDATE branch SET status = :s WHERE id = :id").param("s", status).param("id", branch.id()).update();
        this.workspaces.forget(branch.workspaceProjectId());
        this.locks.dropProject(branch.workspaceProjectId());
        this.projects.deleteProject(new DeleteProjectInput(UUID.randomUUID(), branch.workspaceProjectId()));
    }

    /**
     * After an edit went through in a Sirius project: if it is an open branch's workspace, the changeset rows of the
     * elements the edit affected are brought up to date. Runs on the editing context's thread.
     */
    public void recordEdit(String projectId, ResourceSet resourceSet, ChangeDescription change) {
        Optional<Branch> branch = this.workspaces.branchOf(projectId);
        if (branch.isEmpty()) {
            return;
        }
        Map<String, ElementState> base = this.bases.computeIfAbsent(branch.get().id(), b -> this.baselines.elements(branch.get().baseBaseline()));
        Set<EObject> affected = new LinkedHashSet<>();
        change.getObjectChanges().keySet().forEach(e -> {
            affected.add(e);
            affected.addAll(e.eContents()); // children placed in or moved into a changed container
        });
        change.getObjectsToDetach().forEach(affected::add);
        boolean unnamedDeleted = false;
        Set<String> deleted = new LinkedHashSet<>();
        for (EObject removed : change.getObjectsToAttach()) {
            if (removed.eResource() == null) {
                List<EObject> tree = new ArrayList<>(List.of(removed));
                removed.eAllContents().forEachRemaining(tree::add);
                for (EObject gone : tree) {
                    String id = gone instanceof ArchElement ? EcoreUtil.getID(gone) : null;
                    if (id != null && !id.isBlank()) {
                        deleted.add(id);
                    } else if (gone instanceof ArchElement) {
                        unnamedDeleted = true; // its id was its place in the document, which it no longer has
                    }
                }
            }
        }
        String branchId = branch.get().id();
        for (EObject element : affected) {
            if (element instanceof ArchElement arch && !(element instanceof DiagramLayout) && element.eResource() != null) {
                ElementState now = ModelSnapshot.state(arch);
                this.write(branchId, now.id(), base.get(now.id()), now);
                deleted.remove(now.id());
            }
        }
        for (String id : deleted) {
            this.write(branchId, id, base.get(id), null);
        }
        if (unnamedDeleted) {
            this.dropAddsNoLongerInTheModel(branchId, resourceSet);
        }
    }

    /** One element's row: none when equal to the base, else ADD / MODIFY / DELETE with the state now. */
    private void write(String branchId, String elementId, ElementState base, ElementState now) {
        String op;
        List<String> fields;
        if (now == null) {
            op = base == null ? null : "DELETE";
            fields = List.of();
        } else if (base == null) {
            op = "ADD";
            fields = List.of();
        } else {
            fields = ModelDiffs.fields(base, now);
            op = fields.isEmpty() ? null : "MODIFY";
        }
        if (op == null) {
            this.jdbc.sql("DELETE FROM changeset WHERE branch_id = :b AND element_id = :e").param("b", branchId).param("e", elementId).update();
            return;
        }
        String payload = this.payload(fields, now);
        int updated = this.jdbc.sql("UPDATE changeset SET op = :op, payload = " + this.payloadValue + ", created_at = :at WHERE branch_id = :b AND element_id = :e")
                .param("op", op).param("payload", payload).param("at", OffsetDateTime.now(ZoneOffset.UTC)).param("b", branchId).param("e", elementId).update();
        if (updated == 0) {
            this.jdbc.sql("INSERT INTO changeset (id, branch_id, element_id, op, payload, created_at) VALUES (:id, :b, :e, :op, " + this.payloadValue + ", :at)")
                    .param("id", UUID.randomUUID().toString()).param("b", branchId).param("e", elementId).param("op", op).param("payload", payload)
                    .param("at", OffsetDateTime.now(ZoneOffset.UTC)).update();
        }
    }

    private void dropAddsNoLongerInTheModel(String branchId, ResourceSet resourceSet) {
        Set<String> present = new HashSet<>();
        resourceSet.getResources().forEach(r -> r.getAllContents().forEachRemaining(e -> {
            if (e instanceof ArchElement) {
                present.add(ElementIds.of(e));
            }
        }));
        this.jdbc.sql("SELECT element_id FROM changeset WHERE branch_id = :b AND op = 'ADD'").param("b", branchId).query(String.class).list().stream()
                .filter(id -> !present.contains(id))
                .forEach(id -> this.jdbc.sql("DELETE FROM changeset WHERE branch_id = :b AND element_id = :e").param("b", branchId).param("e", id).update());
    }

    private Branch find(String branchId) {
        return this.jdbc.sql("SELECT * FROM branch WHERE id = :id").param("id", branchId).query(BranchWorkspaces::branch).optional()
                .orElseThrow(() -> new UnknownBranchException("no branch " + branchId));
    }

    private BranchView view(Branch branch) {
        int changes = this.jdbc.sql("SELECT COUNT(*) FROM changeset WHERE branch_id = :b").param("b", branch.id()).query(Integer.class).single();
        String label = this.baselines.find(branch.baseBaseline()).map(b -> b.label()).orElse(null);
        String workspaceName = this.jdbc.sql("SELECT name FROM project WHERE CAST(id AS VARCHAR) = :w").param("w", String.valueOf(branch.workspaceProjectId()))
                .query(String.class).optional().orElse(null);
        return new BranchView(branch.id(), branch.projectId(), branch.name(), branch.baseBaseline(), label, branch.status(), branch.createdBy(),
                branch.createdAt(), branch.workspaceProjectId(), workspaceName, changes);
    }

    private String payload(List<String> fields, ElementState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fields", fields);
        payload.put("state", state);
        try {
            return this.json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Change change(String elementId, String op, String payload, OffsetDateTime at) {
        try {
            Map<String, Object> map = this.json.readValue(payload, new TypeReference<LinkedHashMap<String, Object>>() { });
            Map<String, Object> state = (Map<String, Object>) map.get("state");
            ElementState element = state == null ? null
                    : new ElementState((String) state.get("id"), (String) state.get("type"), (String) state.get("name"), (String) state.get("layer"),
                            (Map<String, Object>) state.getOrDefault("attributes", Map.of()));
            return new Change(elementId, op, (List<String>) map.getOrDefault("fields", List.of()), element, at);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unreadable changeset payload of " + elementId, e);
        }
    }

    private static boolean isPostgres(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase(java.util.Locale.ROOT).contains("postgres");
        } catch (SQLException e) {
            throw new IllegalStateException("cannot read the database product", e);
        }
    }
}

package com.example.sysarch.branch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.baseline.BaselineRepository;
import com.example.sysarch.baseline.BaselineRepository.StoredBaseline;
import com.example.sysarch.baseline.ModelSnapshot;
import com.example.sysarch.baseline.ModelSnapshot.ElementState;
import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.lock.LockService;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.session.SessionService;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.web.application.project.dto.CreateProjectSuccessPayload;
import org.eclipse.sirius.web.application.project.dto.DeleteProjectInput;
import org.eclipse.sirius.web.application.project.dto.ProjectDTO;
import org.eclipse.sirius.web.application.project.services.api.IProjectApplicationService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import liquibase.integration.spring.SpringLiquibase;

/**
 * P2 T3 acceptance ("在分支改 5 个元素后回到主干，主干数据不变；changeset 恰好 5 条") and the branch rules: fork only from
 * a baseline of the trunk that records structure; the workspace starts equal to the baseline; one net changeset row per
 * element (user's decision); one writer per branch; leaving releases the user's locks and session.
 */
class BranchTest {

    private static final String TRUNK = "trunk-1";

    private static final String WORKSPACE = "ws-1";

    private JdbcClient jdbc;

    private BaselineRepository baselines;

    private LockService locks;

    private SessionService sessions;

    private BranchWorkspaces workspaces;

    private BranchService branches;

    private IProjectApplicationService projects;

    private Resource trunk;

    private final ResourceSet workspaceSet = new ResourceSetImpl();

    private Resource workspace;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        this.lastDataSource = dataSource;
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/sysarch/changelog.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        this.jdbc = JdbcClient.create(dataSource);
        // The branch view reads workspace names from Sirius' project table, which the tests' H2 does not have.
        this.jdbc.sql("CREATE TABLE project (id VARCHAR(64), name VARCHAR(255))").update();
        this.jdbc.sql("INSERT INTO project VALUES ('" + WORKSPACE + "', '域控演示 ⎇ 方案A')").update();
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.baselines = new BaselineRepository(this.jdbc, json, dataSource);
        this.locks = new LockService(this.jdbc, transaction, new AuditLog(this.jdbc), Clock.systemUTC());
        this.workspaces = new BranchWorkspaces(this.jdbc);
        this.sessions = new SessionService(this.jdbc, 30, Duration.ofMinutes(30), Clock.systemUTC(), List.of(this.workspaces));

        this.trunk = new ResourceImpl(URI.createURI("memory:/trunk.json"));
        this.trunk.getContents().addAll(DomainControllerDemo.build());

        ProjectModels models = mock(ProjectModels.class);
        when(models.siriusProjectId(anyString())).thenAnswer(call -> call.getArgument(0));
        when(models.projectName(anyString())).thenReturn("域控演示");
        when(models.addDocument(anyString(), anyString(), any())).thenAnswer(call -> {
            Supplier<List<EObject>> contents = call.getArgument(2);
            this.workspace = new ResourceImpl(URI.createURI("memory:/workspace.json"));
            this.workspaceSet.getResources().add(this.workspace);
            this.workspace.getContents().addAll(contents.get());
            return "doc-1";
        });
        this.projects = mock(IProjectApplicationService.class);
        when(this.projects.createProject(any())).thenReturn(new CreateProjectSuccessPayload(UUID.randomUUID(), new ProjectDTO(WORKSPACE, "ws", List.of())));
        this.branches = new BranchService(this.jdbc, this.workspaces, this.baselines, models, this.projects, this.locks, this.sessions,
                new AuditLog(this.jdbc), json, dataSource);
    }

    // =========================================================== acceptance

    @Test
    void fiveElementsChangedInTheBranchAreFiveRowsAndTheTrunkIsUnchanged() {
        Map<String, ElementState> trunkBefore = ModelSnapshot.capture(List.of(this.trunk));
        BranchService.BranchView branch = this.branches.create(TRUNK, "方案A", this.baseline(), "alice");
        assertEquals(trunkBefore, ModelSnapshot.capture(List.of(this.workspace)), "the workspace starts as the baseline");
        assertEquals("域控演示 ⎇ 方案A", branch.workspaceName());

        this.edit(() -> this.block("PD-002").setName("感知融合单元 v2"));
        this.edit(() -> this.block("PD-003").setDescription("新的描述"));
        this.edit(() -> this.block("PD-006").setLayer(Layer.PHYSICAL)); // same value: no change
        this.edit(() -> this.block("PD-006").getStereotypes().add("asil-d"));
        this.edit(() -> this.requirement("RQ-004").getSatisfiedBy().add(this.block("PD-005")));
        this.edit(() -> this.part("PT-003").setMultiplicity("2"));
        this.edit(() -> this.block("PD-002").setName("感知融合单元 v3")); // the same element again

        List<BranchService.Change> changes = this.branches.changes(branch.id());
        assertEquals(List.of("PD-002", "PD-003", "PD-006", "PT-003", "RQ-004"), changes.stream().map(BranchService.Change::elementId).toList());
        assertTrue(changes.stream().allMatch(c -> c.op().equals("MODIFY")));
        assertEquals(List.of("name"), changes.get(0).fields());
        assertEquals("感知融合单元 v3", changes.get(0).state().name(), "the row holds the element as it is now");
        assertEquals(5, this.branches.get(branch.id()).changes());

        assertEquals(trunkBefore, ModelSnapshot.capture(List.of(this.trunk)), "the trunk did not change");
    }

    // =========================================================== net changeset

    @Test
    void anElementBackInItsBaseStateHasNoRow() {
        BranchService.BranchView branch = this.branches.create(TRUNK, "方案A", this.baseline(), "alice");
        this.edit(() -> this.block("PD-002").setName("改了"));
        this.edit(() -> this.block("PD-002").setName("感知融合单元"));
        assertEquals(List.of(), this.branches.changes(branch.id()));
    }

    @Test
    void addedThenDeletedLeavesNothingAndDeletingABaseElementIsADelete() {
        BranchService.BranchView branch = this.branches.create(TRUNK, "方案A", this.baseline(), "alice");
        Part part = SysarchFactory.eINSTANCE.createPart();
        part.setId("PT-NEW");
        part.setName("新部件");
        part.setLayer(Layer.LOGICAL);
        part.setTypedBy(this.block("PD-002"));
        this.edit(() -> this.block("PD-001").getOwnedParts().add(part));
        assertEquals(List.of("PT-NEW:ADD"), this.ops(branch), "adding a part is one ADD, its block is unchanged");

        this.edit(() -> EcoreUtil.delete(part, true));
        assertEquals(List.of(), this.ops(branch));

        this.edit(() -> EcoreUtil.delete(this.requirement("RQ-005"), true));
        assertEquals(List.of("RQ-005:DELETE"), this.ops(branch));
        assertEquals(null, this.branches.changes(branch.id()).get(0).state());
    }

    /** Deleting a part takes its ports along: each is a DELETE, and connections that lost an end are MODIFY. */
    @Test
    void deletingAPartDeletesItsPortsAndChangesTheirConnections() {
        BranchService.BranchView branch = this.branches.create(TRUNK, "方案A", this.baseline(), "alice");
        this.edit(() -> EcoreUtil.delete(this.part("PT-001"), true));

        List<String> ops = this.ops(branch);
        assertTrue(ops.contains("PT-001:DELETE"), String.valueOf(ops));
        assertTrue(ops.stream().filter(o -> o.endsWith(":DELETE")).count() >= 3, "the part and its two ports: " + ops);
        assertTrue(ops.stream().anyMatch(o -> o.startsWith("CO-") && o.endsWith(":MODIFY")), "a connection lost an end: " + ops);
    }

    @Test
    void movingAPartIsAModifyOfThePart() {
        BranchService.BranchView branch = this.branches.create(TRUNK, "方案A", this.baseline(), "alice");
        this.edit(() -> this.block("PD-005").getOwnedParts().add(this.part("PT-003")));
        List<BranchService.Change> changes = this.branches.changes(branch.id());
        assertEquals(List.of("PT-003"), changes.stream().map(BranchService.Change::elementId).toList());
        assertEquals(List.of("container"), changes.get(0).fields());
    }

    /** Edits outside a workspace record nothing. */
    @Test
    void theTrunkRecordsNothing() {
        BranchService.BranchView branch = this.branches.create(TRUNK, "方案A", this.baseline(), "alice");
        ChangeRecorder recorder = new ChangeRecorder(this.trunk);
        ((PartDefinition) this.trunk.getContents().get(0)).setName("主干改名");
        ChangeDescription change = recorder.endRecording();
        recorder.dispose();
        this.branches.recordEdit(TRUNK, null, change);
        assertEquals(List.of(), this.branches.changes(branch.id()));
    }

    // =========================================================== forking rules

    @Test
    void aBranchNeedsAStructuredBaselineOfItsTrunkAndAFreeName() {
        String baseline = this.baseline();
        assertThrows(BranchService.BranchRequestException.class, () -> this.branches.create(TRUNK, "x", null, "alice"), "only from a baseline");
        assertThrows(BranchService.BranchRequestException.class, () -> this.branches.create(TRUNK, " ", baseline, "alice"));
        assertThrows(BranchService.BranchRequestException.class, () -> this.branches.create("trunk-2", "x", baseline, "alice"), "another project's");

        String old = this.oldBaseline();
        BranchService.BranchRequestException noStructure = assertThrows(BranchService.BranchRequestException.class,
                () -> this.branches.create(TRUNK, "x", old, "alice"));
        assertTrue(noStructure.conflict());
        assertTrue(noStructure.getMessage().contains("建于 P2 之前"));
        verify(this.projects, never()).createProject(any());

        this.branches.create(TRUNK, "方案A", baseline, "alice");
        assertTrue(assertThrows(BranchService.BranchRequestException.class, () -> this.branches.create(TRUNK, "方案A", baseline, "bob")).conflict());
        assertThrows(BranchService.BranchRequestException.class, () -> this.branches.create(WORKSPACE, "嵌套", baseline, "bob"),
                "a workspace is not a trunk");
    }

    @Test
    void aFailedForkDeletesItsWorkspace() {
        ProjectModels failing = mock(ProjectModels.class);
        when(failing.siriusProjectId(anyString())).thenAnswer(call -> call.getArgument(0));
        when(failing.projectName(anyString())).thenReturn("域控演示");
        when(failing.addDocument(anyString(), anyString(), any())).thenThrow(new IllegalStateException("disk full"));
        BranchService service = new BranchService(this.jdbc, this.workspaces, this.baselines, failing, this.projects, this.locks, this.sessions,
                new AuditLog(this.jdbc), new ObjectMapper().findAndRegisterModules(), dataSourceOf());
        String baseline = this.baseline();
        assertThrows(IllegalStateException.class, () -> service.create(TRUNK, "方案A", baseline, "alice"));
        verify(this.projects).deleteProject(any(DeleteProjectInput.class));
        assertEquals(0, this.jdbc.sql("SELECT COUNT(*) FROM branch").query(Integer.class).single());
    }

    // =========================================================== one writer, leaving

    @Test
    void aBranchHasOneWriterAndLeavingFreesItsPlaceAndLocks() {
        BranchService.BranchView branch = this.branches.create(TRUNK, "方案A", this.baseline(), "alice");
        assertEquals(1, this.sessions.limit(WORKSPACE));
        assertEquals(30, this.sessions.limit(TRUNK));

        this.sessions.touch(WORKSPACE, "alice", null);
        SessionService.FullException busy = assertThrows(SessionService.FullException.class, () -> this.sessions.touch(WORKSPACE, "bob", null));
        assertEquals("该分支正由 alice 编辑，同一分支同时只允许一人写，请稍后再试", busy.getMessage());

        this.locks.acquire(WORKSPACE, List.of("PD-002", "PD-003"), "alice", null);
        this.locks.acquire(TRUNK, List.of("PD-002"), "alice", null);
        assertEquals(2, this.branches.leave(branch.id(), "alice"));
        assertEquals(List.of(), this.locks.list(WORKSPACE));
        assertEquals(1, this.locks.list(TRUNK).size(), "only the branch's locks");
        assertNotNull(this.sessions.touch(WORKSPACE, "bob", null), "the place is free");
    }

    @Test
    void abandoningDeletesTheWorkspaceAndKeepsTheChangeset() {
        BranchService.BranchView branch = this.branches.create(TRUNK, "方案A", this.baseline(), "alice");
        this.edit(() -> this.block("PD-002").setName("v2"));
        BranchService.BranchView abandoned = this.branches.abandon(branch.id(), "admin");

        assertEquals(BranchService.ABANDONED, abandoned.status());
        assertEquals(1, abandoned.changes());
        assertEquals(30, this.sessions.limit(WORKSPACE), "no longer a workspace");
        verify(this.projects).deleteProject(any(DeleteProjectInput.class));
        assertEquals(1, this.jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE action = 'BRANCH_ABANDON'").query(Integer.class).single());
    }

    // =========================================================== helpers

    private JdbcDataSource lastDataSource;

    private JdbcDataSource dataSourceOf() {
        return this.lastDataSource;
    }

    private String baseline() {
        String id = UUID.randomUUID().toString();
        this.baselines.insert(new StoredBaseline(id, TRUNK, "评审前", null, 1, 0, "alice", OffsetDateTime.now(ZoneOffset.UTC)),
                ModelSnapshot.capture(List.of(this.trunk)).values());
        return id;
    }

    /** A baseline as P1 stored it: no structure. */
    private String oldBaseline() {
        String id = UUID.randomUUID().toString();
        Map<String, ElementState> old = new TreeMap<>();
        ModelSnapshot.capture(List.of(this.trunk)).forEach((elementId, state) -> {
            Map<String, Object> attributes = new HashMap<>(state.attributes());
            attributes.remove(ModelSnapshot.CONTAINER);
            attributes.remove(ModelSnapshot.FEATURE);
            old.put(elementId, new ElementState(state.id(), state.type(), state.name(), state.layer(), attributes));
        });
        this.baselines.insert(new StoredBaseline(id, TRUNK, "P1 基线", null, 1, 0, "alice", OffsetDateTime.now(ZoneOffset.UTC)), old.values());
        return id;
    }

    /** One edit in the workspace, recorded as the Guard does after it went through. */
    private void edit(Runnable edit) {
        ChangeRecorder recorder = new ChangeRecorder(this.workspaceSet);
        edit.run();
        ChangeDescription change = recorder.endRecording();
        recorder.dispose();
        this.branches.recordEdit(WORKSPACE, this.workspaceSet, change);
    }

    private List<String> ops(BranchService.BranchView branch) {
        return this.branches.changes(branch.id()).stream().map(c -> c.elementId() + ":" + c.op()).toList();
    }

    private PartDefinition block(String id) {
        return (PartDefinition) this.find(id);
    }

    private Part part(String id) {
        return (Part) this.find(id);
    }

    private Requirement requirement(String id) {
        return (Requirement) this.find(id);
    }

    private EObject find(String id) {
        for (var it = this.workspace.getAllContents(); it.hasNext();) {
            EObject e = it.next();
            if (id.equals(EcoreUtil.getID(e))) {
                return e;
            }
        }
        throw new IllegalArgumentException(id);
    }
}

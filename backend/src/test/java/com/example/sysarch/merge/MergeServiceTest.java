package com.example.sysarch.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.baseline.BaselineRepository;
import com.example.sysarch.baseline.BaselineService;
import com.example.sysarch.baseline.ModelSnapshot;
import com.example.sysarch.baseline.ModelSnapshot.ElementState;
import com.example.sysarch.branch.BranchService;
import com.example.sysarch.branch.BranchWorkspaces;
import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.identity.Editors.Editor;
import com.example.sysarch.lock.LockService;
import com.example.sysarch.merge.MergeEngine.Resolution;
import com.example.sysarch.merge.MergeEngine.Side;
import com.example.sysarch.merge.MergeEngine.Strategy;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.session.SessionService;
import com.example.sysarch.validation.ConstraintValidator;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.web.application.project.dto.CreateProjectSuccessPayload;
import org.eclipse.sirius.web.application.project.dto.ProjectDTO;
import org.eclipse.sirius.web.application.project.services.api.IProjectApplicationService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import liquibase.integration.spring.SpringLiquibase;

/**
 * P2-AC-3 "分支改 5 元素后合并：预演列出 5 项，应用后主干更新" and P2-AC-4 "构造 1 个字段冲突：冲突列出 elementId 与字段名，
 * 不静默覆盖", end to end over the merge service: preview stored, apply only of a fresh preview with every conflict
 * chosen, pre-merge baseline, all or nothing, branch closed.
 */
class MergeServiceTest {

    private static final String TRUNK = "trunk-1";

    private static final String WORKSPACE = "ws-1";

    private static final ConstraintValidator VALIDATOR = new ConstraintValidator();

    @AfterAll
    static void disposeOcl() {
        VALIDATOR.close();
    }

    private JdbcClient jdbc;

    private BranchService branches;

    private MergeService merges;

    private final Resource trunk = resource("memory:/trunk.json");

    private Resource workspace;

    @BeforeEach
    void setUp() throws Exception {
        this.trunk.getContents().addAll(DomainControllerDemo.build());
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/sysarch/changelog.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        this.jdbc = JdbcClient.create(dataSource);
        this.jdbc.sql("CREATE TABLE project (id VARCHAR(64), name VARCHAR(255))").update();
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        BaselineRepository baselines = new BaselineRepository(this.jdbc, json, dataSource);
        LockService locks = new LockService(this.jdbc, transaction, new AuditLog(this.jdbc), Clock.systemUTC());
        BranchWorkspaces workspaces = new BranchWorkspaces(this.jdbc);
        SessionService sessions = new SessionService(this.jdbc, 30, Duration.ofMinutes(30), Clock.systemUTC(), List.of(workspaces));

        ProjectModels models = mock(ProjectModels.class);
        when(models.siriusProjectId(anyString())).thenAnswer(call -> call.getArgument(0));
        when(models.projectName(anyString())).thenReturn("域控演示");
        when(models.addDocument(anyString(), anyString(), any())).thenAnswer(call -> {
            Supplier<List<EObject>> contents = call.getArgument(2);
            this.workspace = resource("memory:/workspace.json");
            this.workspace.getContents().addAll(contents.get());
            return "doc-1";
        });
        when(models.read(anyString(), any())).thenAnswer(call -> {
            Function<List<Resource>, ?> task = call.getArgument(1);
            return task.apply(List.of(this.model(call.getArgument(0))));
        });
        when(models.write(eq(TRUNK), any(Editor.class), any())).thenAnswer(call -> {
            Function<List<Resource>, ProjectModels.Outcome<?>> task = call.getArgument(2);
            return task.apply(List.of(this.trunk)).value();
        });
        when(models.write(eq(TRUNK), isNull(), any())).thenAnswer(call -> {
            Function<List<Resource>, ProjectModels.Outcome<?>> task = call.getArgument(2);
            return task.apply(List.of(this.trunk)).value();
        });
        IProjectApplicationService projects = mock(IProjectApplicationService.class);
        when(projects.createProject(any())).thenReturn(new CreateProjectSuccessPayload(UUID.randomUUID(), new ProjectDTO(WORKSPACE, "ws", List.of())));
        AuditLog audit = new AuditLog(this.jdbc);
        this.branches = new BranchService(this.jdbc, workspaces, baselines, models, projects, locks, sessions, audit, json, dataSource);
        BaselineService baselineService = new BaselineService(models, baselines, transaction);
        this.merges = new MergeService(this.jdbc, this.branches, baselines, baselineService, models, VALIDATOR, audit, json);
    }

    /** P2-AC-3. */
    @Test
    void fiveChangesInTheBranchArePreviewedAsFiveAndAppliedToTheTrunk() {
        String branch = this.fork();
        this.block(this.workspace, "PD-002").setName("融合 v2");
        this.block(this.workspace, "PD-003").setName("规划 v2");
        this.block(this.workspace, "PD-004").setDescription("新的说明");
        this.requirement(this.workspace, "RQ-004").getSatisfiedBy().add(this.block(this.workspace, "PD-005"));
        EcoreUtil.delete(this.find(this.workspace, "RQ-005"), true);
        Map<String, ElementState> trunkBefore = ModelSnapshot.capture(List.of(this.trunk));

        MergeService.View preview = this.merges.preview(branch, Strategy.POLARION_WINS, List.of(), "alice");
        assertEquals(5, preview.merged());
        assertEquals(List.of("PD-002:MODIFY", "PD-003:MODIFY", "PD-004:MODIFY", "RQ-004:MODIFY", "RQ-005:DELETE"),
                preview.operations().stream().map(o -> o.elementId() + ":" + o.op()).toList());
        assertTrue(preview.applicable());
        assertEquals(trunkBefore, ModelSnapshot.capture(List.of(this.trunk)), "a preview writes nothing");

        MergeService.View applied = this.merges.apply(branch, preview.runId(), Strategy.POLARION_WINS, List.of(), "alice");
        assertEquals("APPLIED", applied.status());
        assertEquals(ModelSnapshot.capture(List.of(this.workspace)), ModelSnapshot.capture(List.of(this.trunk)), "the trunk now is the branch");
        assertEquals("融合 v2", this.block(this.trunk, "PD-002").getName());
        assertEquals(BranchService.MERGED, this.branches.get(branch).status());
        assertNotNull(applied.preMergeBaseline());
        assertEquals(1, this.count("SELECT COUNT(*) FROM baseline WHERE id = '" + applied.preMergeBaseline() + "'"));
        assertEquals(1, this.count("SELECT COUNT(*) FROM audit_log WHERE action = 'MERGE'"));
        assertThrows(MergeService.MergeRefusedException.class, () -> this.merges.preview(branch, Strategy.POLARION_WINS, List.of(), "alice"),
                "a merged branch is closed");
    }

    /** P2-AC-4: listed with element and field; no APPLY until chosen; the choice is applied. */
    @Test
    void aFieldConflictBlocksUntilChosen() {
        String branch = this.fork();
        this.block(this.workspace, "PD-002").setName("分支的名字");
        this.block(this.workspace, "PD-003").setName("规划 v2");
        this.block(this.trunk, "PD-002").setName("主干的名字");

        MergeService.View preview = this.merges.preview(branch, Strategy.POLARION_WINS, List.of(), "alice");
        assertEquals(1, preview.conflicts().size());
        assertEquals("PD-002", preview.conflicts().get(0).elementId());
        assertEquals("name", preview.conflicts().get(0).field());
        MergeService.MergeRefusedException refused = assertThrows(MergeService.MergeRefusedException.class,
                () -> this.merges.apply(branch, preview.runId(), Strategy.POLARION_WINS, List.of(), "alice"));
        assertTrue(refused.getMessage().contains("1 项冲突"));
        assertEquals("主干的名字", this.block(this.trunk, "PD-002").getName(), "nothing written");
        assertEquals("决策规划单元", this.block(this.trunk, "PD-003").getName(), "not even the clean change");

        MergeService.View applied = this.merges.apply(branch, preview.runId(), Strategy.POLARION_WINS, List.of(new Resolution("PD-002", "name", Side.OURS)),
                "alice");
        assertEquals("APPLIED", applied.status());
        assertEquals("主干的名字", this.block(this.trunk, "PD-002").getName(), "the chosen side");
        assertEquals("规划 v2", this.block(this.trunk, "PD-003").getName());
    }

    @Test
    void aPreviewIsStaleOnceTheTrunkChanged() {
        String branch = this.fork();
        this.block(this.workspace, "PD-002").setName("融合 v2");
        MergeService.View preview = this.merges.preview(branch, Strategy.POLARION_WINS, List.of(), "alice");
        this.block(this.trunk, "PD-006").setDescription("有人改了主干");

        MergeService.MergeRefusedException stale = assertThrows(MergeService.MergeRefusedException.class,
                () -> this.merges.apply(branch, preview.runId(), Strategy.POLARION_WINS, List.of(), "alice"));
        assertTrue(stale.getMessage().contains("重新预演"));
        assertEquals("STALE", stale.view().status());
        assertThrows(MergeService.MergeRefusedException.class, () -> this.merges.apply(branch, null, Strategy.POLARION_WINS, List.of(), "alice"),
                "APPLY only after a PREVIEW");
    }

    /** All or nothing: a result that breaks a vetoed constraint leaves the trunk as it was, and the branch open. */
    @Test
    void aResultBreakingAConstraintIsUndoneWhole() {
        String branch = this.fork();
        this.block(this.workspace, "PD-002").setName("融合 v2");
        this.block(this.workspace, "PD-005").setLayer(Layer.EPBS); // AL-001 LOGICAL → EPBS skips PHYSICAL
        Map<String, ElementState> trunkBefore = ModelSnapshot.capture(List.of(this.trunk));

        MergeService.View preview = this.merges.preview(branch, Strategy.POLARION_WINS, List.of(), "alice");
        MergeService.MergeRefusedException rejected = assertThrows(MergeService.MergeRefusedException.class,
                () -> this.merges.apply(branch, preview.runId(), Strategy.POLARION_WINS, List.of(), "alice"));
        assertTrue(rejected.getMessage().startsWith("合并结果违反约束"), rejected.getMessage());
        assertEquals(trunkBefore, ModelSnapshot.capture(List.of(this.trunk)));
        assertEquals(BranchService.OPEN, this.branches.get(branch).status());
        assertEquals("REJECTED", this.jdbc.sql("SELECT status FROM merge_run WHERE id = :id").param("id", preview.runId()).query(String.class).single());
    }

    // =========================================================== helpers

    private String fork() {
        String baseline = UUID.randomUUID().toString();
        this.jdbc.sql("INSERT INTO baseline (id, project_id, label, model_rev, element_count, created_by) VALUES (:id, :p, '评审前', 1, 0, 'alice')")
                .param("id", baseline).param("p", TRUNK).update();
        ModelSnapshot.capture(List.of(this.trunk)).values().forEach(e -> this.jdbc.sql(
                "INSERT INTO baseline_element (baseline_id, element_id, element_type, name, layer, attributes) VALUES (:b, :id, :t, :n, :l, :a)")
                .param("b", baseline).param("id", e.id()).param("t", e.type()).param("n", e.name()).param("l", e.layer())
                .param("a", write(e.attributes())).update());
        return this.branches.create(TRUNK, "方案A", baseline, "alice").id();
    }

    private Resource model(String project) {
        return WORKSPACE.equals(project) ? this.workspace : this.trunk;
    }

    private int count(String sql) {
        return this.jdbc.sql(sql).query(Integer.class).single();
    }

    private PartDefinition block(Resource model, String id) {
        return (PartDefinition) this.find(model, id);
    }

    private Requirement requirement(Resource model, String id) {
        return (Requirement) this.find(model, id);
    }

    private EObject find(Resource model, String id) {
        for (var it = model.getAllContents(); it.hasNext();) {
            EObject e = it.next();
            if (id.equals(EcoreUtil.getID(e))) {
                return e;
            }
        }
        throw new IllegalArgumentException(id);
    }

    private static Resource resource(String uri) {
        Resource resource = new ResourceImpl(URI.createURI(uri));
        new ResourceSetImpl().getResources().add(resource);
        return resource;
    }

    private static String write(Map<String, Object> attributes) {
        try {
            return new ObjectMapper().writeValueAsString(new java.util.TreeMap<>(attributes));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}

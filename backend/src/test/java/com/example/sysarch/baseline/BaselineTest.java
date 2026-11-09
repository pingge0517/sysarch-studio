package com.example.sysarch.baseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.sysarch.baseline.BaselineRepository.StoredBaseline;
import com.example.sysarch.baseline.ModelSnapshot.ElementState;
import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.DiagramLayout;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.sync.api.model.ModelDiff;
import com.example.sysarch.sync.api.model.SyncResultConflictsInner;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import liquibase.integration.spring.SpringLiquibase;

/**
 * P1 T7: baselines and diffs. Acceptance: "改 3 个元素（1 改名、1 删、1 加）后 diff 精确命中 3 项，无多余项" and
 * P1-AC-4 "改一个元素名后打基线，diff 只报 1 项 modified". Stored on H2 with the application's change sets.
 */
class BaselineTest {

    private Resource model;

    private BaselineRepository repository;

    private BaselineService service;

    @BeforeEach
    void setUp() throws Exception {
        this.model = new ResourceImpl(URI.createURI("memory:/demo.xmi"));
        this.model.getContents().addAll(DomainControllerDemo.build());

        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/sysarch/changelog.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        this.repository = new BaselineRepository(JdbcClient.create(dataSource), new ObjectMapper().findAndRegisterModules(), dataSource);

        ProjectModels models = mock(ProjectModels.class);
        when(models.siriusProjectId(anyString())).thenReturn("sirius-1");
        when(models.read(anyString(), any())).thenAnswer(call -> {
            Function<List<Resource>, ?> task = call.getArgument(1);
            return task.apply(List.of(this.model));
        });
        this.service = new BaselineService(models, this.repository, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    // =========================================================== acceptance

    /** T7: rename one, delete one, add one → exactly those three, nothing else. */
    @Test
    void renameDeleteAndAddGiveExactlyThreeEntries() {
        StoredBaseline baseline = this.service.create("sysarch", "评审前", null, "alice");
        this.element("PD-002").setName("感知融合单元 v2");
        EcoreUtil.delete(this.element("RQ-004"), true);
        PartDefinition gateway = SysarchFactory.eINSTANCE.createPartDefinition();
        gateway.setId("PD-100");
        gateway.setName("中央网关");
        gateway.setLayer(Layer.PHYSICAL);
        this.model.getContents().add(gateway);

        ModelDiff diff = this.service.diff(baseline.id(), null, null);

        assertEquals(List.of("PD-100"), diff.getAdded());
        assertEquals(List.of("RQ-004"), diff.getRemoved());
        assertEquals(List.of(new SyncResultConflictsInner().elementId("PD-002").fields(List.of("name"))), diff.getModified());
    }

    /** P1-AC-4: one renamed element is one modified entry. */
    @Test
    void oneRenameIsOneModifiedEntry() {
        StoredBaseline baseline = this.service.create("sysarch", "before", null, "alice");
        this.element("PT-001").setName("感知融合（改名）");

        ModelDiff diff = this.service.diff(baseline.id(), "CURRENT", null);

        assertEquals(List.of(), diff.getAdded());
        assertEquals(List.of(), diff.getRemoved());
        assertEquals(1, diff.getModified().size());
        assertEquals("PT-001", diff.getModified().get(0).getElementId());
        assertEquals(List.of("name"), diff.getModified().get(0).getFields());
    }

    @Test
    void anUnchangedModelHasAnEmptyDiff() {
        StoredBaseline baseline = this.service.create("sysarch", "now", null, "alice");

        ModelDiff diff = this.service.diff(baseline.id(), null, null);

        assertTrue(diff.getAdded().isEmpty() && diff.getRemoved().isEmpty() && diff.getModified().isEmpty(), String.valueOf(diff));
    }

    // =========================================================== what is compared

    /** Adding a part is one "added": the owner's containment list is not an attribute. */
    @Test
    void containmentIsNotAnAttributeOfTheOwner() {
        Map<String, ElementState> before = ModelSnapshot.capture(List.of(this.model));
        var part = SysarchFactory.eINSTANCE.createPart();
        part.setId("PT-100");
        part.setName("新部件");
        part.setTypedBy((PartDefinition) this.element("PD-002"));
        ((PartDefinition) this.element("PD-001")).getOwnedParts().add(part);

        ModelDiff diff = ModelDiffs.diff(before, ModelSnapshot.capture(List.of(this.model)));

        assertEquals(List.of("PT-100"), diff.getAdded());
        assertEquals(List.of(), diff.getModified());
    }

    @Test
    void attributesHoldOtherFieldsAndReferencesAsIds() {
        Map<String, ElementState> states = ModelSnapshot.capture(List.of(this.model));

        ElementState part = states.get("PT-001");
        assertEquals("Part", part.type());
        assertEquals("LOGICAL", part.layer());
        assertEquals("PD-002", part.attributes().get("typedBy"));
        assertFalse(part.attributes().containsKey("owner"), "a container reference is not compared");
        assertFalse(part.attributes().containsKey("ports"), "nor a containment");
        assertEquals("PO-001", states.get("CO-001").attributes().get("source"));
        assertEquals("IF-001", states.get("CO-001").attributes().get("interfaceType"));
        assertEquals(List.of("PD-002"), states.get("RQ-001").attributes().get("satisfiedBy"));
        assertEquals(List.of("system"), states.get("PD-001").attributes().get("stereotypes"));
    }

    @Test
    void layoutRecordsAreNotPartOfTheModelState() {
        DiagramLayout layout = SysarchFactory.eINSTANCE.createDiagramLayout();
        layout.setId("DL-1");
        layout.setName("layout");
        this.model.getContents().add(layout);

        assertFalse(ModelSnapshot.capture(List.of(this.model)).containsKey("DL-1"));
    }

    @Test
    void aChangedReferenceOrLayerIsReportedByName() {
        Map<String, ElementState> before = ModelSnapshot.capture(List.of(this.model));
        this.element("PD-003").setLayer(Layer.PHYSICAL);
        ((com.example.sysarch.model.Requirement) this.element("RQ-002")).getSatisfiedBy().clear();

        ModelDiff diff = ModelDiffs.diff(before, ModelSnapshot.capture(List.of(this.model)));

        assertEquals(List.of(new SyncResultConflictsInner().elementId("PD-003").fields(List.of("layer")),
                new SyncResultConflictsInner().elementId("RQ-002").fields(List.of("satisfiedBy"))), diff.getModified());
    }

    // =========================================================== storage and listing

    @Test
    void aBaselineIsStoredWithItsElementsAndVersion() {
        StoredBaseline baseline = this.service.create("sysarch", "评审前", "desc", "alice");

        StoredBaseline stored = this.repository.find(baseline.id()).orElseThrow();
        assertEquals("sirius-1", stored.projectId(), "the resolved Sirius project id");
        assertEquals(ModelSnapshot.capture(List.of(this.model)).size(), stored.elementCount());
        assertEquals("alice", stored.createdBy());
        assertEquals(ModelSnapshot.capture(List.of(this.model)), this.repository.elements(baseline.id()), "a stored state reads back equal");
    }

    @Test
    void twoBaselinesCanBeCompared() {
        StoredBaseline first = this.service.create("sysarch", "v1", null, "alice");
        this.element("PD-005").setName("域控 SoC（新）");
        StoredBaseline second = this.service.create("sysarch", "v2", null, "alice");
        this.element("PD-005").setName("域控 SoC（再改）"); // after v2: not in a v1 → v2 diff

        ModelDiff diff = this.service.diff(first.id(), "BASELINE", second.id());

        assertEquals(List.of("PD-005"), diff.getModified().stream().map(SyncResultConflictsInner::getElementId).toList());
        assertEquals(List.of(first.id(), second.id()), this.service.list("sysarch").stream().map(StoredBaseline::id).sorted(
                java.util.Comparator.comparing(id -> id.equals(first.id()) ? 0 : 1)).toList());
    }

    @Test
    void badRequestsAreRefused() {
        assertThrows(BaselineService.InvalidRequestException.class, () -> this.service.create("sysarch", " ", null, "alice"));
        StoredBaseline baseline = this.service.create("sysarch", "v1", null, "alice");
        assertThrows(BaselineService.InvalidRequestException.class, () -> this.service.diff(baseline.id(), "BASELINE", null));
        assertThrows(BaselineService.InvalidRequestException.class, () -> this.service.diff(baseline.id(), "YESTERDAY", null));
        assertThrows(BaselineService.UnknownBaselineException.class, () -> this.service.diff("nope", null, null));
    }

    private ArchElement element(String id) {
        var all = this.model.getAllContents();
        while (all.hasNext()) {
            if (all.next() instanceof ArchElement element && id.equals(element.getId())) {
                return element;
            }
        }
        throw new AssertionError("no element " + id);
    }
}

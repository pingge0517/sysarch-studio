package com.example.sysarch.sysmlimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.export.SysmlV2Exporter;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.validation.ConstraintValidator;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.sirius.web.application.project.dto.CreateProjectSuccessPayload;
import org.eclipse.sirius.web.application.project.dto.DeleteProjectInput;
import org.eclipse.sirius.web.application.project.dto.ProjectDTO;
import org.eclipse.sirius.web.application.project.services.api.IProjectApplicationService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;

import liquibase.integration.spring.SpringLiquibase;

/**
 * P2 T6 steps 1–3 over the import service: the job; nothing created when the text has unsupported syntax; a new
 * project with every element, written in one go; the consistency check attached; a failure deletes the project.
 */
class ImportServiceTest {

    private static final ConstraintValidator VALIDATOR = new ConstraintValidator();

    @AfterAll
    static void disposeOcl() {
        VALIDATOR.close();
    }

    private JdbcClient jdbc;

    private ProjectModels models;

    private IProjectApplicationService projects;

    private ImportService imports;

    private Resource imported;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/sysarch/changelog.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        this.jdbc = JdbcClient.create(dataSource);
        this.jdbc.sql("CREATE TABLE project (id VARCHAR(64), name VARCHAR(255))").update();
        this.jdbc.sql("INSERT INTO project VALUES ('other', '域控演示')").update();
        this.models = mock(ProjectModels.class);
        when(this.models.addDocument(anyString(), anyString(), any())).thenAnswer(call -> {
            Supplier<List<EObject>> contents = call.getArgument(2);
            this.imported = new ResourceImpl(URI.createURI("memory:/imported.json"));
            this.imported.getContents().addAll(contents.get());
            return "doc";
        });
        when(this.models.read(anyString(), any())).thenAnswer(call -> {
            Function<List<Resource>, ?> task = call.getArgument(1);
            return task.apply(List.of(this.imported));
        });
        this.projects = mock(IProjectApplicationService.class);
        when(this.projects.createProject(any())).thenReturn(new CreateProjectSuccessPayload(UUID.randomUUID(), new ProjectDTO("new-1", "x", List.of())));
        this.imports = new ImportService(this.jdbc, this.models, this.projects, VALIDATOR, new AuditLog(this.jdbc), new ObjectMapper().findAndRegisterModules(),
                Runnable::run, Clock.systemUTC());
    }

    @Test
    void theDemoTextIsImportedIntoANewProjectWithItsReport() {
        Resource demo = new ResourceImpl(URI.createURI("memory:/demo.json"));
        demo.getContents().addAll(DomainControllerDemo.build());
        String text = SysmlV2Exporter.export("域控演示", List.of(demo), SysmlV2Exporter.Scope.PROJECT, null, null);

        ImportService.Job job = this.imports.submit(text, null, null, "demo.sysml", "alice");

        assertEquals("DONE", job.status(), job.problems() + " " + job.error());
        assertEquals("new-1", job.projectId());
        assertEquals(28, job.report().get("total"));
        assertTrue(job.report().get("projectName").toString().startsWith("域控演示（导入 "), "a new name: 域控演示 is taken");
        @SuppressWarnings("unchecked")
        Map<String, Object> consistency = (Map<String, Object>) job.report().get("consistency");
        assertEquals(List.of(), consistency.get("constraintViolations"));
        assertEquals(List.of("RQ-004 算力余量", "RQ-005 功能安全"), consistency.get("R6"), "the demo's two unsatisfied requirements");
        assertEquals(1, this.jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE action = 'IMPORT'").query(Integer.class).single());
    }

    /** "不支持语法必须整批失败并给出 file:line 清单，禁止部分导入". */
    @Test
    void unsupportedSyntaxCreatesNothing() {
        String text = "package p {\n  part def 'A' {\n    attribute mass : Real;\n  }\n}\n";
        ImportService.Job job = this.imports.submit(text, "试试", null, "p.sysml", "alice");

        assertEquals("FAILED", job.status());
        assertEquals(List.of("第 3 行：不支持的语法：属性/参数（attribute）"), job.problems());
        assertNull(job.projectId());
        verify(this.projects, never()).createProject(any());
    }

    @Test
    void aFailedWriteDeletesTheNewProject() {
        org.mockito.Mockito.doThrow(new IllegalStateException("disk full")).when(this.models).addDocument(anyString(), anyString(), any());
        ImportService.Job job = this.imports.submit("package p { part def 'A'; }", null, "PHYSICAL", null, "alice");
        assertEquals("FAILED", job.status());
        assertTrue(job.error().contains("disk full"));
        verify(this.projects).deleteProject(any(DeleteProjectInput.class));
    }

    @Test
    void badRequestsAreRefused() {
        assertEquals(400, assertThrows(ImportService.ImportRequestException.class, () -> this.imports.submit(" ", null, null, null, "a")).status());
        assertEquals(400, assertThrows(ImportService.ImportRequestException.class, () -> this.imports.submit("package p {}", null, "MOON", null, "a")).status());
        ImportService.Job empty = this.imports.submit("package p { }", null, null, null, "a");
        assertEquals("FAILED", empty.status());
        assertTrue(empty.problems().get(0).contains("没有可导入的元素"));
    }
}

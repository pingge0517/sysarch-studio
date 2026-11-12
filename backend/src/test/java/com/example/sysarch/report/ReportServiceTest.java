package com.example.sysarch.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.baseline.BaselineRepository;
import com.example.sysarch.baseline.BaselineRepository.StoredBaseline;
import com.example.sysarch.baseline.ModelSnapshot;
import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.project.ProjectModels;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;

import liquibase.integration.spring.SpringLiquibase;

/**
 * P2 T5 steps 3–4 (spec §4.4): asynchronous jobs, the 24-hour cache per (project, baseline or model content,
 * template), failures that say what is missing, files kept 90 days, interrupted jobs failed at start.
 */
class ReportServiceTest {

    private static final String PROJECT = "p-1";

    @TempDir
    Path dir;

    private final MovableClock clock = new MovableClock();

    private JdbcClient jdbc;

    private BaselineRepository baselines;

    private ReportService reports;

    private final Resource model = new ResourceImpl(URI.createURI("memory:/demo.json"));

    @BeforeEach
    void setUp() throws Exception {
        this.model.getContents().addAll(DomainControllerDemo.build());
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/sysarch/changelog.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        this.jdbc = JdbcClient.create(dataSource);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        this.baselines = new BaselineRepository(this.jdbc, json, dataSource);
        ProjectModels models = mock(ProjectModels.class);
        when(models.siriusProjectId(anyString())).thenAnswer(call -> call.getArgument(0));
        when(models.projectName(anyString())).thenReturn("域控演示");
        when(models.read(anyString(), any())).thenAnswer(call -> {
            Function<List<Resource>, ?> task = call.getArgument(1);
            return task.apply(List.of(this.model));
        });
        ReportRenderer renderer = new ReportRenderer(ReportConfiguration.defaultFonts());
        this.reports = new ReportService(this.jdbc, models, this.baselines, renderer, new AuditLog(this.jdbc), json, this.dir, Runnable::run, this.clock);
    }

    @Test
    void aBaselineReportIsGeneratedWithItsFilesAndAudited() {
        String baseline = this.baseline("评审前");
        ReportService.Job job = this.reports.submit(PROJECT, "srs", baseline, "alice");

        assertEquals("DONE", job.status());
        assertFalse(job.cached());
        assertEquals("基线「评审前」", job.sourceLabel());
        assertTrue(job.pages() >= 12, job.pages() + " pages");
        assertEquals(List.of("2 条需求无 satisfiedBy", "4 条需求未验证（无 verifiedBy）"), job.gaps());
        for (String format : ReportService.FORMATS) {
            assertTrue(Files.isRegularFile(this.reports.file(job.id(), format)), format);
        }
        assertEquals(1, this.jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE action = 'REPORT_GENERATE'").query(Integer.class).single());
    }

    /** §4.4: the same (project, baseline, template) within 24 hours reuses the result. */
    @Test
    void theSameRequestWithinADayIsServedFromTheCache() {
        String baseline = this.baseline("评审前");
        ReportService.Job first = this.reports.submit(PROJECT, "icd", baseline, "alice");
        this.clock.advance(Duration.ofHours(23));
        ReportService.Job again = this.reports.submit(PROJECT, "icd", baseline, "bob");
        assertTrue(again.cached());
        assertEquals(first.id(), again.id());

        assertNotEquals(first.id(), this.reports.submit(PROJECT, "srs", baseline, "bob").id(), "another template");
        this.clock.advance(Duration.ofHours(2));
        assertFalse(this.reports.submit(PROJECT, "icd", baseline, "bob").cached(), "after 24 hours");
    }

    /** The current model is keyed by its content: unchanged → cached, changed → a new report. */
    @Test
    void theCurrentModelIsCachedByItsContent() {
        ReportService.Job first = this.reports.submit(PROJECT, "trace-report", null, "alice");
        assertEquals("当前模型", first.sourceLabel());
        assertNull(first.baselineId());
        assertTrue(this.reports.submit(PROJECT, "trace-report", null, "bob").cached());

        ((PartDefinition) this.model.getContents().get(0)).setDescription("改了");
        assertFalse(this.reports.submit(PROJECT, "trace-report", null, "bob").cached());
    }

    /** §4.4: "生成失败必须给出具体缺什么". */
    @Test
    void aReportThatCannotBeMadeSaysWhatIsMissing() {
        ((PartDefinition) this.model.getContents().get(0)).getStereotypes().clear();
        ReportService.Job job = this.reports.submit(PROJECT, "srs", null, "alice");
        assertEquals("FAILED", job.status());
        assertEquals(1, job.missing().size());
        assertTrue(job.missing().get(0).contains("system 构造型"));
        assertThrows(ReportService.ReportRequestException.class, () -> this.reports.file(job.id(), "pdf"));
    }

    @Test
    void badRequestsAreRefused() {
        assertEquals(400, assertThrows(ReportService.ReportRequestException.class, () -> this.reports.submit(PROJECT, "nope", null, "a")).status());
        assertEquals(404, assertThrows(ReportService.ReportRequestException.class, () -> this.reports.submit(PROJECT, "srs", "missing", "a")).status());
        String other = this.baseline("别的项目", "p-2");
        assertEquals(400, assertThrows(ReportService.ReportRequestException.class, () -> this.reports.submit(PROJECT, "srs", other, "a")).status());
    }

    /** §7.3: files are kept 90 days. */
    @Test
    void filesGoAfterNinetyDays() {
        ReportService.Job job = this.reports.submit(PROJECT, "icd", null, "alice");
        Path file = this.reports.file(job.id(), "docx");
        this.clock.advance(Duration.ofDays(89));
        assertEquals(0, this.reports.expire());
        this.clock.advance(Duration.ofDays(2));
        assertEquals(1, this.reports.expire());
        assertFalse(Files.exists(file));
        assertEquals("EXPIRED", this.reports.get(job.id()).status());
    }

    /** P4 T5: over the size cap, the oldest finished reports go (never the one just written), audited once. */
    @Test
    void theReportDirectoryKeepsToItsSizeCap() throws Exception {
        String[] ids = { "old-1", "old-2", "new-3" };
        for (int i = 0; i < ids.length; i++) {
            this.jdbc.sql("INSERT INTO report_job (id, project_id, source_label, template_id, cache_key, status, requested_by, created_at) "
                    + "VALUES (:id, 'p-1', '当前模型', 'srs', :k, 'DONE', 'a', :at)").param("id", ids[i]).param("k", "k" + i)
                    .param("at", OffsetDateTime.now(ZoneOffset.UTC).minusDays(10 - i)).update();
            Path folder = Files.createDirectories(this.dir.resolve(ids[i]));
            Files.write(folder.resolve("srs.pdf"), new byte[400_000]);
        }
        this.reports.setMaxTotalBytes(1_000_000);

        assertEquals(1, this.reports.enforceSizeCap("new-3"), "1.2 MB over a 1 MB cap: the oldest goes");

        assertEquals("EXPIRED", this.reports.get("old-1").status());
        assertFalse(Files.exists(this.dir.resolve("old-1").resolve("srs.pdf")));
        assertEquals("DONE", this.reports.get("old-2").status());
        assertEquals(1, this.jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE action = 'REPORT_EXPIRE'").query(Integer.class).single());
        assertEquals("size cap 976.6 KB: removed 1 reports, 1.1 MB → 781.3 KB", this.jdbc.sql("SELECT detail FROM audit_log WHERE action = 'REPORT_EXPIRE'").query(String.class).single());
        assertEquals("1.5 KB", ReportService.human(1536));

        this.reports.setMaxTotalBytes(300_000);
        assertEquals(1, this.reports.enforceSizeCap("new-3"), "the report just written stays even when it alone is over the cap");
        assertEquals("DONE", this.reports.get("new-3").status());
        this.reports.setMaxTotalBytes(0);
        assertEquals(0, this.reports.enforceSizeCap(null), "0 = no cap");
    }

    @Test
    void jobsARestartInterruptedAreFailed() {
        this.jdbc.sql("INSERT INTO report_job (id, project_id, source_label, template_id, cache_key, status, requested_by, created_at) "
                + "VALUES ('j1', 'p-1', '当前模型', 'srs', 'k', 'RUNNING', 'a', :at)").param("at", OffsetDateTime.now(ZoneOffset.UTC)).update();
        assertEquals(1, this.reports.failInterrupted());
        assertEquals("FAILED", this.reports.get("j1").status());
    }

    private String baseline(String label) {
        return this.baseline(label, PROJECT);
    }

    private String baseline(String label, String project) {
        String id = UUID.randomUUID().toString();
        this.baselines.insert(new StoredBaseline(id, project, label, null, 1, 0, "carol", OffsetDateTime.now(ZoneOffset.UTC)),
                ModelSnapshot.capture(List.of(this.model)).values());
        return id;
    }

    private static final class MovableClock extends Clock {

        private Instant now = Instant.now();

        void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.now;
        }
    }
}

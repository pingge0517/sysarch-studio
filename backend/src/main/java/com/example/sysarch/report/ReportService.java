package com.example.sysarch.report;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.baseline.BaselineRepository;
import com.example.sysarch.baseline.ModelSnapshot;
import com.example.sysarch.baseline.ModelSnapshot.ElementState;
import com.example.sysarch.baseline.SnapshotModels;
import com.example.sysarch.project.ProjectModels;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Report jobs (P2 §4.4, T5 step 3). Submitting freezes the data at once (the baseline's states, or the current model
 * captured on its editing context, user's decision "基线优先，可选当前") and queues the rendering; the result is three
 * files in the report directory (user's decision: a local directory). The same (project, baseline or model content,
 * template) within 24 hours reuses the finished job. A model that cannot give the report fails with what is missing;
 * a model with gaps gives the report with its "待补充" section (user's decision). Files go after 90 days (§7.3).
 */
public class ReportService {

    public static final Duration CACHE = Duration.ofHours(24);

    public static final Duration RETENTION = Duration.ofDays(90);

    public static final List<String> FORMATS = List.of("html", "docx", "pdf");

    /** A report job as the API shows it. */
    public record Job(String id, String projectId, String baselineId, String sourceLabel, String templateId, String status, String requestedBy,
            OffsetDateTime createdAt, OffsetDateTime finishedAt, Integer pages, List<String> missing, List<String> gaps, String error, boolean cached) {
    }

    /** A request that cannot be served (400/404/409 at the API). */
    public static class ReportRequestException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int status;

        public ReportRequestException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return this.status;
        }
    }

    private final Logger logger = LoggerFactory.getLogger(ReportService.class);

    private final JdbcClient jdbc;

    private final ProjectModels models;

    private final BaselineRepository baselines;

    private final ReportRenderer renderer;

    private final AuditLog audit;

    private final ObjectMapper json;

    private final Path directory;

    private final Executor executor;

    private final Clock clock;

    public ReportService(JdbcClient jdbc, ProjectModels models, BaselineRepository baselines, ReportRenderer renderer, AuditLog audit, ObjectMapper json,
            Path directory, Executor executor, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.models = Objects.requireNonNull(models);
        this.baselines = Objects.requireNonNull(baselines);
        this.renderer = Objects.requireNonNull(renderer);
        this.audit = Objects.requireNonNull(audit);
        this.json = Objects.requireNonNull(json);
        this.directory = Objects.requireNonNull(directory);
        this.executor = Objects.requireNonNull(executor);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Queues a report, or answers with a finished one of the last 24 hours for the same data and template. */
    public Job submit(String project, String templateId, String baselineId, String user) {
        if (!ReportRenderer.TEMPLATES.contains(templateId)) {
            throw new ReportRequestException(400, "未知模板 " + templateId + "，可选 " + ReportRenderer.TEMPLATES);
        }
        String projectId = this.models.siriusProjectId(project);
        Map<String, ElementState> states;
        String label;
        String owner;
        String key;
        if (baselineId != null && !baselineId.isBlank()) {
            var baseline = this.baselines.find(baselineId).orElseThrow(() -> new ReportRequestException(404, "基线 " + baselineId + " 不存在"));
            if (!baseline.projectId().equals(projectId)) {
                throw new ReportRequestException(400, "基线 " + baselineId + " 不属于这个项目");
            }
            states = this.baselines.elements(baselineId);
            if (!ModelSnapshot.isStructural(states)) {
                throw new ReportRequestException(409, "基线「" + baseline.label() + "」建于 P2 之前，没有记录元素的层级关系，无法生成报告；请用当前模型或新建基线");
            }
            label = "基线「" + baseline.label() + "」";
            owner = baseline.createdBy();
            key = templateId + "|" + projectId + "|baseline:" + baselineId;
        } else {
            states = this.models.read(projectId, ModelSnapshot::capture);
            label = "当前模型";
            owner = user;
            key = templateId + "|" + projectId + "|model:" + fingerprint(states);
        }
        OffsetDateTime now = OffsetDateTime.now(this.clock).withOffsetSameInstant(ZoneOffset.UTC);
        Optional<Job> cached = this.jdbc.sql("SELECT * FROM report_job WHERE cache_key = :k AND status = 'DONE' AND created_at > :since ORDER BY created_at DESC")
                .param("k", key).param("since", now.minus(CACHE)).query(this::job).list().stream().filter(j -> this.filesExist(j.id(), j.templateId())).findFirst();
        if (cached.isPresent()) {
            Job job = cached.get();
            return new Job(job.id(), job.projectId(), job.baselineId(), job.sourceLabel(), job.templateId(), job.status(), job.requestedBy(), job.createdAt(),
                    job.finishedAt(), job.pages(), job.missing(), job.gaps(), job.error(), true);
        }
        String id = UUID.randomUUID().toString();
        this.jdbc.sql("INSERT INTO report_job (id, project_id, baseline_id, source_label, template_id, cache_key, status, requested_by, created_at) "
                + "VALUES (:id, :p, :b, :l, :t, :k, 'QUEUED', :u, :at)")
                .param("id", id).param("p", projectId).param("b", baselineId == null || baselineId.isBlank() ? null : baselineId).param("l", label)
                .param("t", templateId).param("k", key).param("u", user).param("at", now).update();
        this.audit.append(projectId, user, com.example.sysarch.audit.AuditLog.REPORT_GENERATE, null, templateId + " from " + label + " (job " + id + ")");
        String projectName = this.models.projectName(projectId);
        ReportExtractor.Source source = new ReportExtractor.Source(projectId, projectName, label, owner,
                now.atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        this.executor.execute(() -> this.run(id, templateId, states, source));
        return this.get(id);
    }

    /** Renders a queued job (on the report executor). */
    void run(String id, String templateId, Map<String, ElementState> states, ReportExtractor.Source source) {
        this.jdbc.sql("UPDATE report_job SET status = 'RUNNING' WHERE id = :id").param("id", id).update();
        try {
            Resource model = new ResourceImpl(URI.createURI("memory:/report/" + id + ".json"));
            model.getContents().addAll(SnapshotModels.build(states));
            Map<String, Object> context = ReportExtractor.extract(templateId, List.of(model), source);
            ReportRenderer.Rendered rendered = this.renderer.render(templateId, context);
            Path dir = Files.createDirectories(this.directory.resolve(id));
            Files.writeString(dir.resolve(templateId + ".html"), rendered.html(), StandardCharsets.UTF_8);
            Files.write(dir.resolve(templateId + ".docx"), rendered.docx());
            Files.write(dir.resolve(templateId + ".pdf"), rendered.pdf());
            @SuppressWarnings("unchecked")
            List<String> gaps = ((List<Map<String, Object>>) context.get("gaps")).stream().map(g -> String.valueOf(g.get("title"))).toList();
            this.jdbc.sql("UPDATE report_job SET status = 'DONE', finished_at = :at, pages = :pages, gaps = :gaps WHERE id = :id")
                    .param("at", OffsetDateTime.now(this.clock)).param("pages", rendered.pages()).param("gaps", this.write(gaps)).param("id", id).update();
        } catch (ReportExtractor.MissingException e) {
            this.fail(id, e.missing(), null);
        } catch (SnapshotModels.UnbuildableException e) {
            this.fail(id, List.of("模型无法还原：" + e.getMessage()), null);
        } catch (RuntimeException | IOException e) {
            this.logger.error("Report job {} failed", id, e);
            this.fail(id, List.of(), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public Job get(String id) {
        return this.jdbc.sql("SELECT * FROM report_job WHERE id = :id").param("id", id).query(this::job).optional()
                .orElseThrow(() -> new ReportRequestException(404, "报告任务 " + id + " 不存在"));
    }

    /** The project's report jobs, newest first. */
    public List<Job> list(String project) {
        return this.jdbc.sql("SELECT * FROM report_job WHERE project_id = :p ORDER BY created_at DESC").param("p", this.models.siriusProjectId(project))
                .query(this::job).list();
    }

    /** A finished job's file. */
    public Path file(String id, String format) {
        Job job = this.get(id);
        if (!FORMATS.contains(format)) {
            throw new ReportRequestException(400, "格式只能是 " + FORMATS);
        }
        if (!"DONE".equals(job.status())) {
            throw new ReportRequestException(409, "报告任务 " + id + " 是 " + job.status() + "，没有文件");
        }
        Path file = this.directory.resolve(id).resolve(job.templateId() + "." + format);
        if (!Files.isRegularFile(file)) {
            throw new ReportRequestException(404, "文件已不存在（超过保留期限或被清理）");
        }
        return file;
    }

    /** Jobs a restart interrupted. */
    public int failInterrupted() {
        return this.jdbc.sql("UPDATE report_job SET status = 'FAILED', error = '服务重启，任务中断，请重新提交', finished_at = :at WHERE status IN ('QUEUED', 'RUNNING')")
                .param("at", OffsetDateTime.now(this.clock)).update();
    }

    /** Every report job of a project goes with its files (the Sirius project was deleted); returns how many jobs. */
    public int dropProject(String projectId) {
        List<String> ids = this.jdbc.sql("SELECT id FROM report_job WHERE project_id = :p").param("p", projectId).query(String.class).list();
        ids.forEach(this::deleteFiles);
        this.jdbc.sql("DELETE FROM report_job WHERE project_id = :p").param("p", projectId).update();
        return ids.size();
    }

    private void deleteFiles(String id) {
        Path dir = this.directory.resolve(id);
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException e) {
                this.logger.warn("Cannot delete report files {}: {}", dir, e.getMessage());
            }
        }
    }

    /** Files older than 90 days go (§7.3); their jobs become EXPIRED. */
    public int expire() {
        OffsetDateTime limit = OffsetDateTime.now(this.clock).minus(RETENTION);
        List<String> old = this.jdbc.sql("SELECT id FROM report_job WHERE created_at < :limit AND status <> 'EXPIRED'").param("limit", limit).query(String.class)
                .list();
        for (String id : old) {
            this.deleteFiles(id);
            this.jdbc.sql("UPDATE report_job SET status = 'EXPIRED' WHERE id = :id").param("id", id).update();
        }
        return old.size();
    }

    private void fail(String id, List<String> missing, String error) {
        this.jdbc.sql("UPDATE report_job SET status = 'FAILED', finished_at = :at, missing = :m, error = :e WHERE id = :id").param("at", OffsetDateTime.now(this.clock))
                .param("m", this.write(missing)).param("e", error).param("id", id).update();
    }

    private boolean filesExist(String id, String templateId) {
        return FORMATS.stream().allMatch(f -> Files.isRegularFile(this.directory.resolve(id).resolve(templateId + "." + f)));
    }

    private Job job(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        int pageCount = rs.getInt("pages");
        Integer pages = rs.wasNull() ? null : pageCount;
        return new Job(rs.getString("id"), rs.getString("project_id"), rs.getString("baseline_id"), rs.getString("source_label"), rs.getString("template_id"),
                rs.getString("status"), rs.getString("requested_by"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class), pages, this.read(rs.getString("missing")),
                this.read(rs.getString("gaps")), rs.getString("error"), false);
    }

    private String write(List<String> values) {
        try {
            return this.json.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> read(String text) {
        try {
            return text == null ? List.of() : this.json.readValue(text, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            return List.of(text);
        }
    }

    private String fingerprint(Map<String, ElementState> states) {
        try {
            byte[] bytes = this.json.writeValueAsString(new TreeMap<>(states)).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 32);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

package com.example.sysarch.report;

import com.example.sysarch.identity.CurrentUser;
import com.example.sysarch.project.ProjectModels;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/reports} (P2 §4.4–4.5, T5). Hand-written: the spec's operations have no schemas.
 *
 * <ul>
 * <li>{@code GET /reports/templates}: the three templates.</li>
 * <li>{@code POST /reports} {@code {projectId, templateId, baselineId?}}: 202 with the job ({@code jobId}, status
 * QUEUED), or the finished job of the last 24 hours for the same data ({@code cached: true}); 400 / 404 / 409 with a
 * message for a bad template, project or baseline.</li>
 * <li>{@code GET /reports/{jobId}}: status (QUEUED, RUNNING, DONE, FAILED, EXPIRED), {@code missing} (why it failed),
 * {@code gaps} ("待补充" titles), {@code pages} (of the PDF), and the download links when DONE.</li>
 * <li>{@code GET /reports?projectId=}: the project's jobs. {@code GET /reports/{jobId}/files/{html|docx|pdf}}: the file.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    public record SubmitRequest(String projectId, String templateId, String baselineId) {
    }

    private static final Map<String, String> LABELS = Map.of("srs", "系统规格书（SRS）", "icd", "接口数据清单（ICD）", "trace-report", "追溯报告");

    private static final Map<String, MediaType> TYPES = Map.of("html", MediaType.TEXT_HTML, "docx",
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), "pdf", MediaType.APPLICATION_PDF);

    private final ReportService reports;

    private final com.example.sysarch.project.ProjectModels models;

    private final com.example.sysarch.access.Access access;

    private com.example.sysarch.config.FeatureProperties features = new com.example.sysarch.config.FeatureProperties();

    @org.springframework.beans.factory.annotation.Autowired
    void features(com.example.sysarch.config.FeatureProperties features) {
        this.features = features;
    }

    public ReportController(ReportService reports, com.example.sysarch.project.ProjectModels models, com.example.sysarch.access.Access access) {
        this.reports = Objects.requireNonNull(reports);
        this.models = Objects.requireNonNull(models);
        this.access = Objects.requireNonNull(access);
    }

    @GetMapping("/templates")
    public List<Map<String, String>> templates() {
        return ReportRenderer.TEMPLATES.stream().map(t -> Map.of("id", t, "label", LABELS.get(t))).toList();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> submit(@RequestBody SubmitRequest request, HttpServletRequest http) {
        if (request == null || request.projectId() == null || request.projectId().isBlank() || request.templateId() == null) {
            throw new ReportService.ReportRequestException(400, "projectId and templateId are required");
        }
        com.example.sysarch.config.FeatureProperties.require(this.features.isReports(), "报告生成");
        // P2 T8: every role may generate a report (it reads), but it is an audited action, so it needs a signed-in user
        String user = CurrentUser.of(http).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录：生成报告会记入审计"));
        String project;
        try {
            project = this.models.siriusProjectId(request.projectId());
        } catch (com.example.sysarch.project.ProjectModels.UnknownProjectException e) {
            throw new ReportService.ReportRequestException(404, e.getMessage());
        }
        this.access.require(http, project, com.example.sysarch.access.Permission.REPORT);
        ReportService.Job job = this.reports.submit(request.projectId(), request.templateId(), request.baselineId(), user);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(view(job));
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam("projectId") String projectId) {
        return this.reports.list(projectId).stream().map(ReportController::view).toList();
    }

    @GetMapping("/{jobId}")
    public Map<String, Object> get(@PathVariable String jobId) {
        return view(this.reports.get(jobId));
    }

    @GetMapping("/{jobId}/files/{format}")
    public ResponseEntity<Resource> file(@PathVariable String jobId, @PathVariable String format) {
        Path file = this.reports.file(jobId, format);
        ReportService.Job job = this.reports.get(jobId);
        String name = LABELS.get(job.templateId()).replaceAll("（.*）", "") + "-" + job.createdAt().toLocalDate() + "." + format;
        return ResponseEntity.ok().contentType(TYPES.get(format))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("html".equals(format) ? "inline" : "attachment")
                        .filename(name, StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file));
    }

    @ExceptionHandler(ReportService.ReportRequestException.class)
    public ResponseEntity<Map<String, String>> refused(ReportService.ReportRequestException e) {
        return ResponseEntity.status(e.status()).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(ProjectModels.UnknownProjectException.class)
    public ResponseEntity<Map<String, String>> unknownProject(ProjectModels.UnknownProjectException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    static Map<String, Object> view(ReportService.Job job) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("jobId", job.id());
        view.put("projectId", job.projectId());
        view.put("templateId", job.templateId());
        view.put("template", LABELS.get(job.templateId()));
        view.put("baselineId", job.baselineId());
        view.put("source", job.sourceLabel());
        view.put("status", job.status());
        view.put("cached", job.cached());
        view.put("requestedBy", job.requestedBy());
        view.put("createdAt", job.createdAt());
        view.put("finishedAt", job.finishedAt());
        view.put("pages", job.pages());
        view.put("missing", job.missing());
        view.put("gaps", job.gaps());
        view.put("error", job.error());
        Map<String, String> files = new LinkedHashMap<>();
        if ("DONE".equals(job.status())) {
            ReportService.FORMATS.forEach(f -> files.put(f, "/api/v1/reports/" + job.id() + "/files/" + f));
        }
        view.put("files", files);
        return view;
    }
}

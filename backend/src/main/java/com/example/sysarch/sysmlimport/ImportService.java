package com.example.sysarch.sysmlimport;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.DiagramLayout;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.validation.ValidationError;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.web.application.project.dto.CreateProjectInput;
import org.eclipse.sirius.web.application.project.dto.CreateProjectSuccessPayload;
import org.eclipse.sirius.web.application.project.dto.DeleteProjectInput;
import org.eclipse.sirius.web.application.project.services.api.IProjectApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * SysML v2 import jobs (P2 §5.2, T6). Queued, then on the import executor:
 * <ol>
 * <li>parse the text into the intermediate tree ({@link SysmlParser});</li>
 * <li>check: any unsupported syntax or syntax error fails the whole job with every problem and its line, and nothing
 * is created (no half model);</li>
 * <li>map to sysarch ({@link SysmlMapper}), UNMAPPED listed;</li>
 * <li>create a new Sirius project (user's decision) holding all the elements in one document, written in one input
 * on its editing context: all or nothing (the project is deleted if it fails);</li>
 * <li>check the result: the OCL constraints on every element and R6 (requirements without satisfiedBy); R1–R3 and R5
 * concern Polarion bindings, which a new project has none of, and import never creates Work Items (spec).</li>
 * </ol>
 */
public class ImportService {

    public static final int MAX_TEXT = 5 * 1024 * 1024;

    /** An import job as the API shows it. */
    public record Job(String id, String status, String sourceName, String defaultLayer, String projectId, Map<String, Object> report, List<String> problems,
            String error, String requestedBy, OffsetDateTime createdAt, OffsetDateTime finishedAt) {
    }

    /** A request that cannot be served (400/404 at the API). */
    public static class ImportRequestException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int status;

        public ImportRequestException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return this.status;
        }
    }

    private final Logger logger = LoggerFactory.getLogger(ImportService.class);

    private final JdbcClient jdbc;

    private final ProjectModels models;

    private final IProjectApplicationService projects;

    private final ConstraintValidator constraints;

    private final AuditLog audit;

    private final ObjectMapper json;

    private final Executor executor;

    private final Clock clock;

    public ImportService(JdbcClient jdbc, ProjectModels models, IProjectApplicationService projects, ConstraintValidator constraints, AuditLog audit,
            ObjectMapper json, Executor executor, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.models = Objects.requireNonNull(models);
        this.projects = Objects.requireNonNull(projects);
        this.constraints = Objects.requireNonNull(constraints);
        this.audit = Objects.requireNonNull(audit);
        this.json = Objects.requireNonNull(json);
        this.executor = Objects.requireNonNull(executor);
        this.clock = Objects.requireNonNull(clock);
    }

    public Job submit(String text, String projectName, String layer, String sourceName, String user) {
        if (text == null || text.isBlank()) {
            throw new ImportRequestException(400, "没有要导入的文本");
        }
        if (text.length() > MAX_TEXT) {
            throw new ImportRequestException(400, "文本超过 " + MAX_TEXT / 1024 / 1024 + " MB");
        }
        Layer defaultLayer = layer == null || layer.isBlank() ? Layer.LOGICAL : Layer.getByName(layer.strip().toUpperCase(java.util.Locale.ROOT));
        if (defaultLayer == null) {
            throw new ImportRequestException(400, "defaultLayer 只能是 " + Layer.VALUES.stream().map(Layer::getName).toList());
        }
        String id = UUID.randomUUID().toString();
        this.jdbc.sql("INSERT INTO import_job (id, status, source_name, default_layer, requested_by, created_at) VALUES (:id, 'QUEUED', :s, :l, :u, :at)")
                .param("id", id).param("s", sourceName).param("l", defaultLayer.getName()).param("u", user).param("at", OffsetDateTime.now(this.clock)).update();
        this.executor.execute(() -> this.run(id, text, projectName, defaultLayer, user));
        return this.get(id);
    }

    void run(String id, String text, String projectName, Layer defaultLayer, String user) {
        this.jdbc.sql("UPDATE import_job SET status = 'RUNNING' WHERE id = :id").param("id", id).update();
        String projectId = null;
        try {
            SysmlParser.Parsed parsed = SysmlParser.parse(text);
            if (!parsed.problems().isEmpty()) {
                this.finish(id, "FAILED", null, null, parsed.problems().stream().map(SysmlParser.Problem::toString).toList(), null);
                return;
            }
            SysmlMapper.Mapped mapped = SysmlMapper.map(parsed.root(), defaultLayer);
            if (mapped.roots().isEmpty()) {
                this.finish(id, "FAILED", null, null, List.of("文本里没有可导入的元素（part def、part、port def、port、connection、requirement）"), null);
                return;
            }
            String name = this.uniqueName(projectName != null && !projectName.isBlank() ? projectName.strip()
                    : (mapped.packageName() == null ? "SysML v2 导入" : mapped.packageName()) + "（导入 "
                            + OffsetDateTime.now(this.clock).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) + "）");
            IPayload created = this.projects.createProject(new CreateProjectInput(UUID.randomUUID(), name, List.of()));
            if (!(created instanceof CreateProjectSuccessPayload success)) {
                throw new IllegalStateException("could not create the project: " + created);
            }
            projectId = success.project().id();
            List<EObject> roots = mapped.roots();
            this.models.addDocument(projectId, mapped.packageName() == null ? name : mapped.packageName(), () -> roots);
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("projectId", projectId);
            report.put("projectName", name);
            report.put("packageName", mapped.packageName());
            report.put("total", mapped.total());
            report.put("counts", mapped.counts());
            report.put("defaultLayer", defaultLayer.getName());
            report.put("defaultedLayer", mapped.defaultedLayer());
            report.put("derivedPorts", mapped.derivedPorts());
            report.put("unmapped", mapped.unmapped());
            report.put("consistency", this.consistency(projectId));
            this.finish(id, "DONE", projectId, report, List.of(), null);
            this.audit.append(projectId, user, "IMPORT", null, "SysML v2: " + mapped.total() + " elements into new project " + name + " (job " + id + ")");
            this.logger.info("Imported {} elements into new project {} ({}) by {}", mapped.total(), name, projectId, user);
        } catch (RuntimeException e) {
            this.logger.error("Import job {} failed", id, e);
            if (projectId != null) {
                this.projects.deleteProject(new DeleteProjectInput(UUID.randomUUID(), projectId)); // no half model
            }
            this.finish(id, "FAILED", null, null, List.of(), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** The check after import (T6 step 3): constraint violations and R6, read on the new project's editing context. */
    private Map<String, Object> consistency(String projectId) {
        return this.models.read(projectId, resources -> {
            List<ArchElement> elements = new ArrayList<>();
            resources.forEach(r -> r.getAllContents().forEachRemaining(e -> {
                if (e instanceof ArchElement element && !(e instanceof DiagramLayout)) {
                    elements.add(element);
                }
            }));
            List<ValidationError> violations = this.constraints.validateAll(elements);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("constraintViolations", violations.stream().map(v -> v.code() + " " + (v.elementId() == null ? "" : v.elementId() + "：") + v.message()).toList());
            result.put("R6", elements.stream().filter(Requirement.class::isInstance).map(Requirement.class::cast).filter(r -> r.getSatisfiedBy().isEmpty())
                    .map(r -> r.getId() + " " + r.getName()).toList());
            result.put("notApplicable", "R1–R3、R5 针对 Polarion 绑定：新导入的项目没有任何绑定，导入也不会创建 Work Item；R4（层越级）已包含在约束检查里");
            return result;
        });
    }

    public Job get(String id) {
        return this.jdbc.sql("SELECT * FROM import_job WHERE id = :id").param("id", id).query(this::job).optional()
                .orElseThrow(() -> new ImportRequestException(404, "导入任务 " + id + " 不存在"));
    }

    public List<Job> recent() {
        return this.jdbc.sql("SELECT * FROM import_job ORDER BY created_at DESC LIMIT 50").query(this::job).list();
    }

    public int failInterrupted() {
        return this.jdbc.sql("UPDATE import_job SET status = 'FAILED', error = '服务重启，任务中断，请重新提交', finished_at = :at WHERE status IN ('QUEUED', 'RUNNING')")
                .param("at", OffsetDateTime.now(this.clock)).update();
    }

    private String uniqueName(String name) {
        String candidate = name;
        for (int i = 2; this.jdbc.sql("SELECT COUNT(*) FROM project WHERE name = :n").param("n", candidate).query(Integer.class).single() > 0; i++) {
            candidate = name + " (" + i + ")";
        }
        return candidate;
    }

    private void finish(String id, String status, String projectId, Map<String, Object> report, List<String> problems, String error) {
        this.jdbc.sql("UPDATE import_job SET status = :s, project_id = :p, report = :r, problems = :pr, error = :e, finished_at = :at WHERE id = :id")
                .param("s", status).param("p", projectId).param("r", report == null ? null : this.write(report)).param("pr", this.write(problems))
                .param("e", error).param("at", OffsetDateTime.now(this.clock)).param("id", id).update();
    }

    private Job job(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Job(rs.getString("id"), rs.getString("status"), rs.getString("source_name"), rs.getString("default_layer"), rs.getString("project_id"),
                this.readMap(rs.getString("report")), this.readList(rs.getString("problems")), rs.getString("error"), rs.getString("requested_by"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("finished_at", OffsetDateTime.class));
    }

    private String write(Object value) {
        try {
            return this.json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> readMap(String text) {
        try {
            return text == null ? null : this.json.readValue(text, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException e) {
            return Map.of("unreadable", text);
        }
    }

    private List<String> readList(String text) {
        try {
            return text == null ? List.of() : this.json.readValue(text, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            return List.of(text);
        }
    }
}

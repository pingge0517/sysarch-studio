package com.example.sysarch.sysmlimport;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.identity.CurrentUser;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.validation.ConstraintValidator;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.sirius.web.application.project.services.api.IProjectApplicationService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/imports} (P2 §4.5 {@code POST /imports/sysmlv2}, T6).
 *
 * <ul>
 * <li>{@code POST /imports/sysmlv2} with the text as {@code text/plain} (UTF-8) and optional query parameters
 * {@code projectName}, {@code defaultLayer} (default LOGICAL), {@code fileName}: 202 with the queued job.</li>
 * <li>{@code GET /imports/{jobId}}: QUEUED / RUNNING / DONE (with the report: new project, counts, layers defaulted,
 * ports made explicit, UNMAPPED, consistency) / FAILED (with {@code problems}, each "第 N 行：…", and nothing
 * created).</li>
 * <li>{@code GET /imports}: the 50 most recent jobs.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final ImportService imports;

    public ImportController(ImportService imports) {
        this.imports = Objects.requireNonNull(imports);
    }

    @PostMapping(value = "/sysmlv2", consumes = { MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE, "*/*" })
    public ResponseEntity<ImportService.Job> submit(@RequestBody(required = false) byte[] body, @RequestParam(value = "projectName", required = false) String projectName,
            @RequestParam(value = "defaultLayer", required = false) String defaultLayer, @RequestParam(value = "fileName", required = false) String fileName,
            HttpServletRequest http) {
        String text = body == null ? null : new String(body, java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(this.imports.submit(text, projectName, defaultLayer, fileName, CurrentUser.of(http).orElse("anonymous")));
    }

    @GetMapping("/{jobId}")
    public ImportService.Job get(@PathVariable String jobId) {
        return this.imports.get(jobId);
    }

    @GetMapping
    public List<ImportService.Job> recent() {
        return this.imports.recent();
    }

    @ExceptionHandler(ImportService.ImportRequestException.class)
    public ResponseEntity<Map<String, String>> refused(ImportService.ImportRequestException e) {
        return ResponseEntity.status(e.status()).body(Map.of("message", e.getMessage()));
    }

    /** The service, with its own executor (not an Executor bean: see ReportConfiguration), and start-up recovery. */
    @Configuration
    public static class Wiring {

        @Bean
        public ImportService importService(JdbcClient jdbc, ProjectModels models, IProjectApplicationService projects, ConstraintValidator constraints,
                AuditLog audit, ObjectMapper json) {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(50);
            executor.setThreadNamePrefix("import-");
            executor.initialize();
            return new ImportService(jdbc, models, projects, constraints, audit, json, executor, Clock.systemUTC());
        }

        @EventListener(ApplicationReadyEvent.class)
        public void interrupted(ApplicationReadyEvent event) {
            event.getApplicationContext().getBean(ImportService.class).failInterrupted();
        }
    }
}

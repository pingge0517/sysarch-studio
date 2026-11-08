package com.example.sysarch.migration;

import java.util.Objects;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/migrations/interface-entity/report?mode=dry-run|applied}: the interface migration report as CSV
 * ({@code migration-report.csv}, P1 T1 step 3). An admin endpoint of our own, not part of the Polarion OpenAPI.
 */
@RestController
@RequestMapping("/api/v1/migrations/interface-entity")
public class InterfaceMigrationController {

    private final InterfaceMigrationReport report;

    public InterfaceMigrationController(InterfaceMigrationReport report) {
        this.report = Objects.requireNonNull(report);
    }

    @GetMapping(value = "/report", produces = "text/csv")
    public ResponseEntity<String> report(@RequestParam(name = "mode", defaultValue = "dry-run") String mode) {
        var rows = "applied".equals(mode) ? this.report.applied() : this.report.dryRun();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"migration-report-" + ("applied".equals(mode) ? "applied" : "dry-run") + ".csv\"")
                .body(InterfaceMigrationReport.toCsv(rows));
    }
}

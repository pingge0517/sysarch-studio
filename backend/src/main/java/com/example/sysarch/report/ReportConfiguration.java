package com.example.sysarch.report;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.baseline.BaselineRepository;
import com.example.sysarch.project.ProjectModels;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Reports (P2 T5): where files go ({@code sysarch.report.dir}, env {@code SYSARCH_REPORT_DIR}; default
 * {@code data/reports} under the working directory), which CJK font the PDF embeds ({@code sysarch.report.fonts}, the
 * first existing {@code .ttf}), how many reports render at once ({@code sysarch.report.threads}, default 2); the daily
 * clean-up of files older than 90 days, and jobs a restart interrupted marked failed.
 */
@Configuration
public class ReportConfiguration {

    /**
     * Common places of a CJK TrueType font: Windows (DengXian, SimHei) and Debian/Ubuntu's fonts-droid-fallback. (The
     * Debian WenQuanYi and AR PL packages ship .ttc collections and Noto CJK is CFF OpenType: openhtmltopdf embeds
     * neither.)
     */
    static final String DEFAULT_FONTS = "C:/Windows/Fonts/Deng.ttf,C:/Windows/Fonts/simhei.ttf,/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf";

    /** The default fonts as paths (also for the tests). */
    public static List<Path> defaultFonts() {
        return java.util.Arrays.stream(DEFAULT_FONTS.split(",")).map(Path::of).toList();
    }

    @Bean
    public ReportRenderer reportRenderer(@Value("${sysarch.report.fonts:" + DEFAULT_FONTS + "}") List<String> fonts) {
        return new ReportRenderer(fonts.stream().map(String::strip).filter(s -> !s.isEmpty()).map(Path::of).toList());
    }

    /**
     * The executor is the service's own, not a bean: an Executor bean would make Spring Boot drop its default task
     * executor, which other parts (Sirius among them) may use.
     */
    @Bean
    public ReportService reportService(JdbcClient jdbc, ProjectModels models, BaselineRepository baselines, ReportRenderer renderer, AuditLog audit,
            ObjectMapper json, @Value("${sysarch.report.dir:data/reports}") String dir, @Value("${sysarch.report.threads:2}") int threads,
            @Value("${sysarch.report.max-total-size:5GB}") org.springframework.util.unit.DataSize maxTotal) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("report-");
        executor.initialize();
        ReportService service = new ReportService(jdbc, models, baselines, renderer, audit, json, Path.of(dir).toAbsolutePath(), executor, Clock.systemUTC());
        service.setMaxTotalBytes(maxTotal.toBytes());
        return service;
    }

    /** Housekeeping: interrupted jobs at start, expired files every night. */
    @Configuration
    public static class Housekeeping {

        private final Logger logger = LoggerFactory.getLogger(Housekeeping.class);

        private final ReportService reports;

        public Housekeeping(ReportService reports) {
            this.reports = reports;
        }

        @EventListener(ApplicationReadyEvent.class)
        public void interrupted() {
            int failed = this.reports.failInterrupted();
            if (failed > 0) {
                this.logger.warn("{} report jobs were interrupted by the restart and marked FAILED", failed);
            }
        }

        @Scheduled(cron = "${sysarch.report.retention-cron:0 15 3 * * *}")
        public void expire() {
            int expired = this.reports.expire();
            this.reports.enforceSizeCap(null);
            if (expired > 0) {
                this.logger.info("Deleted the files of {} report jobs older than 90 days", expired);
            }
        }
    }
}

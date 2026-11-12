package com.example.sysarch.project;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.baseline.BaselineRepository;
import com.example.sysarch.branch.BranchService;
import com.example.sysarch.identity.Editors;
import com.example.sysarch.lock.LockService;
import com.example.sysarch.report.ReportService;
import com.example.sysarch.session.SessionService;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.eclipse.sirius.components.events.ICause;
import org.eclipse.sirius.web.domain.boundedcontexts.project.events.ProjectDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * When a Sirius project is deleted, the SysArch data that only made sense for it goes too (P1 trial issue T9-02, P2 T3's
 * known gap): its branches (open ones closed as by abandon, which deletes their workspace projects; then the rows with
 * their changesets and merge runs), its baselines, locks, edit sessions, report jobs with their files and element
 * comments. A deleted workspace project abandons its open branch. One {@code PROJECT_DELETE} audit row lists the counts.
 *
 * <p>Kept: the audit log (append-only), Polarion links and sync state (keyed by the Polarion project, which still
 * exists; the next consistency report shows the bindings as R1), import job history and the interface migration backup.
 *
 * <p>Runs after Sirius' transaction committed (a deletion rolled back removes nothing) and on its own thread: in an
 * after-commit callback the finished transaction is still bound, so work joining it would never be committed, and
 * closing a branch deletes its workspace project through Sirius, which publishes its own event.
 */
@Component
public class ProjectCleanup {

    /** What was removed for one deleted project. */
    public record Removed(String projectId, int branches, boolean workspaceOfOpenBranch, int baselines, int locks, int sessions, int reports,
            int comments) {

        public String describe() {
            return "branches " + this.branches + ", baselines " + this.baselines + ", locks " + this.locks + ", sessions " + this.sessions + ", reports "
                    + this.reports + ", comments " + this.comments + (this.workspaceOfOpenBranch ? "; was the workspace of an open branch (abandoned)" : "");
        }
    }

    private final Logger logger = LoggerFactory.getLogger(ProjectCleanup.class);

    private final JdbcClient jdbc;

    private final BranchService branches;

    private final BaselineRepository baselines;

    private final LockService locks;

    private final SessionService sessions;

    private final ReportService reports;

    private final AuditLog audit;

    private final Editors editors;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "sysarch-project-cleanup");
        thread.setDaemon(true);
        return thread;
    });

    public ProjectCleanup(JdbcClient jdbc, BranchService branches, BaselineRepository baselines, LockService locks, SessionService sessions,
            ReportService reports, AuditLog audit, Editors editors) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.branches = Objects.requireNonNull(branches);
        this.baselines = Objects.requireNonNull(baselines);
        this.locks = Objects.requireNonNull(locks);
        this.sessions = Objects.requireNonNull(sessions);
        this.reports = Objects.requireNonNull(reports);
        this.audit = Objects.requireNonNull(audit);
        this.editors = Objects.requireNonNull(editors);
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onProjectDeleted(ProjectDeletedEvent event) {
        String projectId = event.project().getId();
        String actor = this.actorOf(event.causedBy());
        this.executor.execute(() -> {
            try {
                Removed removed = this.cleanUp(projectId, actor);
                this.logger.info("Project {} deleted: removed {}", projectId, removed.describe());
            } catch (RuntimeException e) {
                this.logger.error("Cleaning up after the deletion of project {} failed", projectId, e);
            }
        });
    }

    /** Removes the project's data now (also the entry point for tests). */
    public Removed cleanUp(String projectId, String actor) {
        BranchService.Removed branchRows = this.branches.projectDeleted(projectId, actor);
        int baselineRows = this.baselines.dropProject(projectId);
        int lockRows = this.locks.dropProject(projectId);
        int sessionRows = this.sessions.dropProject(projectId);
        int reportJobs = this.reports.dropProject(projectId);
        int commentRows = this.jdbc.sql("DELETE FROM element_comment WHERE project_id = :p").param("p", projectId).update();
        Removed removed = new Removed(projectId, branchRows.branches(), branchRows.workspaceOfOpenBranch(), baselineRows, lockRows, sessionRows, reportJobs,
                commentRows);
        this.audit.append(projectId, actor, AuditLog.PROJECT_DELETE, null, removed.describe());
        return removed;
    }

    /** Who deleted it: the user {@code GraphQLEditorFilter} recorded for the input, else "system" (our own deletions). */
    private String actorOf(ICause cause) {
        for (ICause c = cause; c != null; c = c.causedBy() == c ? null : c.causedBy()) {
            String user = this.editors.of(c.id()).map(Editors.Editor::user).orElse(null);
            if (user != null) {
                return user;
            }
        }
        return "system";
    }

    @PreDestroy
    void stop() {
        this.executor.shutdown();
    }
}

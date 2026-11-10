package com.example.sysarch.sync.consistency;

import com.example.sysarch.link.LinkRepository;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.sync.SyncProperties;
import com.example.sysarch.sync.consistency.ConsistencyRules.Facts;
import com.example.sysarch.sync.consistency.ConsistencyRules.Issue;
import com.example.sysarch.sync.model.ModelOperations;
import com.example.sysarch.sync.model.SiriusModelGateway;
import com.example.sysarch.validation.ConstraintValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IValidationService;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * The consistency report in Sirius Web's Validation view, its right-side panel (T8 step 4, decided with the user).
 * Sirius collects every {@link IValidationService} bean into that view and refreshes it when it is opened and after
 * every saved edit: the spec's "打开图纸时与保存时各触发一次". BLOCKER issues are errors (red), MAJOR and MINOR
 * warnings (yellow).
 *
 * <p>It runs on the editing context thread and has no user token, so it judges from what is recorded: R1 (from pulls
 * and webhooks), R2 on the canvas side, R3, R5, R6. Checks that ask Polarion live need {@code /consistency/report} with a
 * token. R4 is left out here because the OCL {@code LayerAdjacentOnly} error is already listed for the same Allocate.
 */
@Service
public class ConsistencyDiagnostics implements IValidationService {

    public static final String SOURCE = "com.example.sysarch.consistency";

    private final Logger logger = LoggerFactory.getLogger(ConsistencyDiagnostics.class);

    /** Lazy: Sirius' properties validation also collects every IValidationService, and the gateway depends on Sirius' editing
     * context services, which depend on the properties view: an eager dependency is a bean cycle (found at startup). */
    private final ObjectProvider<SiriusModelGateway> gateway;

    private final LinkRepository links;

    private final ConsistencyStore store;

    private final ConstraintValidator constraints;

    private final ConsistencyRules rules;

    private final SyncProperties properties;

    private final com.example.sysarch.sync.SyncStatusStore status;

    public ConsistencyDiagnostics(ObjectProvider<SiriusModelGateway> gateway, LinkRepository links, ConsistencyStore store, ConstraintValidator constraints,
            SyncProperties properties, com.example.sysarch.sync.SyncStatusStore status) {
        this.properties = Objects.requireNonNull(properties);
        this.status = Objects.requireNonNull(status);
        this.gateway = Objects.requireNonNull(gateway);
        this.links = Objects.requireNonNull(links);
        this.store = Objects.requireNonNull(store);
        this.constraints = Objects.requireNonNull(constraints);
        this.rules = new ConsistencyRules(properties);
    }

    @Override
    public List<Object> validate(IEditingContext editingContext) {
        if (!(editingContext instanceof IEMFEditingContext emf)) {
            return List.of();
        }
        try {
            Map<String, ArchElement> index = ModelOperations.index(emf.getDomain().getResourceSet().getAllContents());
            // P3 T2: R7–R10 judge the model only, for every project (user's decision)
            List<Object> behavior = new ArrayList<>();
            for (Issue issue : com.example.sysarch.behavior.BehaviorChecks.evaluate(emf.getDomain().getResourceSet().getAllContents())) {
                behavior.add(toDiagnostic(issue, issue.elementId() == null ? null : index.get(issue.elementId())));
            }
            Optional<String> projectId = this.gateway.getObject().polarionProjectOf(editingContext.getId());
            if (projectId.isEmpty()) {
                return behavior; // a project not bound to Polarion has no binding consistency (R1–R6, sync) to report
            }
            Facts facts = new Facts(projectId.get(), ModelOperations.snapshots(index, null, this.constraints), this.links.findByProject(projectId.get(), null),
                    this.store.pendingChecks(projectId.get()), this.store.pendingClaims(projectId.get()), null);
            List<Object> diagnostics = new ArrayList<>(this.syncStatus(projectId.get()));
            for (Issue issue : this.rules.evaluate(facts)) {
                if (!"R4".equals(issue.rule())) {
                    ArchElement element = issue.elementId() == null ? null : index.get(issue.elementId());
                    diagnostics.add(toDiagnostic(issue, element));
                }
            }
            diagnostics.addAll(behavior);
            return diagnostics;
        } catch (RuntimeException e) {
            // The Validation view must keep working even if the database is unreachable.
            this.logger.warn("consistency diagnostics unavailable: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * The sync panel (P1 T6 step 4): the three times as an info line, and the scheduled pull's state: an error when it is
     * paused (the alert), a warning after a failure, an info line when no service account is configured.
     */
    private List<Diagnostic> syncStatus(String projectId) {
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        this.status.checked(projectId, now);
        com.example.sysarch.sync.SyncStatusStore.Status state = this.status.status(projectId);
        List<Diagnostic> lines = new ArrayList<>();
        lines.add(new BasicDiagnostic(Diagnostic.INFO, SOURCE, 0, "[同步] 上次推送：" + time(state.lastPushAt()) + "；上次拉取：" + time(state.lastPullAt())
                + "；上次一致性校验：" + time(now), new Object[0]));
        if (this.properties.getSyncToken() == null || this.properties.getSyncToken().isBlank()) {
            lines.add(new BasicDiagnostic(Diagnostic.INFO, SOURCE, 0, "[同步] 未配置后台同步账号（POLARION_SYNC_TOKEN），定时拉取未运行", new Object[0]));
        } else if (state.schedulePaused()) {
            lines.add(new BasicDiagnostic(Diagnostic.ERROR, SOURCE, 0, "[同步] 定时拉取已暂停：连续 " + state.scheduleFailures() + " 次失败，最后一次："
                    + state.scheduleLastError() + "。可以继续编辑；Polarion 恢复后手动拉取一次或调用 POST /api/v1/sync/status/resume", new Object[0]));
        } else if (state.scheduleFailures() > 0) {
            lines.add(new BasicDiagnostic(Diagnostic.WARNING, SOURCE, 0, "[同步] 定时拉取失败 " + state.scheduleFailures() + " 次：" + state.scheduleLastError()
                    + "；下次尝试：" + time(state.scheduleNextAttemptAt()), new Object[0]));
        }
        return lines;
    }

    private static String time(java.time.OffsetDateTime at) {
        return at == null ? "从未" : at.atZoneSameInstant(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    /** Diagnostics are per editing context; nothing per property field. */
    @Override
    public List<Object> validate(Object object, Object feature) {
        return List.of();
    }

    public static Diagnostic toDiagnostic(Issue issue, ArchElement element) {
        int severity = ConsistencyRules.BLOCKER.equals(issue.severity()) ? Diagnostic.ERROR : Diagnostic.WARNING;
        Object[] data = element == null ? new Object[0] : new Object[] { element };
        return new BasicDiagnostic(severity, SOURCE, Integer.parseInt(issue.rule().substring(1)), "[" + issue.rule() + " " + issue.severity() + "] "
                + issue.message(), data);
    }
}

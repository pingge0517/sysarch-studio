package com.example.sysarch.sync.consistency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.sysarch.sync.api.model.ConsistencyReport;
import com.example.sysarch.sync.api.model.ConsistencyReportIssuesInner;
import com.example.sysarch.link.LinkRepository;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.sync.SyncProperties;
import com.example.sysarch.sync.SyncService;
import com.example.sysarch.sync.SyncStore;
import com.example.sysarch.sync.model.ModelGateway;
import com.example.sysarch.sync.polarion.PolarionClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * P3 T2 step 3 (user's decision): R7–R10 are in the consistency report of every project. A project bound to Polarion
 * gets them after R1–R6; an unbound one gets only them (no 404 any more); an unknown project is still 404; repair
 * refuses them.
 */
class BehaviorReportTest {

    private static final ConsistencyRules.Issue R7 = new ConsistencyRules.Issue("R7:SM-1", "R7", ConsistencyRules.BLOCKER, "SM-1", null, "初始状态不唯一",
            false, null, List.of());

    private static ConsistencyService service(ModelGateway model) {
        ConsistencyProperties properties = new ConsistencyProperties();
        properties.setStrategy(ConsistencyProperties.MODEL_WINS);
        LinkRepository links = mock(LinkRepository.class);
        when(links.findByProject(any(), any())).thenReturn(List.of());
        ConsistencyStore store = mock(ConsistencyStore.class);
        when(store.pendingChecks(any())).thenReturn(Map.of());
        when(store.pendingClaims(any())).thenReturn(Map.of());
        return new ConsistencyService(model, links, store, mock(SyncStore.class), mock(SyncService.class), mock(PolarionClient.class), new SyncProperties(),
                properties, new ObjectMapper(), mock(TransactionTemplate.class));
    }

    @Test
    void anUnboundProjectGetsTheBehaviourChecks() {
        ModelGateway unbound = mock(ModelGateway.class);
        when(unbound.readAll("project001")).thenThrow(new ModelGateway.UnmappedProjectException("project001"));
        when(unbound.readAll("nope")).thenThrow(new ModelGateway.UnmappedProjectException("nope"));
        ConsistencyService service = service(unbound);
        service.behaviorChecks(projectId -> List.of(R7));
        ConsistencyReport report = service.report("project001", null);
        assertEquals(List.of("R7:SM-1"), report.getIssues().stream().map(ConsistencyReportIssuesInner::getId).toList());
        assertEquals(ConsistencyReportIssuesInner.RuleEnum.R7, report.getIssues().get(0).getRule());

        service.behaviorChecks(projectId -> {
            throw new ProjectModels.UnknownProjectException("no Sirius project 'nope'");
        });
        ResponseStatusException unknown = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class, () -> service.report("nope", null));
        assertEquals(404, unknown.getStatusCode().value());
    }

    @Test
    void aBoundProjectGetsThemAfterTheBindingRules() {
        ModelGateway bound = mock(ModelGateway.class);
        when(bound.readAll("sysarch")).thenReturn(Map.of());
        ConsistencyService service = service(bound);
        ConsistencyRules.Issue r10 = new ConsistencyRules.Issue("R10:AN-1", "R10", ConsistencyRules.MAJOR, "AN-1", null, "动作不完整", false, null, List.of());
        service.behaviorChecks(projectId -> List.of(r10, R7));
        assertEquals(List.of("R7:SM-1", "R10:AN-1"), service.report("sysarch", null).getIssues().stream().map(ConsistencyReportIssuesInner::getId).toList(),
                "ordered by rule number, R10 after R7");
        ConsistencyService.RepairResult repaired = service.repair("sysarch", List.of("R7:SM-1"), null, null, "alice");
        assertEquals("REFUSED", repaired.actions().get(0).status());
    }
}

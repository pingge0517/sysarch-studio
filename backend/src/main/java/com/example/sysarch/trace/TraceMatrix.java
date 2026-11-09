package com.example.sysarch.trace;

import com.example.sysarch.ElementIds;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Requirement;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * {@code GET /trace/matrix} (spec P1 §4.1, T5 step 2): every requirement with what satisfies and verifies it. The
 * OpenAPI gives the response no schema; this record is it. Structured (no layout), so the matrix page and P2's report
 * can both use it.
 *
 * <p>{@code satisfied} is false for a requirement with no {@code satisfiedBy}: the consistency report lists those as R6
 * (P1 T5 step 3, user's decision: counted per requirement, as the acceptance test does).
 */
public record TraceMatrix(String projectId, OffsetDateTime generatedAt, Summary summary, List<Entry> requirements) {

    /** An element a requirement points at. */
    public record Ref(String id, String name, String type, String layer) {
    }

    public record Entry(String id, String reqId, String name, String layer, String verifyMethod, int priority, List<Ref> satisfiedBy,
            List<Ref> verifiedBy, boolean satisfied) {
    }

    public record Summary(int requirements, int satisfied, int verified, int unsatisfied) {
    }

    /** Every requirement of the resources, in document order. */
    public static TraceMatrix of(String projectId, Collection<Resource> resources) {
        List<Entry> entries = new ArrayList<>();
        for (Resource resource : resources) {
            for (TreeIterator<EObject> it = resource.getAllContents(); it.hasNext();) {
                if (it.next() instanceof Requirement requirement) {
                    entries.add(entry(requirement));
                }
            }
        }
        int satisfied = (int) entries.stream().filter(Entry::satisfied).count();
        int verified = (int) entries.stream().filter(e -> !e.verifiedBy().isEmpty()).count();
        return new TraceMatrix(projectId, OffsetDateTime.now(), new Summary(entries.size(), satisfied, verified, entries.size() - satisfied), entries);
    }

    private static Entry entry(Requirement requirement) {
        List<Ref> satisfiedBy = requirement.getSatisfiedBy().stream().map(TraceMatrix::ref).toList();
        List<Ref> verifiedBy = requirement.getVerifiedBy().stream().map(TraceMatrix::ref).toList();
        return new Entry(ElementIds.of(requirement), requirement.getReqId(), requirement.getName(), requirement.getLayer().getLiteral(),
                requirement.getVerifyMethod().getLiteral(), requirement.getPriority(), satisfiedBy, verifiedBy, !satisfiedBy.isEmpty());
    }

    private static Ref ref(ArchElement element) {
        return new Ref(ElementIds.of(element), element.getName(), element.eClass().getName(), element.getLayer().getLiteral());
    }
}

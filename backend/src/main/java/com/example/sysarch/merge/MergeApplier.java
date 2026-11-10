package com.example.sysarch.merge;

import com.example.sysarch.ElementIds;
import com.example.sysarch.baseline.ModelSnapshot.ElementState;
import com.example.sysarch.baseline.SnapshotModels;
import com.example.sysarch.merge.MergeEngine.Operation;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.DiagramLayout;
import com.example.sysarch.model.SysarchPackage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Writes a merge result into the trunk's model (P2 T4), on the editing context's thread. Three passes, so no
 * reference ever points at a half-built element: create the added elements; set every added or modified element to
 * its merged state and put it where it belongs; delete the deleted ones (their references go with them). The caller
 * records the change and undoes it on any failure, so the trunk gets all of it or none.
 */
final class MergeApplier {

    private MergeApplier() {
    }

    /** @return how many elements changed */
    static int apply(List<Resource> resources, Map<String, ElementState> merged, List<Operation> operations) {
        if (resources.isEmpty()) {
            throw new IllegalStateException("the trunk has no sysarch document");
        }
        Map<String, EObject> index = new HashMap<>();
        for (Resource resource : resources) {
            resource.getAllContents().forEachRemaining(e -> {
                if (e instanceof ArchElement && !(e instanceof DiagramLayout)) {
                    index.put(ElementIds.of(e), e);
                }
            });
        }
        for (Operation op : operations) {
            if (op.op().equals("ADD")) {
                ElementState state = merged.get(op.elementId());
                index.put(op.elementId(), EcoreUtil.create((EClass) SysarchPackage.eINSTANCE.getEClassifier(state.type())));
            }
        }
        Resource topLevel = resources.get(0);
        for (Operation op : operations) {
            if (!op.op().equals("DELETE")) {
                ElementState state = merged.get(op.elementId());
                EObject element = index.get(op.elementId());
                SnapshotModels.set(element, state, index);
                SnapshotModels.place(element, state, index, topLevel);
            }
        }
        for (Operation op : operations) {
            if (op.op().equals("DELETE")) {
                EObject element = index.get(op.elementId());
                if (element != null && element.eResource() != null) {
                    EcoreUtil.delete(element, true);
                }
            }
        }
        return operations.size();
    }
}

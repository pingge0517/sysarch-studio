package com.example.sysarch.sync.model;

import com.example.sysarch.ElementIds;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.DiagramLayout;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.sync.model.ModelGateway.ConnectionRepair;
import com.example.sysarch.sync.model.ModelGateway.ElementSnapshot;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.validation.ValidationError;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * The model reads and edits behind {@link ModelGateway}, on plain EMF. {@link SiriusModelGateway} runs them inside the
 * editing context; tests run them on an in-memory resource, so both execute the same code.
 */
public final class ModelOperations {

    private ModelOperations() {
    }

    /** Every element by element id. Diagram layouts are left out: they are view data, not model elements (§5.1). */
    public static Map<String, ArchElement> index(Iterator<? extends Notifier> contents) {
        Map<String, ArchElement> index = new TreeMap<>();
        contents.forEachRemaining(notifier -> {
            if (notifier instanceof ArchElement element && !(element instanceof DiagramLayout)) {
                index.put(ElementIds.of(element), element);
            }
        });
        return index;
    }

    /** Snapshots of the elements with these ids, or of every element when {@code ids} is null. */
    public static Map<String, ElementSnapshot> snapshots(Map<String, ArchElement> index, Collection<String> ids, ConstraintValidator constraints) {
        Map<String, ElementSnapshot> result = new LinkedHashMap<>();
        index.forEach((id, element) -> {
            if (ids == null || ids.contains(id)) {
                result.put(id, ElementSnapshots.of(element, constraints));
            }
        });
        return result;
    }

    /** @return how many values were written */
    public static int applyPolarionOwned(Map<String, ArchElement> index, Map<String, Map<String, String>> changes) {
        int applied = 0;
        for (var change : changes.entrySet()) {
            if (index.get(change.getKey()) instanceof Requirement requirement) {
                if (change.getValue().containsKey(ElementSnapshot.TEXT)) {
                    requirement.setText(change.getValue().get(ElementSnapshot.TEXT));
                    applied++;
                }
                if (change.getValue().containsKey(ElementSnapshot.REQ_ID)) {
                    requirement.setReqId(change.getValue().get(ElementSnapshot.REQ_ID)); // P1 T6: Polarion owns reqId
                    applied++;
                }
            }
        }
        return applied;
    }

    /**
     * Applies an R2 repair to the model (POLARION_WINS). The endpoints are checked with the on-drop constraints first
     * (spec §3.2), so a repair can never create a connection the editor would refuse.
     *
     * @return null when applied, else why not (nothing was changed)
     */
    public static String repairConnection(Map<String, ArchElement> index, ConnectionRepair repair, ConstraintValidator constraints) {
        ArchElement existing = index.get(repair.connectionId());
        if (existing != null && !(existing instanceof Connection)) {
            return repair.connectionId() + " is a " + existing.eClass().getName() + ", not a Connection";
        }
        Connection connection = (Connection) existing;
        if (repair.delete()) {
            if (connection != null) {
                EcoreUtil.delete(connection, true);
            }
            return null;
        }
        if (!(index.get(repair.sourceId()) instanceof Port source)) {
            return "source " + repair.sourceId() + " is not a port in the model";
        }
        if (!(index.get(repair.targetId()) instanceof Port target)) {
            return "target " + repair.targetId() + " is not a port in the model";
        }
        List<ValidationError> errors = constraints.validateEdgeDrop(source, target,
                connection == null ? com.example.sysarch.model.ConnectionKind.ASSEMBLY : connection.getKind());
        if (!errors.isEmpty()) {
            return errors.stream().map(e -> e.code() + ": " + e.message()).toList().toString();
        }
        if (connection == null) {
            if (source.eResource() == null) {
                return "source port " + repair.sourceId() + " is not in a document";
            }
            connection = SysarchFactory.eINSTANCE.createConnection();
            connection.setId(repair.connectionId());
            connection.setName(repair.name());
            source.eResource().getContents().add(connection);
        }
        connection.setSource(source);
        connection.setTarget(target);
        return null;
    }
}

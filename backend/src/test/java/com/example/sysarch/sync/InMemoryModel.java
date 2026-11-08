package com.example.sysarch.sync;

import com.example.sysarch.model.Allocate;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.PortDirection;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.sync.model.ModelGateway;
import com.example.sysarch.sync.model.ModelOperations;
import com.example.sysarch.validation.ConstraintValidator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;

/**
 * The model of one Polarion project as a plain EMF resource, behind {@link ModelGateway}. It runs the same
 * {@link ModelOperations} as the Sirius gateway, so tests need no Sirius Web context.
 */
final class InMemoryModel implements ModelGateway {

    private static final SysarchFactory FACTORY = SysarchFactory.eINSTANCE;

    final Resource resource = new ResourceImpl(URI.createURI("memory:/model.xmi"));

    final List<Map<String, Map<String, String>>> applied = new ArrayList<>();

    /** Projects whose views were asked to refresh, in order. */
    final List<String> refreshed = new ArrayList<>();

    private final String projectId;

    private final ConstraintValidator constraints;

    InMemoryModel(String projectId, ConstraintValidator constraints) {
        this.projectId = projectId;
        this.constraints = constraints;
    }

    private Map<String, ArchElement> index(String polarionProjectId) {
        if (!this.projectId.equals(polarionProjectId)) {
            throw new UnmappedProjectException(polarionProjectId);
        }
        return ModelOperations.index(this.resource.getAllContents());
    }

    @Override
    public Map<String, ElementSnapshot> read(String polarionProjectId, Collection<String> elementIds) {
        return ModelOperations.snapshots(this.index(polarionProjectId), elementIds, this.constraints);
    }

    @Override
    public Map<String, ElementSnapshot> readAll(String polarionProjectId) {
        return ModelOperations.snapshots(this.index(polarionProjectId), null, this.constraints);
    }

    @Override
    public void applyPolarionOwned(String polarionProjectId, Map<String, Map<String, String>> changes) {
        if (!changes.isEmpty()) {
            this.applied.add(changes);
            ModelOperations.applyPolarionOwned(this.index(polarionProjectId), changes);
        }
    }

    @Override
    public String repairConnection(String polarionProjectId, ConnectionRepair repair) {
        return ModelOperations.repairConnection(this.index(polarionProjectId), repair, this.constraints);
    }

    @Override
    public void refreshViews(String polarionProjectId) {
        this.refreshed.add(polarionProjectId);
    }

    PartDefinition block(String id, String name) {
        PartDefinition block = FACTORY.createPartDefinition();
        block.setId(id);
        block.setName(name);
        this.resource.getContents().add(block);
        return block;
    }

    Requirement requirement(String id, String name, String text) {
        Requirement requirement = FACTORY.createRequirement();
        requirement.setId(id);
        requirement.setName(name);
        requirement.setText(text);
        requirement.setLayer(Layer.LOGICAL);
        this.resource.getContents().add(requirement);
        return requirement;
    }

    /** A Connection between two new CAN ports (OUT to IN) of a new owner block. */
    Connection connection(String id, String sourceId, String targetId) {
        PartDefinition owner = this.block("PD-" + id, "owner of " + id);
        Port source = port(sourceId, PortDirection.OUT);
        Port target = port(targetId, PortDirection.IN);
        owner.getOwnedPorts().addAll(List.of(source, target));
        Connection connection = FACTORY.createConnection();
        connection.setId(id);
        connection.setName("bus");
        connection.setSource(source);
        connection.setTarget(target);
        this.resource.getContents().add(connection);
        return connection;
    }

    /** A port on an existing block. */
    Port port(PartDefinition owner, String id, PortDirection direction) {
        Port port = port(id, direction);
        owner.getOwnedPorts().add(port);
        return port;
    }

    Allocate allocate(String id, ArchElement from, ArchElement to) {
        Allocate allocate = FACTORY.createAllocate();
        allocate.setId(id);
        allocate.setName(id);
        allocate.setFrom(from);
        allocate.setTo(to);
        this.resource.getContents().add(allocate);
        return allocate;
    }

    private static Port port(String id, PortDirection direction) {
        Port port = FACTORY.createPort();
        port.setId(id);
        port.setName(id);
        port.setInterfaceType("CAN");
        port.setDirection(direction);
        return port;
    }
}

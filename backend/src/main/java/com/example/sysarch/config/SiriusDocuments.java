package com.example.sysarch.config;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.components.emf.ResourceMetadataAdapter;
import org.eclipse.sirius.components.emf.migration.MigrationService;
import org.eclipse.sirius.components.emf.migration.api.IMigrationParticipant;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.springframework.stereotype.Component;

/**
 * Creates a sysarch document in an editing context the way Sirius Web's own stereotypes do (a JSON resource with its
 * name and the current migration version), so Sirius saves it with the next semantic change. Used by the "new model"
 * choices ({@link SysarchStereotypes}) and for branch workspaces (P2 T3). Runs on the editing context's thread.
 */
@Component
public class SiriusDocuments {

    private final List<IMigrationParticipant> migrationParticipants;

    public SiriusDocuments(List<IMigrationParticipant> migrationParticipants) {
        this.migrationParticipants = Objects.requireNonNull(migrationParticipants);
    }

    /** A new document holding {@code contents}; returns its id. */
    public UUID create(IEMFEditingContext editingContext, String name, Collection<? extends EObject> contents) {
        UUID documentId = UUID.randomUUID();
        Resource resource = new JSONResourceFactory().createResourceFromPath(documentId.toString());
        ResourceMetadataAdapter metadata = new ResourceMetadataAdapter(name);
        metadata.addMigrationData(new MigrationService(this.migrationParticipants).getMostRecentParticipantMigrationData());
        resource.eAdapters().add(metadata);
        resource.getContents().addAll(contents);
        editingContext.getDomain().getResourceSet().getResources().add(resource);
        return documentId;
    }
}

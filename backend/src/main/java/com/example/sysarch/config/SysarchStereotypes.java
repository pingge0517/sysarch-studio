package com.example.sysarch.config;

import com.example.sysarch.demo.DomainControllerDemo;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.web.application.document.dto.DocumentDTO;
import org.eclipse.sirius.web.application.document.dto.Stereotype;
import org.eclipse.sirius.web.application.document.services.api.IStereotypeHandler;
import org.eclipse.sirius.web.application.document.services.api.IStereotypeProvider;
import org.eclipse.sirius.web.application.views.explorer.services.ExplorerDescriptionProvider;
import org.springframework.stereotype.Service;

/**
 * The "new model" choices in Sirius Web: an empty system-architecture model, and the domain-controller demo (T9). Without a stereotype Sirius Web
 * offers nothing in its "New model" dialog, so no sysarch document could be created. Elements are then
 * added through "New object" (PartDefinition, Requirement, ...), which Sirius derives from the registered
 * EPackage.
 */
public final class SysarchStereotypes {

    public static final String EMPTY_MODEL = "sysarch.empty";

    /** The T9 demo, {@link DomainControllerDemo}. */
    public static final String DEMO_MODEL = "sysarch.demo.domain-controller";

    private SysarchStereotypes() {
    }

    @Service
    public static class Provider implements IStereotypeProvider {

        @Override
        public List<Stereotype> getStereotypes(IEditingContext editingContext) {
            return List.of(new Stereotype(EMPTY_MODEL, "系统架构模型（空）"), new Stereotype(DEMO_MODEL, "系统架构模型（域控制器演示）"));
        }
    }

    /** Creates the document (empty, or with the demo content) the same way Sirius Web's own studio stereotypes do. */
    @Service
    public static class Handler implements IStereotypeHandler {

        private final SiriusDocuments documents;

        public Handler(SiriusDocuments documents) {
            this.documents = Objects.requireNonNull(documents);
        }

        @Override
        public boolean canHandle(IEditingContext editingContext, String stereotypeId) {
            return EMPTY_MODEL.equals(stereotypeId) || DEMO_MODEL.equals(stereotypeId);
        }

        @Override
        public Optional<DocumentDTO> handle(IEditingContext editingContext, String stereotypeId, String name) {
            if (!(editingContext instanceof IEMFEditingContext emfEditingContext)) {
                return Optional.empty();
            }
            UUID documentId = this.documents.create(emfEditingContext, name, DEMO_MODEL.equals(stereotypeId) ? DomainControllerDemo.build() : List.of());
            return Optional.of(new DocumentDTO(documentId, name, ExplorerDescriptionProvider.DOCUMENT_KIND));
        }
    }
}

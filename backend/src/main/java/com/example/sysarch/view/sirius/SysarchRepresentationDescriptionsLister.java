package com.example.sysarch.view.sirius;

import java.util.List;

import org.eclipse.sirius.components.collaborative.api.IRepresentationDescriptionsProvider;
import org.eclipse.sirius.components.collaborative.api.RepresentationDescriptionMetadata;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.representations.IRepresentationDescription;
import org.springframework.stereotype.Service;

/**
 * Lists our VSM diagrams under "New representation" (T4 step 2).
 *
 * <p>Sirius Web offers a representation description for an object only if its {@code canCreatePredicate} accepts
 * the object <i>and</i> some {@link IRepresentationDescriptionsProvider} claims the description
 * ({@code EditingContextRepresentationDescriptionsEventHandler} in sirius-components-emf). Without this class our
 * Java diagram descriptions are loaded but silently left out of the list. Found in the live check: only "Portal"
 * was offered for a PartDefinition.
 */
@Service
public class SysarchRepresentationDescriptionsLister implements IRepresentationDescriptionsProvider {

    @Override
    public boolean canHandle(IRepresentationDescription representationDescription) {
        return representationDescription.getId().startsWith(SiriusDiagramDescriptionFactory.ID_PREFIX);
    }

    @Override
    public List<RepresentationDescriptionMetadata> handle(IEditingContext editingContext, Object object, IRepresentationDescription representationDescription) {
        String label = representationDescription.getLabel();
        return List.of(new RepresentationDescriptionMetadata(representationDescription.getId(), label, label));
    }
}

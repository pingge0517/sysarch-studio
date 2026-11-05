package com.example.sysarch.view.sirius;

import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.view.generated.ViewpointRegistry;

import java.util.List;

import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextRepresentationDescriptionProvider;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.representations.IRepresentationDescription;
import org.springframework.stereotype.Service;

/**
 * Makes every VSM diagram (BDD, IBD, ...) available in Sirius Web (T4 step 2): Sirius collects these descriptions
 * when it loads an editing context, and offers each one under "New representation" for objects its
 * {@code canCreatePredicate} accepts (e.g. a PartDefinition).
 *
 * <p>The descriptions are built once from the generated {@link ViewpointRegistry}; they are immutable and read
 * the model only when a diagram is rendered.
 */
@Service
public class SysarchRepresentationDescriptionProvider implements IEditingContextRepresentationDescriptionProvider {

    private final List<IRepresentationDescription> descriptions;

    public SysarchRepresentationDescriptionProvider(ViewpointRegistry registry, IIdentityService identityService, ProjectionCache projections,
            DiagramFilterState filters, ConstraintValidator validator) {
        var factory = new SiriusDiagramDescriptionFactory(SysarchPackage.eINSTANCE, identityService, projections, filters, validator);
        this.descriptions = registry.diagramDescriptions().stream().<IRepresentationDescription>map(factory::create).toList();
    }

    @Override
    public List<IRepresentationDescription> getRepresentationDescriptions(IEditingContext editingContext) {
        return this.descriptions;
    }
}

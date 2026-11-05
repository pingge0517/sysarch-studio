package com.example.sysarch.view.sirius;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.collaborative.diagrams.api.IConnectorToolsProvider;
import org.eclipse.sirius.components.collaborative.diagrams.api.IPaletteProvider;
import org.eclipse.sirius.components.collaborative.diagrams.dto.Palette;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IRepresentationDescriptionSearchService;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.components.diagrams.description.DiagramDescription;
import org.eclipse.sirius.components.diagrams.description.IDiagramElementDescription;
import org.eclipse.sirius.components.diagrams.tools.ITool;
import org.eclipse.sirius.components.diagrams.tools.SingleClickOnDiagramElementTool;
import org.eclipse.sirius.components.diagrams.tools.SingleClickOnTwoDiagramElementsTool;
import org.springframework.stereotype.Service;

/**
 * Shows, for the element the user clicked, the tools of our diagram palette that apply to it. Sirius Web has no
 * default for Java diagram descriptions: without a provider no palette appears at all.
 *
 * <ul>
 * <li>Background: tools that apply to the diagram root (create-child into the root, filter toggles).</li>
 * <li>A node: create-child tools that target its description, and edge tools it can be the source of.</li>
 * </ul>
 */
@Service
public class SysarchPaletteProvider implements IPaletteProvider {

    @Override
    public boolean canHandle(DiagramDescription diagramDescription) {
        return diagramDescription.getId().startsWith(SiriusDiagramDescriptionFactory.ID_PREFIX);
    }

    @Override
    public Palette handle(IEditingContext editingContext, DiagramContext diagramContext, DiagramDescription diagramDescription, Object diagramElementDescription,
            Object diagramElement, Object targetElement) {
        String elementDescriptionId = diagramElementDescription instanceof IDiagramElementDescription description ? description.getId() : null;
        List<org.eclipse.sirius.components.collaborative.diagrams.dto.ITool> tools = allTools(diagramDescription)
                .filter(tool -> elementDescriptionId == null ? appliesToRoot(tool) : appliesTo(tool, elementDescriptionId))
                .map(SysarchPaletteProvider::toDto)
                .toList();
        String paletteId = diagramDescription.getId() + "/palette/" + Objects.requireNonNullElse(elementDescriptionId, "diagram");
        return new Palette(paletteId, tools, List.of());
    }

    /** The palette sent to the frontend uses DTO tools; the description keeps the executable ones (with handlers). */
    private static org.eclipse.sirius.components.collaborative.diagrams.dto.ITool toDto(ITool tool) {
        if (tool instanceof SingleClickOnTwoDiagramElementsTool edge) {
            var candidates = edge.getCandidates().stream()
                    .map(c -> new org.eclipse.sirius.components.collaborative.diagrams.dto.SingleClickOnTwoDiagramElementsCandidate(c.getSources(), c.getTargets()))
                    .toList();
            return new org.eclipse.sirius.components.collaborative.diagrams.dto.SingleClickOnTwoDiagramElementsTool(edge.getId(), edge.getLabel(),
                    edge.getIconURL(), candidates, null);
        }
        SingleClickOnDiagramElementTool click = (SingleClickOnDiagramElementTool) tool;
        return new org.eclipse.sirius.components.collaborative.diagrams.dto.SingleClickOnDiagramElementTool(click.getId(), click.getLabel(), click.getIconURL(),
                click.getTargetDescriptions(), null, click.isAppliesToDiagramRoot(), false);
    }

    static Stream<ITool> allTools(DiagramDescription diagramDescription) {
        return diagramDescription.getPalettes().stream().flatMap(palette -> palette.getTools().stream());
    }

    private static boolean appliesToRoot(ITool tool) {
        return tool instanceof SingleClickOnDiagramElementTool click && click.isAppliesToDiagramRoot();
    }

    private static boolean appliesTo(ITool tool, String elementDescriptionId) {
        if (tool instanceof SingleClickOnDiagramElementTool click) {
            return click.getTargetDescriptions().stream().anyMatch(d -> d.getId().equals(elementDescriptionId));
        }
        if (tool instanceof SingleClickOnTwoDiagramElementsTool edge) {
            return edge.getCandidates().stream().flatMap(c -> c.getSources().stream()).anyMatch(d -> d.getId().equals(elementDescriptionId));
        }
        return false;
    }

    /**
     * Edge tools offered when the user drags from one node to another on the canvas: those whose candidates accept
     * the source and target node descriptions (IBD: port to port).
     */
    @Service
    public static class ConnectorTools implements IConnectorToolsProvider {

        private final IRepresentationDescriptionSearchService descriptions;

        public ConnectorTools(IRepresentationDescriptionSearchService descriptions) {
            this.descriptions = Objects.requireNonNull(descriptions);
        }

        @Override
        public boolean canHandle(DiagramDescription diagramDescription) {
            return diagramDescription.getId().startsWith(SiriusDiagramDescriptionFactory.ID_PREFIX);
        }

        @Override
        public List<ITool> getConnectorTools(Object sourceDiagramElement, Object targetDiagramElement, Diagram diagram, IEditingContext editingContext) {
            if (!(sourceDiagramElement instanceof Node source) || !(targetDiagramElement instanceof Node target)) {
                return List.of();
            }
            return this.descriptions.findById(editingContext, diagram.getDescriptionId())
                    .filter(DiagramDescription.class::isInstance)
                    .map(DiagramDescription.class::cast)
                    .stream()
                    .flatMap(SysarchPaletteProvider::allTools)
                    .filter(SingleClickOnTwoDiagramElementsTool.class::isInstance)
                    .map(SingleClickOnTwoDiagramElementsTool.class::cast)
                    .filter(tool -> tool.getCandidates().stream().anyMatch(c -> contains(c.getSources(), source) && contains(c.getTargets(), target)))
                    .<ITool>map(tool -> tool)
                    .toList();
        }

        private static boolean contains(List<IDiagramElementDescription> descriptions, Node node) {
            return descriptions.stream().anyMatch(d -> d.getId().equals(node.getDescriptionId()));
        }
    }
}

package com.example.sysarch.storage.polarion;

import com.example.sysarch.ElementIds;
import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.storage.ModelCodec;
import com.example.sysarch.view.api.DiagramDescription;
import com.example.sysarch.view.api.TableDescription;
import com.example.sysarch.view.generated.*;
import com.example.sysarch.view.sirius.*;
import com.example.sysarch.view.table.*;
import java.util.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.components.DiagramComponent;
import org.eclipse.sirius.components.diagrams.components.DiagramComponentProps;
import org.eclipse.sirius.components.diagrams.renderer.DiagramRenderer;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.representations.Element;
import org.eclipse.sirius.components.representations.VariableManager;

/** Pure rendering of authorized, detached decoded objects. No editing context, persistent cache or mutation dispatch. */
public final class PolarionReadViews {
    public record Description(String id,String label,String kind,String rootType) { }
    public record Rendered(String viewId,UUID rootUuid,Diagram diagram,Matrix matrix) { }
    private final List<DiagramDescription> diagrams=List.of(new BddDiagramDescriptionProvider().build(),new IbdDiagramDescriptionProvider().build(),
        new SaFunctionChainDiagramDescriptionProvider().build(),new LaArchitectureDiagramDescriptionProvider().build(),new PaArchitectureDiagramDescriptionProvider().build());
    private final List<TableDescription> tables=List.of(new AllocationMatrixTableDescriptionProvider().build(),new RequirementTraceTableDescriptionProvider().build());

    public List<Description> descriptions() {
        List<Description> result=new ArrayList<>();
        diagrams.forEach(view->result.add(new Description(view.id(),view.label(),"DIAGRAM",view.domainType().substring(view.domainType().indexOf("::")+2))));
        tables.forEach(view->result.add(new Description(view.id(),view.label(),"TABLE",null)));
        return List.copyOf(result);
    }
    public Rendered render(ModelCodec.Decoded model,String viewId,UUID rootUuid) {
        var recorder=new org.eclipse.emf.ecore.change.util.ChangeRecorder(model.documents().stream().map(ModelCodec.SourceDocument::resource).toList());
        try { return renderBound(model,viewId,rootUuid); }
        finally {
            var changes=recorder.endRecording();recorder.dispose();
            if(!changes.getObjectChanges().isEmpty() || !changes.getResourceChanges().isEmpty()
                || !changes.getObjectsToAttach().isEmpty() || !changes.getObjectsToDetach().isEmpty()) {
                changes.applyAndReverse();throw new IllegalStateException("A read projection attempted to change model semantics");
            }
        }
    }
    private Rendered renderBound(ModelCodec.Decoded model,String viewId,UUID rootUuid) {
        Map<UUID,EObject> objects=new HashMap<>();
        for(var document:model.documents()) for(var it=document.resource().getAllContents();it.hasNext();) {
            EObject object=it.next(); UUID uuid=model.identities().id(object);
            if(objects.put(uuid,object)!=null) throw new IllegalArgumentException("Duplicate projection UUID");
            ElementIds.bindProjectionId(object,uuid);
        }
        Optional<TableDescription> table=tables.stream().filter(view->view.id().equals(viewId)).findFirst();
        if(table.isPresent()) {
            if(rootUuid!=null) throw new IllegalArgumentException("Table projections do not select a diagram root");
            Matrix source=MatrixProjector.project(table.get(),model.documents().stream().map(ModelCodec.SourceDocument::resource).toList());
            Matrix readOnly=new Matrix(source.tableId(),source.label(),source.markSymbol(),source.markColor(),source.groups(),source.rows(),source.columns(),
                source.cells().stream().map(cell->new Matrix.Cell(cell.rowId(),cell.columnId(),List.of())).toList(),List.of());
            return new Rendered(viewId,null,null,readOnly);
        }
        var descriptor=diagrams.stream().filter(view->view.id().equals(viewId)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown read view"));
        EObject root=objects.get(rootUuid);
        String rootType=descriptor.domainType().substring(descriptor.domainType().indexOf("::")+2);
        if(root==null || !SysarchPackage.eINSTANCE.getEClassifier(rootType).isInstance(root)) throw new IllegalArgumentException("Invalid diagram root UUID or type");
        IIdentityService identities=new IIdentityService() {
            @Override public String getId(Object object) { return object instanceof EObject element?ElementIds.of(element):null; }
            @Override @SuppressWarnings("removal") public String getKind(Object object) { return object instanceof EObject element?"sysarch::"+element.eClass().getName():""; }
        };
        // Per-request services avoid sharing mutable projections, filters or authorized bodies between callers.
        var factory=SiriusDiagramDescriptionFactory.readOnly(SysarchPackage.eINSTANCE,identities,new ProjectionCache(),new DiagramFilterState());
        var description=factory.create(descriptor);
        var variables=new VariableManager();variables.put(VariableManager.SELF,root);variables.put(DiagramContext.DIAGRAM_CONTEXT,null);
        var props=DiagramComponentProps.newDiagramComponentProps().variableManager(variables).diagramDescription(description).allDiagramDescriptions(List.of(description))
            // This Sirius hook validates read providers (e.g. Node#semanticCandidates), not edit dispatch.
            .operationValidator((operation,scope)->{})
            .viewCreationRequests(List.of()).viewDeletionRequests(List.of()).previousDiagram(Optional.empty()).diagramEvents(List.of())
            .nodeAppearanceHandlers(List.of()).edgeAppearanceHandlers(List.of()).build();
        Diagram rendered=new DiagramRenderer().render(new Element(DiagramComponent.class,props));
        return new Rendered(viewId,rootUuid,rendered,null);
    }
}

package com.example.sysarch.storage.polarion;

import static org.junit.jupiter.api.Assertions.*;
import com.example.sysarch.ElementIds;
import com.example.sysarch.model.*;
import com.example.sysarch.storage.*;
import java.util.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.sirius.components.diagrams.Node;
import org.junit.jupiter.api.Test;

class PolarionReadViewsTest {
    private final ModelCodec codec=new ModelCodec();
    private final PolarionReadViews views=new PolarionReadViews();
    private final UUID model=UUID.randomUUID();
    private ModelCodec.Decoded fixture() {
        var f=SysarchFactory.eINSTANCE;var resource=new ResourceImpl();
        var system=f.createPartDefinition();system.setId("DUPLICATE");system.setName("System");system.setLayer(Layer.SYSTEM);
        var logical=f.createPartDefinition();logical.setId("DUPLICATE");logical.setName("Logical");logical.setLayer(Layer.LOGICAL);
        var physical=f.createPartDefinition();physical.setId("DUPLICATE");physical.setName("Physical");physical.setLayer(Layer.PHYSICAL);
        var part=f.createPart();part.setId("PART");part.setName("Component");part.setTypedBy(logical);system.getOwnedParts().add(part);
        var a=f.createAllocate();a.setId("A1");a.setFrom(system);a.setTo(logical);
        var b=f.createAllocate();b.setId("A2");b.setFrom(logical);b.setTo(physical);
        var req=f.createRequirement();req.setId("R1");req.setName("Requirement");req.getSatisfiedBy().add(logical);
        resource.getContents().addAll(List.of(system,logical,physical,a,b,req));
        byte[] bytes=codec.encode(codec.capture(model,List.of(new ModelCodec.SourceDocument(UUID.randomUUID(),resource)),new ModelIdentityRegistry()));
        return codec.decode(bytes,ModelCodec.sha256(bytes));
    }
    private UUID root(ModelCodec.Decoded decoded,String name) {
        for(var document:decoded.documents()) for(var it=document.resource().getAllContents();it.hasNext();) {
            var object=it.next();if(object instanceof ArchElement element && name.equals(element.getName()))return decoded.identities().id(object);
        }
        throw new AssertionError("Missing fixture root");
    }
    private List<Node> nodes(List<Node> source) {
        List<Node> all=new ArrayList<>();for(Node node:source){all.add(node);all.addAll(nodes(node.getChildNodes()));all.addAll(nodes(node.getBorderNodes()));}return all;
    }
    @Test void siriusBddKeepsDistinctUuidsDespiteDuplicateBusinessIds() throws Exception {
        var decoded=fixture();var rendered=views.render(decoded,"bdd",root(decoded,"System"));
        var targets=nodes(rendered.diagram().getNodes()).stream().map(Node::getTargetObjectId).toList();
        for(String name:List.of("System","Logical","Physical"))assertTrue(targets.contains(root(decoded,name).toString()));
        assertEquals(targets.size(),new HashSet<>(targets).size());
        var json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(rendered);
        assertTrue(json.contains(root(decoded,"Logical").toString()));
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/polarion-read-view-fixture.json"),json);
    }
    @Test void layerViewsUseExistingVsmFiltersAndIbdUsesContainment() {
        var decoded=fixture();UUID system=root(decoded,"System");
        for(var pair:Map.of("sa-function-chain","System","la-architecture","Logical","pa-architecture","Physical").entrySet()) {
            var diagram=views.render(decoded,pair.getKey(),system).diagram();
            var targets=nodes(diagram.getNodes()).stream().map(Node::getTargetObjectId).toList();
            assertTrue(targets.contains(root(decoded,pair.getValue()).toString()));
        }
        assertTrue(nodes(views.render(decoded,"ibd",system).diagram().getNodes()).stream().anyMatch(node->root(decoded,"Component").toString().equals(node.getTargetObjectId())));
    }
    @Test void matricesUseUuidHeadersAndExposeNoEditActions() {
        var decoded=fixture();var matrix=views.render(decoded,"allocation-matrix",null).matrix();
        assertEquals(2,matrix.cells().size());assertTrue(matrix.actions().isEmpty());assertTrue(matrix.cells().stream().allMatch(cell->cell.links().isEmpty()));
        assertTrue(matrix.rows().stream().anyMatch(row->row.id().equals(root(decoded,"System").toString())));
        assertEquals(1,views.render(decoded,"requirement-trace",null).matrix().cells().size());
    }
    @Test void projectionsDoNotChangeSemanticAttributesOrSnapshotHashes() {
        var decoded=fixture();byte[] before=codec.encode(codec.capture(model,decoded.documents(),decoded.identities()));
        for(var view:views.descriptions())views.render(decoded,view.id(),view.kind().equals("DIAGRAM")?root(decoded,"System"):null);
        assertArrayEquals(before,codec.encode(codec.capture(model,decoded.documents(),decoded.identities())));
        var first=(PartDefinition)decoded.documents().getFirst().resource().getContents().getFirst();assertEquals("DUPLICATE",first.getId());
    }
    @Test void unknownViewsAndForeignOrWrongTypeRootsFailExplicitly() {
        var decoded=fixture();
        assertThrows(IllegalArgumentException.class,()->views.render(decoded,"unknown",null));
        assertThrows(IllegalArgumentException.class,()->views.render(decoded,"bdd",UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,()->views.render(decoded,"bdd",root(decoded,"Requirement")));
        assertThrows(IllegalArgumentException.class,()->views.render(decoded,"allocation-matrix",root(decoded,"System")));
    }
    @Test void localObjectsKeepExistingIdsAndProjectionBindingsCannotBeReassigned() {
        var local=SysarchFactory.eINSTANCE.createPartDefinition();local.setId("LOCAL-1");assertEquals("LOCAL-1",ElementIds.of(local));
        UUID id=UUID.randomUUID();ElementIds.bindProjectionId(local,id);assertEquals(id.toString(),ElementIds.of(local));
        assertEquals("LOCAL-1",local.getId());assertThrows(IllegalArgumentException.class,()->ElementIds.bindProjectionId(local,UUID.randomUUID()));
    }
    @Test void readDescriptionsHaveNoPaletteAndCannotRenameOrDelete() {
        var identities=org.mockito.Mockito.mock(org.eclipse.sirius.components.core.api.IIdentityService.class);
        var factory=com.example.sysarch.view.sirius.SiriusDiagramDescriptionFactory.readOnly(SysarchPackage.eINSTANCE,identities,
            new com.example.sysarch.view.sirius.ProjectionCache(),new com.example.sysarch.view.sirius.DiagramFilterState());
        var description=factory.create(new com.example.sysarch.view.generated.BddDiagramDescriptionProvider().build());
        assertTrue(description.getPalettes().isEmpty());
        var variables=new org.eclipse.sirius.components.representations.VariableManager();
        assertInstanceOf(org.eclipse.sirius.components.representations.Failure.class,description.getNodeDescriptions().getFirst().getDeleteHandler().apply(variables));
        assertInstanceOf(org.eclipse.sirius.components.representations.Failure.class,description.getNodeDescriptions().getFirst().getLabelEditHandler().apply(variables,"new label"));
    }
    @Test void readRenderingDoesNotInterfereWithAnExistingOclValidator() {
        var decoded=fixture();var root=(PartDefinition)decoded.documents().getFirst().resource().getContents().getFirst();
        try(var validator=new com.example.sysarch.validation.ConstraintValidator()) {
            assertDoesNotThrow(()->validator.validate(root));
            for(var view:views.descriptions()) views.render(decoded,view.id(),view.kind().equals("DIAGRAM")?root(decoded,"System"):null);
            assertDoesNotThrow(()->validator.validate(root));
        }
    }
}

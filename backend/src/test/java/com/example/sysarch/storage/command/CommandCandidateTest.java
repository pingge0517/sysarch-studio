package com.example.sysarch.storage.command;

import static org.junit.jupiter.api.Assertions.*;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.storage.ModelCodec;
import com.example.sysarch.storage.ModelIdentityRegistry;
import com.example.sysarch.storage.StoredModel;
import com.example.sysarch.validation.ConstraintValidator;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommandCandidateTest {
    private static ConstraintValidator constraints;
    private final ModelCodec codec=new ModelCodec();
    private final UUID model=UUID.randomUUID();
    private UUID element;
    private StoredModel committed;
    @BeforeAll static void validation() {constraints=new ConstraintValidator();}
    @AfterAll static void closeValidation() {constraints.close();}
    @BeforeEach void setup() {
        var resource=new ResourceImpl(); var system=SysarchFactory.eINSTANCE.createPartDefinition();system.setId("BUSINESS");system.setName("System");resource.getContents().add(system);
        var identities=new ModelIdentityRegistry();element=identities.id(system);
        committed=codec.capture(model,List.of(new ModelCodec.SourceDocument(UUID.randomUUID(),resource)),identities);
    }
    private ModelCommandProtocol.BoundCommand command(UUID target,String kind,String fields) {
        return new ModelCommandProtocol().bind(model,"verified-fixture",("{\"commandId\":\""+UUID.randomUUID()+"\",\"expectedVersion\":3,\"schemaVersion\":\"1.0\",\"operations\":[{\"kind\":\""+kind+"\",\"elementUuid\":\""+target+"\","+fields+"}]}").getBytes(StandardCharsets.UTF_8));
    }
    @Test void renameIsIsolatedAndKeepsStableUuidBusinessIdAndDocumentOrder() {
        byte[] before=codec.encode(committed);
        var candidate=new CommandCandidate(codec,constraints).prepare(committed,3,Set.of(),command(element,"RenameElement","\"name\":\"电机\""));
        assertArrayEquals(before,codec.encode(committed)); assertNotEquals(ModelCodec.sha256(before),ModelCodec.sha256(codec.encode(candidate)));
        assertEquals(committed.documents(),candidate.documents()); assertEquals(element,candidate.nodes().getFirst().uuid());
        assertEquals(List.of("电机"),candidate.nodes().getFirst().attributes().get("name"));assertEquals(List.of("BUSINESS"),candidate.nodes().getFirst().attributes().get("id"));
    }
    @Test void staleVersionWrongModelAndUnknownTargetsCannotPrepareACandidate() {
        var engine=new CommandCandidate(codec,constraints);var rename=command(element,"RenameElement","\"name\":\"Next\"");
        assertThrows(CommandCandidate.VersionConflict.class,()->engine.prepare(committed,4,Set.of(),rename));
        assertThrows(CommandCandidate.Rejected.class,()->engine.prepare(new StoredModel(committed.schemaVersion(),UUID.randomUUID(),committed.documents(),committed.nodes()),3,Set.of(),rename));
        assertThrows(CommandCandidate.Rejected.class,()->engine.prepare(committed,3,Set.of(),command(UUID.randomUUID(),"RenameElement","\"name\":\"Next\"")));
    }
    @Test void invalidEnumsUnknownAttributesReferenceEditsAndOclViolationsAreRejected() {
        var engine=new CommandCandidate(codec,constraints);byte[] before=codec.encode(committed);
        for(String fields:List.of("\"attribute\":\"layer\",\"values\":[\"UNKNOWN\"]","\"attribute\":\"unknown\",\"values\":[\"x\"]",
            "\"attribute\":\"ownedParts\",\"values\":[\"x\"]","\"attribute\":\"name\",\"values\":[\"\"]","\"attribute\":\"name\",\"values\":[]"))
            assertThrows(CommandCandidate.Rejected.class,()->engine.prepare(committed,3,Set.of(),command(element,"SetAttribute",fields)));
        assertArrayEquals(before,codec.encode(committed));
    }
    @Test void externalRequirementsCannotBeRenamedOrHaveAttributesChanged() {
        var resource=new ResourceImpl();var requirement=SysarchFactory.eINSTANCE.createRequirement();requirement.setId("EXT");requirement.setName("Requirement reference");resource.getContents().add(requirement);
        var identities=new ModelIdentityRegistry();UUID target=identities.id(requirement);
        var source=codec.capture(model,List.of(new ModelCodec.SourceDocument(UUID.randomUUID(),resource)),identities);
        var engine=new CommandCandidate(codec,constraints);
        assertThrows(CommandCandidate.Rejected.class,()->engine.prepare(source,3,Set.of(target),command(target,"RenameElement","\"name\":\"Changed\"")));
        assertThrows(CommandCandidate.Rejected.class,()->engine.prepare(source,3,Set.of(target),command(target,"SetAttribute","\"attribute\":\"text\",\"values\":[\"body\"]")));
    }
    @Test void attributesUseCanonicalEmfLiteralsAndCanBeUnset() {
        var engine=new CommandCandidate(codec,constraints);
        var candidate=engine.prepare(committed,3,Set.of(),command(element,"SetAttribute","\"attribute\":\"layer\",\"values\":[\"SYSTEM\"]"));
        assertEquals(List.of("SYSTEM"),candidate.nodes().getFirst().attributes().get("layer"));
        candidate=engine.prepare(candidate,3,Set.of(),command(element,"SetAttribute","\"attribute\":\"layer\",\"values\":[]"));
        assertFalse(candidate.nodes().getFirst().attributes().containsKey("layer"));
    }
    @Test void enrichedExternalBodyCannotBeRepersistedEvenWhenEditingAnotherElement() {
        var reference=SysarchFactory.eINSTANCE.createRequirement();reference.setId("EXT");reference.setName("Requirement reference");reference.setText("restricted body");
        var resource=new ResourceImpl();resource.getContents().add(reference);var ids=new ModelIdentityRegistry();UUID external=ids.id(reference);
        var captured=codec.capture(model,List.of(new ModelCodec.SourceDocument(UUID.randomUUID(),resource)),ids);
        var nodes=new java.util.ArrayList<>(committed.nodes());nodes.addAll(captured.nodes());var docs=new java.util.ArrayList<>(committed.documents());docs.addAll(captured.documents());
        var unsafe=new StoredModel("1.0",model,docs,nodes);
        assertThrows(CommandCandidate.Rejected.class,()->new CommandCandidate(codec,constraints).prepare(unsafe,3,Set.of(external),command(element,"RenameElement","\"name\":\"Next\"")));
    }
    @Test void aLaterInvalidOperationCannotLeaveAnEarlierChangeInTheCommittedInput() {
        var first=command(element,"RenameElement","\"name\":\"Next\"");
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        byte[] input;
        try {
            input=json.writeValueAsBytes(java.util.Map.of("commandId",first.body().commandId(),"expectedVersion",3,"schemaVersion","1.0","operations",List.of(
                java.util.Map.of("kind","RenameElement","elementUuid",element,"name","Next"),
                java.util.Map.of("kind","SetAttribute","elementUuid",element,"attribute","missing","values",List.of("invalid")))));
        } catch(Exception failure) {throw new AssertionError(failure);}
        var bound=new ModelCommandProtocol().bind(model,"verified-fixture",input);byte[] before=codec.encode(committed);
        assertThrows(CommandCandidate.Rejected.class,()->new CommandCandidate(codec,constraints).prepare(committed,3,Set.of(),bound));
        assertArrayEquals(before,codec.encode(committed));
    }
}

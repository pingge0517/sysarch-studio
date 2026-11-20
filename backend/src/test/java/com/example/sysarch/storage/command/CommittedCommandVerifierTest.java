package com.example.sysarch.storage.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.example.sysarch.storage.command.CommittedCommandVerifier.*;

class CommittedCommandVerifierTest {
    private final ModelCommandProtocol.BoundCommand command=new ModelCommandProtocol().bind(UUID.randomUUID(),"verified-fixture",("{\"commandId\":\""+UUID.randomUUID()+"\",\"expectedVersion\":3,\"schemaVersion\":\"1.0\",\"operations\":[{\"kind\":\"RenameElement\",\"elementUuid\":\""+UUID.randomUUID()+"\",\"name\":\"Next\"}]}").getBytes(StandardCharsets.UTF_8));
    private Receipt receipt(String actor,String hash,UUID model,UUID id,long expected) {return new Receipt("1.0",model,id,actor,hash,expected,expected+1,UUID.randomUUID(),"a".repeat(64));}
    private Receipt receipt() {return receipt(command.actor(),command.requestHash(),command.modelUuid(),command.body().commandId(),3);}
    private ReceiptReader reader(Optional<Receipt> receipt) throws Exception {
        var reader=mock(ReceiptReader.class);when(reader.verifiedActor()).thenReturn(command.actor());when(reader.read(command.modelUuid(),command.body().commandId())).thenReturn(receipt);return reader;
    }
    private DurableCommandGate.Ticket ticket() {return new DurableCommandGate.Ticket(command.gateRequest(),UUID.randomUUID(),UUID.randomUUID());}
    @Test void matchingReceiptReplaysOriginalResultAndReleasesOnlyAsCommitted() throws Exception {
        var original=receipt();var verifier=new CommittedCommandVerifier(command,reader(Optional.of(original)));
        assertEquals(original,verifier.replay().orElseThrow());assertEquals(DurableCommandGate.Outcome.COMMITTED,verifier.verify(ticket()));
    }
    @Test void missingReceiptIsNotFoundNeverRolledBack() throws Exception {
        assertEquals(DurableCommandGate.Outcome.NOT_FOUND,new CommittedCommandVerifier(command,reader(Optional.empty())).verify(ticket()));
    }
    @Test void commandIdModelHashAndExpectedVersionMismatchesRejectReplay() throws Exception {
        for(Receipt wrong:java.util.List.of(receipt(command.actor(),"b".repeat(64),command.modelUuid(),command.body().commandId(),3),
            receipt(command.actor(),command.requestHash(),UUID.randomUUID(),command.body().commandId(),3),
            receipt(command.actor(),command.requestHash(),command.modelUuid(),UUID.randomUUID(),3),
            receipt(command.actor(),command.requestHash(),command.modelUuid(),command.body().commandId(),4))) {
            var verifier=new CommittedCommandVerifier(command,reader(Optional.of(wrong)));
            assertThrows(Conflict.class,verifier::replay);
        }
    }
    @Test void wrongCallerIsRejectedBeforeAnyRemoteReadAndIdentityChangesAfterReadAreRejected() throws Exception {
        var reader=reader(Optional.of(receipt()));when(reader.verifiedActor()).thenReturn("other");
        assertThrows(Denied.class,new CommittedCommandVerifier(command,reader)::replay);verify(reader,never()).read(any(),any());
        when(reader.verifiedActor()).thenReturn(command.actor(),"other");
        assertThrows(Denied.class,new CommittedCommandVerifier(command,reader)::replay);
        assertThrows(Denied.class,new CommittedCommandVerifier(command,reader(Optional.of(receipt("other",command.requestHash(),command.modelUuid(),command.body().commandId(),3))))::replay);
    }
    @Test void wrongTicketDoesNotCallTheReaderAndTransportFailureIsNotTreatedAsSuccess() throws Exception {
        var reader=reader(Optional.of(receipt()));var verifier=new CommittedCommandVerifier(command,reader);
        var wrong=new DurableCommandGate.Ticket(new DurableCommandGate.Request(UUID.randomUUID(),command.body().commandId(),command.requestHash(),command.actor()),UUID.randomUUID(),UUID.randomUUID());
        assertThrows(Conflict.class,()->verifier.verify(wrong));verify(reader,never()).read(any(),any());
        when(reader.read(any(),any())).thenThrow(new IllegalStateException("fixture unavailable"));assertThrows(IllegalStateException.class,()->verifier.verify(ticket()));
    }
    @Test void invalidReceiptVersionAndDigestAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new Receipt("1.0",command.modelUuid(),command.body().commandId(),command.actor(),command.requestHash(),3,5,UUID.randomUUID(),"a".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->new Receipt("1.0",command.modelUuid(),command.body().commandId(),command.actor(),command.requestHash(),3,4,UUID.randomUUID(),"invalid"));
    }
}

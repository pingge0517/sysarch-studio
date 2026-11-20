package com.example.sysarch.storage.command;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelCommandProtocolTest {
    private final ModelCommandProtocol protocol=new ModelCommandProtocol();
    private final UUID model=UUID.randomUUID(),command=UUID.randomUUID(),element=UUID.randomUUID();
    private String operation(String name) {return "{\"kind\":\"RenameElement\",\"elementUuid\":\""+element+"\",\"name\":\""+name+"\"}";}
    private String body(String operations) {return "{\"commandId\":\""+command+"\",\"expectedVersion\":7,\"schemaVersion\":\"1.0\",\"operations\":["+operations+"]}";}
    private ModelCommandProtocol.BoundCommand bind(String text) {return protocol.bind(model,"verified-fixture",text.getBytes(StandardCharsets.UTF_8));}
    @Test void fieldOrderWhitespaceAndUnicodeEscapingDoNotChangeCanonicalHash() {
        var first=bind(body(operation("电机")));
        String reordered="{ \"operations\": ["+operation("\\u7535\\u673a")+"], \"schemaVersion\":\"1.0\",\"expectedVersion\":7,\"commandId\":\""+command+"\" }";
        assertEquals(first.requestHash(),bind(reordered).requestHash());
        assertEquals(first.requestHash(),first.gateRequest().requestHash());
        assertFalse(first.toString().contains("电机"));
    }
    @Test void identityScopeVersionOrderAndTextAreBoundToTheDigest() {
        String body=body(operation("First")+","+operation("Second")); var bound=bind(body);
        for(String changed:List.of(body.replace("expectedVersion\":7","expectedVersion\":8"),body.replace("First","Different"),body(operation("Second")+","+operation("First"))))
            assertNotEquals(bound.requestHash(),bind(changed).requestHash());
        assertNotEquals(bound.requestHash(),protocol.bind(UUID.randomUUID(),"verified-fixture",body.getBytes(StandardCharsets.UTF_8)).requestHash());
        assertNotEquals(bound.requestHash(),protocol.bind(model,"another-verified-user",body.getBytes(StandardCharsets.UTF_8)).requestHash());
    }
    @Test void unknownFieldsCredentialsTypesAndUnsupportedOperationsAreRejectedWithoutEcho() {
        String valid=body(operation("Safe"));
        for(String invalid:List.of(valid.replace("\"expectedVersion\":7","\"expectedVersion\":7,\"actorId\":\"admin\""),
            valid.replace("\"name\":\"Safe\"","\"name\":\"Safe\",\"token\":\"fixture-secret-value\""),
            valid.replace("RenameElement","DeleteElement"),valid.replace("RenameElement","java.lang.Runtime"))) {
            var error=assertThrows(ModelCommandProtocol.InvalidCommand.class,()->bind(invalid));
            assertNull(error.getCause());assertFalse(error.toString().contains("fixture-secret-value"));
        }
    }
    @Test void duplicatesTrailingDocumentsMissingFieldsAndScalarCoercionAreRejected() {
        String valid=body(operation("Safe"));
        for(String invalid:List.of(valid+" {}",valid.replace("\"expectedVersion\":7","\"expectedVersion\":7,\"expectedVersion\":8"),
            valid.replace("\"expectedVersion\":7,",""),valid.replace("\"expectedVersion\":7","\"expectedVersion\":\"7\""),
            valid.replace("\"expectedVersion\":7","\"expectedVersion\":7.5"),valid.replace("\"name\":\"Safe\"","\"name\":12"),
            valid.replace("\"expectedVersion\":7","\"expectedVersion\":null"),
            valid.replace("\"name\":\"Safe\"","\"name\":null"))) assertThrows(ModelCommandProtocol.InvalidCommand.class,()->bind(invalid));
    }
    @Test void uuidShapeVersionRangeAndOperationBoundsAreStrict() {
        String valid=body(operation("Safe"));
        for(String invalid:List.of(valid.replace(element.toString(),"1-1-1-1-1"),valid.replace(command.toString(),"not-a-uuid"),
            valid.replace("\"expectedVersion\":7","\"expectedVersion\":-1"),valid.replace("\"expectedVersion\":7","\"expectedVersion\":9223372036854775807"),
            valid.replace("\"1.0\"","\"2.0\""),body(""),body(String.join(",",java.util.Collections.nCopies(257,operation("Safe"))))))
            assertThrows(ModelCommandProtocol.InvalidCommand.class,()->bind(invalid));
        assertThrows(ModelCommandProtocol.InvalidCommand.class,()->protocol.bind(model,"verified-fixture",new byte[ModelCommandProtocol.MAX_BYTES+1]));
    }
    @Test void deserializedOperationListsCannotBeMutated() {
        var bound=bind(body(operation("Safe")));
        assertThrows(UnsupportedOperationException.class,()->bound.body().operations().clear());
        assertThrows(ModelCommandProtocol.InvalidCommand.class,()->protocol.bind(model,"bad\nactor",body(operation("Safe")).getBytes(StandardCharsets.UTF_8)));
    }
}

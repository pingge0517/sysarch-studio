package com.example.sysarch.storage.command;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommandReceiptCodecTest {
    private final CommandReceiptCodec codec=new CommandReceiptCodec();
    private final CommittedCommandVerifier.Receipt receipt=new CommittedCommandVerifier.Receipt("1.0",UUID.randomUUID(),UUID.randomUUID(),"alice","a".repeat(64),3,4,UUID.randomUUID(),"b".repeat(64));
    private String encoded() throws Exception {return new ObjectMapper().writeValueAsString(receipt);}
    @Test void roundTripsBoundedReceiptMetadata() throws Exception {assertEquals(receipt,codec.read(encoded().getBytes(StandardCharsets.UTF_8)));}
    @Test void rejectsDuplicateUnknownTrailingAndCoercedFieldsWithoutEcho() throws Exception {
        String body=encoded();
        for(String invalid:List.of(body+" {}",body.replace("\"version\":4","\"version\":4,\"version\":5"),body.replace("\"version\":4","\"version\":null"),
            body.replace("\"version\":4","\"version\":\"4\""),body.replace("\"version\":4","\"version\":4.5"),
            body.replace("\"actor\":\"alice\"","\"actor\":123"),body.replace("\"actor\":\"alice\"","\"actor\":\"alice\",\"token\":\"fixture-secret\""))) {
            var failure=assertThrows(CommandReceiptCodec.InvalidReceipt.class,()->codec.read(invalid.getBytes(StandardCharsets.UTF_8)));
            assertNull(failure.getCause());assertFalse(failure.toString().contains("fixture-secret"));
        }
    }
    @Test void rejectsNoncanonicalIdentitiesUnsupportedSchemaAndInvalidVersion() throws Exception {
        String body=encoded();
        for(String invalid:List.of(body.replace(receipt.commandId().toString(),"1-1-1-1-1"),body.replace("\"schemaVersion\":\"1.0\"","\"schemaVersion\":\"2.0\""),body.replace("\"version\":4","\"version\":5")))
            assertThrows(CommandReceiptCodec.InvalidReceipt.class,()->codec.read(invalid.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void rejectsEmptyAndOversizedDocuments() {
        assertThrows(CommandReceiptCodec.InvalidReceipt.class,()->codec.read(new byte[0]));
        assertThrows(CommandReceiptCodec.InvalidReceipt.class,()->codec.read(new byte[CommandReceiptCodec.MAX_BYTES+1]));
    }
}

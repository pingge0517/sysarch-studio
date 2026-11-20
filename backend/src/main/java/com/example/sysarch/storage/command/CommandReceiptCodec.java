package com.example.sysarch.storage.command;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;

/** Bounded metadata only. Parse errors never include source excerpts. */
public final class CommandReceiptCodec {
    public static final int MAX_BYTES=64*1024;
    private final com.fasterxml.jackson.databind.ObjectMapper json=JsonMapper.builder(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(8192).maxNumberLength(20).build()).build())
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    public static final class InvalidReceipt extends IllegalArgumentException {
        public InvalidReceipt() {super("Invalid command receipt metadata");}
    }
    public CommittedCommandVerifier.Receipt read(byte[] input) {
        if(input==null || input.length==0 || input.length>MAX_BYTES) throw new InvalidReceipt();
        try {
            var tree=json.readTree(input);
            for(String key:List.of("modelUuid","commandId","commitId"))
                if(!tree.path(key).isTextual() || !tree.path(key).textValue().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw new InvalidReceipt();
            for(String key:List.of("schemaVersion","actor","requestHash","manifestHash")) if(!tree.path(key).isTextual()) throw new InvalidReceipt();
            for(String key:List.of("expectedVersion","version")) if(!tree.path(key).isIntegralNumber() || !tree.path(key).canConvertToLong()) throw new InvalidReceipt();
            return json.treeToValue(tree,CommittedCommandVerifier.Receipt.class);
        } catch(Exception failure) {throw new InvalidReceipt();}
    }
}

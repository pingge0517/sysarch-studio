package com.example.sysarch.storage.command;

import com.example.sysarch.storage.ModelCodec;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Internal command wire contract, not a writable endpoint. Identity and model scope come from trusted server code. */
public final class ModelCommandProtocol {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_OPERATIONS = 256;
    private final ObjectMapper json = JsonMapper.builder(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(20).maxStringLength(65536).maxNumberLength(20).build()).build())
        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();

    public static final class InvalidCommand extends IllegalArgumentException {
        public InvalidCommand() { super("Invalid or unsupported model command"); }
    }
    @JsonTypeInfo(use=JsonTypeInfo.Id.NAME, property="kind")
    @JsonSubTypes({@JsonSubTypes.Type(value=RenameElement.class,name="RenameElement"), @JsonSubTypes.Type(value=SetAttribute.class,name="SetAttribute")})
    public sealed interface Operation permits RenameElement, SetAttribute { UUID elementUuid(); }
    public record RenameElement(UUID elementUuid, String name) implements Operation {
        public RenameElement { Objects.requireNonNull(elementUuid); if(name==null || name.length()>4096) throw new InvalidCommand(); }
    }
    /** Values use the same canonical EMF datatype lexical strings as StoredModel. Empty means unset. */
    public record SetAttribute(UUID elementUuid, String attribute, List<String> values) implements Operation {
        public SetAttribute {
            Objects.requireNonNull(elementUuid);
            if(attribute==null || !attribute.matches("[A-Za-z][A-Za-z0-9_]{0,63}") || values==null || values.size()>1000) throw new InvalidCommand();
            values=List.copyOf(values);
            if(values.stream().anyMatch(value->value.length()>65536)) throw new InvalidCommand();
        }
    }
    public record Body(UUID commandId, long expectedVersion, String schemaVersion, List<Operation> operations) {
        public Body {
            Objects.requireNonNull(commandId);
            if(expectedVersion<0 || expectedVersion==Long.MAX_VALUE || !ModelCodec.SCHEMA.equals(schemaVersion)
                || operations==null || operations.isEmpty() || operations.size()>MAX_OPERATIONS) throw new InvalidCommand();
            operations=List.copyOf(operations);
        }
    }
    public static final class BoundCommand {
        private final UUID modelUuid;
        private final String actor;
        private final Body body;
        private final String requestHash;
        private BoundCommand(UUID modelUuid,String actor,Body body,String requestHash) {
            this.modelUuid=modelUuid;this.actor=actor;this.body=body;this.requestHash=requestHash;
        }
        public UUID modelUuid() {return modelUuid;}
        public String actor() {return actor;}
        public Body body() {return body;}
        public String requestHash() {return requestHash;}
        public DurableCommandGate.Request gateRequest() {return new DurableCommandGate.Request(modelUuid,body.commandId(),requestHash,actor);}
        @Override public String toString() {return "BoundCommand[modelUuid="+modelUuid+",commandId="+body.commandId()+"]";}
    }
    public BoundCommand bind(UUID modelUuid,String verifiedActor,byte[] input) {
        if(modelUuid==null || verifiedActor==null || verifiedActor.isBlank() || verifiedActor.length()>128
            || verifiedActor.chars().anyMatch(Character::isISOControl) || input==null || input.length==0 || input.length>MAX_BYTES) throw new InvalidCommand();
        try {
            var tree=json.readTree(input);
            strictUuid(tree.path("commandId"));
            if(!tree.path("expectedVersion").isIntegralNumber() || !tree.path("expectedVersion").canConvertToLong()) throw new InvalidCommand();
            if(!tree.path("schemaVersion").isTextual() || !tree.path("operations").isArray()) throw new InvalidCommand();
            for(var operation:tree.path("operations")) {
                strictUuid(operation.path("elementUuid"));
                if(!operation.path("kind").isTextual()) throw new InvalidCommand();
                switch(operation.path("kind").textValue()) {
                    case "RenameElement" -> {if(!operation.path("name").isTextual()) throw new InvalidCommand();}
                    case "SetAttribute" -> {
                        if(!operation.path("attribute").isTextual() || !operation.path("values").isArray()) throw new InvalidCommand();
                        for(var value:operation.path("values")) if(!value.isTextual()) throw new InvalidCommand();
                    }
                    default -> throw new InvalidCommand();
                }
            }
            Body body=json.treeToValue(tree,Body.class);
            byte[] canonical=json.writeValueAsBytes(Map.of("protocol","sysarch-command/1","modelUuid",modelUuid,"actor",verifiedActor,"request",body));
            if(canonical.length>MAX_BYTES+1024) throw new InvalidCommand();
            return new BoundCommand(modelUuid,verifiedActor,body,ModelCodec.sha256(canonical));
        } catch(Exception failure) {throw new InvalidCommand();} // Never echo user content, tokens or parser source excerpts.
    }
    private static void strictUuid(com.fasterxml.jackson.databind.JsonNode value) {
        if(!value.isTextual() || !value.textValue().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw new InvalidCommand();
    }
}

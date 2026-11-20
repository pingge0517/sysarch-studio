package com.example.sysarch.storage.command;

import com.example.sysarch.storage.ModelCodec;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Validates replay evidence. Only an authoritative access-checked reader belongs here, never an unattested observation. */
public final class CommittedCommandVerifier implements DurableCommandGate.RemoteVerifier {
    public record Receipt(String schemaVersion,UUID modelUuid,UUID commandId,String actor,String requestHash,long expectedVersion,long version,UUID commitId,String manifestHash) {
        public Receipt {
            Objects.requireNonNull(modelUuid);Objects.requireNonNull(commandId);Objects.requireNonNull(commitId);
            if(!ModelCodec.SCHEMA.equals(schemaVersion) || actor==null || actor.isBlank() || actor.length()>128 || actor.chars().anyMatch(Character::isISOControl)
                || requestHash==null || !requestHash.matches("[a-f0-9]{64}") || manifestHash==null || !manifestHash.matches("[a-f0-9]{64}")
                || expectedVersion<0 || expectedVersion==Long.MAX_VALUE || version!=expectedVersion+1) throw new IllegalArgumentException("Invalid command receipt");
        }
    }
    public interface ReceiptReader {
        /** Verified current credential owner, not a user-supplied actor claim. */
        String verifiedActor();
        /** Must recheck current model/dependency access and durable immutable commit provenance as this actor. */
        Optional<Receipt> read(UUID modelUuid,UUID commandId) throws Exception;
    }
    public static final class Conflict extends RuntimeException {public Conflict() {super("Command identity or request differs");}}
    public static final class Denied extends RuntimeException {public Denied() {super("Command receipt access denied");}}
    private final ModelCommandProtocol.BoundCommand command;
    private final ReceiptReader reader;
    public CommittedCommandVerifier(ModelCommandProtocol.BoundCommand command,ReceiptReader reader) {this.command=Objects.requireNonNull(command);this.reader=Objects.requireNonNull(reader);}
    public Optional<Receipt> replay() throws Exception {
        requireActor(); var result=Objects.requireNonNull(reader.read(command.modelUuid(),command.body().commandId())); requireActor();
        result.ifPresent(record->requireMatch(command,record));
        return result;
    }
    /** Metadata consistency only; does not certify transaction provenance or immutability. */
    public static void requireMatch(ModelCommandProtocol.BoundCommand command,Receipt record) {
        if(!command.actor().equals(record.actor())) throw new Denied();
        if(!command.modelUuid().equals(record.modelUuid()) || !command.body().commandId().equals(record.commandId())
            || !command.requestHash().equals(record.requestHash()) || command.body().expectedVersion()!=record.expectedVersion()) throw new Conflict();
    }
    @Override public DurableCommandGate.Outcome verify(DurableCommandGate.Ticket ticket) throws Exception {
        if(!command.gateRequest().equals(ticket.request())) throw new Conflict();
        return replay().isPresent()?DurableCommandGate.Outcome.COMMITTED:DurableCommandGate.Outcome.NOT_FOUND;
    }
    private void requireActor() {if(!command.actor().equals(reader.verifiedActor())) throw new Denied();}
}

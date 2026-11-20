package com.example.sysarch.storage.polarion;

import com.example.sysarch.storage.command.CommittedCommandVerifier;
import com.example.sysarch.storage.command.DurableCommandGate;
import com.example.sysarch.storage.command.ModelCommandProtocol.BoundCommand;
import com.example.sysarch.storage.polarion.PolarionSnapshotLoader.*;
import com.example.sysarch.storage.polarion.PolarionSnapshotManifest.*;
import java.util.Objects;
import java.util.Optional;

/** Current-head observation only. Deliberately NOT an authoritative ReceiptReader or writable API. */
public final class PolarionCommandReceiptLookup {
    public enum Assurance { UNATTESTED }
    public record Observation(CommittedCommandVerifier.Receipt receipt,Item commitRecord,Head head,Assurance assurance) {
        public Observation {Objects.requireNonNull(receipt);Objects.requireNonNull(commitRecord);Objects.requireNonNull(head);Objects.requireNonNull(assurance);}
        public DurableCommandGate.Outcome gateOutcome() {return DurableCommandGate.Outcome.UNKNOWN;}
    }
    private final PolarionSnapshotLoader loader;
    public PolarionCommandReceiptLookup(PolarionSnapshotLoader loader) {this.loader=Objects.requireNonNull(loader);}
    public Optional<Observation> observe(Location location,BoundCommand command,PolarionRestReadSession session) {
        requireActor(command,session);
        if(!location.modelUuid().equals(command.modelUuid()) || location.root().revision()!=null) throw new ReadFailure(Failure.IDENTITY);
        var loaded=loader.load(location,session);
        var record=session.findCommandRecord(location.root().project(),command.modelUuid(),command.body().commandId());
        if(record.isEmpty()) {requireActor(command,session);return Optional.empty();}
        Item pinned=session.pinCurrentRevision(record.get());
        var receipt=session.commandReceipt(pinned,command.modelUuid(),command.body().commandId());
        CommittedCommandVerifier.requireMatch(command,receipt);
        Head head=loaded.head();
        if(head.version()!=receipt.version() || !head.commitId().equals(receipt.commitId()) || !head.manifest().sha256().equals(receipt.manifestHash())) throw new ReadFailure(Failure.INTEGRITY);
        if(!pinned.equals(session.pinCurrentRevision(record.get())) || !head.equals(session.head(location.root().current()))) throw new ReadFailure(Failure.INTEGRITY);
        requireActor(command,session);
        return Optional.of(new Observation(receipt,pinned,head,Assurance.UNATTESTED));
    }
    private static void requireActor(BoundCommand command,PolarionRestReadSession session) {
        if(session==null || !command.actor().equals(session.verifiedSubject())) throw new ReadFailure(Failure.DENIED);
    }
}

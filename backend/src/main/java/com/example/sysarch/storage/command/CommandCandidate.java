package com.example.sysarch.storage.command;

import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.storage.ModelCodec;
import com.example.sysarch.storage.StoredModel;
import com.example.sysarch.storage.polarion.ExternalRequirementContent;
import com.example.sysarch.validation.ConstraintValidator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;

/** Prepares an isolated candidate only. No permission grant, remote commit, shared projection or persistence. */
public final class CommandCandidate {
    public static final class VersionConflict extends RuntimeException { public VersionConflict() {super("Model version changed");} }
    public static final class Rejected extends RuntimeException { public Rejected() {super("Model command candidate rejected");} }
    private final ModelCodec codec;
    private final ConstraintValidator constraints;
    public CommandCandidate(ModelCodec codec,ConstraintValidator constraints) {this.codec=Objects.requireNonNull(codec);this.constraints=Objects.requireNonNull(constraints);}

    /** Input must be the authorized persisted snapshot, before external requirement text enrichment. */
    public StoredModel prepare(StoredModel committed,long currentVersion,Set<UUID> externalRequirements,ModelCommandProtocol.BoundCommand command) {
        if(currentVersion!=command.body().expectedVersion()) throw new VersionConflict();
        if(!committed.modelUuid().equals(command.modelUuid())) throw new Rejected();
        Set<UUID> external=Set.copyOf(externalRequirements);
        try {
            codec.encode(committed);
            Map<UUID,StoredModel.Node> nodes=new HashMap<>(); committed.nodes().forEach(node->nodes.put(node.uuid(),node));
            for(UUID id:external) if(!ExternalRequirementContent.referenceOnly(nodes.get(id))) throw new Rejected();
            for(var operation:command.body().operations()) {
                StoredModel.Node old=nodes.get(operation.elementUuid());
                if(old==null || external.contains(old.uuid()) || old.type().equals("DiagramLayout")) throw new Rejected();
                String name; java.util.List<String> values;
                if(operation instanceof ModelCommandProtocol.RenameElement rename) {name="name";values=java.util.List.of(rename.name());}
                else if(operation instanceof ModelCommandProtocol.SetAttribute set) {name=set.attribute();values=set.values();}
                else throw new Rejected();
                var type=(EClass)SysarchPackage.eINSTANCE.getEClassifier(old.type());
                var feature=type.getEStructuralFeature(name);
                if(!(feature instanceof EAttribute) || !feature.isChangeable() || feature.isDerived() || feature.isTransient() || feature.isVolatile()) throw new Rejected();
                var attributes=new HashMap<>(old.attributes());
                if(values.isEmpty()) attributes.remove(name); else attributes.put(name,values);
                nodes.put(old.uuid(),new StoredModel.Node(old.uuid(),old.type(),attributes,old.references()));
            }
            var candidate=new StoredModel(committed.schemaVersion(),committed.modelUuid(),committed.documents(),committed.nodes().stream().map(node->nodes.get(node.uuid())).toList());
            byte[] bytes=codec.encode(candidate);
            var decoded=codec.decode(bytes,ModelCodec.sha256(bytes));
            var objects=new ArrayList<org.eclipse.emf.ecore.EObject>();
            decoded.documents().forEach(document->document.resource().getAllContents().forEachRemaining(object->{
                // Reference placeholders have no editable domain attributes; their actual content is authorized separately.
                if(!external.contains(decoded.identities().id(object))) objects.add(object);
            }));
            if(!constraints.validateAll(objects).isEmpty()) throw new Rejected();
            return codec.capture(candidate.modelUuid(),decoded.documents(),decoded.identities());
        } catch(Rejected failure) {throw failure;}
        catch(RuntimeException failure) {throw new Rejected();}
    }
}

package com.example.sysarch.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.model.SysarchPackage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

class ModelCodecTest {
    private final ModelCodec codec = new ModelCodec();
    private final UUID modelUuid = UUID.randomUUID();
    private final UUID documentUuid = UUID.randomUUID();

    @Test
    void allConcreteTypesAndEveryPersistentFeatureRoundTripWithoutLosingOrderOrText() {
        Resource resource = allTypes();
        var identities = new ModelIdentityRegistry();
        StoredModel first = capture(resource, identities);
        byte[] encoded = this.codec.encode(first);
        var decoded = this.codec.decode(encoded, ModelCodec.sha256(encoded));
        StoredModel second = this.codec.capture(this.modelUuid, decoded.documents(), decoded.identities());
        assertArrayEquals(encoded, this.codec.encode(second));
        assertEquals(this.codec.digests(first), this.codec.digests(second));
        var types = first.nodes().stream().map(StoredModel.Node::type).collect(java.util.stream.Collectors.toSet());
        for (var classifier : SysarchPackage.eINSTANCE.getEClassifiers()) {
            if (classifier instanceof EClass type && !type.isAbstract()) assertTrue(types.contains(type.getName()), type.getName());
        }
        assertTrue(types.contains("ConstraintExpr"), "non-ArchElement expressions must be preserved");
        assertTrue(types.contains("DiagramLayout"));
        assertTrue(first.nodes().stream().anyMatch(n -> n.references().values().stream().anyMatch(v -> v.size() == 2)));
        assertTrue(new String(encoded, StandardCharsets.UTF_8).contains("中文"));
    }

    @Test
    void stableUuidIsIndependentOfBusinessIdAndName() {
        Resource resource = new ResourceImpl();
        var block = SysarchFactory.eINSTANCE.createPartDefinition();
        block.setId("PD-001"); block.setName("before");
        resource.getContents().add(block);
        var identities = new ModelIdentityRegistry();
        UUID id = capture(resource, identities).nodes().getFirst().uuid();
        block.setId("PD-999"); block.setName("after");
        assertEquals(id, capture(resource, identities).nodes().getFirst().uuid());
    }

    @Test
    void duplicateBusinessIdsAreNotCollapsed() {
        Resource resource = new ResourceImpl();
        for (int i = 0; i < 2; i++) {
            var block = SysarchFactory.eINSTANCE.createPartDefinition(); block.setId("same");
            resource.getContents().add(block);
        }
        assertEquals(2, capture(resource, new ModelIdentityRegistry()).nodes().size());
    }

    @Test
    void layoutChangesDoNotAffectSemanticHash() {
        Resource resource = new ResourceImpl();
        var block = SysarchFactory.eINSTANCE.createPartDefinition(); block.setName("system");
        var layout = SysarchFactory.eINSTANCE.createDiagramLayout(); layout.setX(10); layout.setElementRef("PD-001");
        resource.getContents().add(block); resource.getContents().add(layout);
        var registry = new ModelIdentityRegistry();
        var before = this.codec.digests(capture(resource, registry));
        layout.setX(99);
        var after = this.codec.digests(capture(resource, registry));
        assertEquals(before.semantic(), after.semantic());
        assertNotEquals(before.layout(), after.layout());
        assertNotEquals(before.complete(), after.complete());
        block.setName("changed");
        assertNotEquals(after.semantic(), this.codec.digests(capture(resource, registry)).semantic());
    }

    @Test
    void unsetDefaultsAndEmptyStringSurviveUsingActualEmfSemantics() {
        var block = SysarchFactory.eINSTANCE.createPartDefinition();
        block.setName(""); block.setLayer(null); // generated EMF setter normalizes null to LOGICAL
        Resource resource = new ResourceImpl(); resource.getContents().add(block);
        var first = capture(resource, new ModelIdentityRegistry());
        byte[] bytes = this.codec.encode(first);
        var decoded = this.codec.decode(bytes, ModelCodec.sha256(bytes));
        var restored = (com.example.sysarch.model.PartDefinition) decoded.documents().getFirst().resource().getContents().getFirst();
        assertEquals("", restored.getName()); assertEquals(block.getLayer(), restored.getLayer()); assertFalse(restored.isIsAbstract());
        assertArrayEquals(bytes, this.codec.encode(this.codec.capture(this.modelUuid, decoded.documents(), decoded.identities())));
    }

    @Test
    void deterministicDespiteNodeAndDocumentMapIterationOrder() {
        var first = capture(allTypes(), new ModelIdentityRegistry());
        var reverse = new ArrayList<>(first.nodes()); Collections.reverse(reverse);
        assertArrayEquals(this.codec.encode(first), this.codec.encode(new StoredModel(ModelCodec.SCHEMA, this.modelUuid, first.documents(), reverse)));
    }

    @Test
    void multipleDocumentsAndCrossDocumentReferencesPreserveRoots() {
        Resource a = new ResourceImpl(); Resource b = new ResourceImpl();
        var type = SysarchFactory.eINSTANCE.createPartDefinition();
        var instance = SysarchFactory.eINSTANCE.createPart(); instance.setTypedBy(type);
        a.getContents().add(instance); b.getContents().add(type);
        var sources = List.of(new ModelCodec.SourceDocument(UUID.randomUUID(), a), new ModelCodec.SourceDocument(UUID.randomUUID(), b));
        byte[] bytes = this.codec.encode(this.codec.capture(this.modelUuid, sources, new ModelIdentityRegistry()));
        var decoded = this.codec.decode(bytes, ModelCodec.sha256(bytes));
        assertEquals(2, decoded.documents().size());
        assertArrayEquals(bytes, this.codec.encode(this.codec.capture(this.modelUuid, decoded.documents(), decoded.identities())));
    }

    @Test
    void emptyModelRoundTrips() {
        byte[] bytes = this.codec.encode(new StoredModel(ModelCodec.SCHEMA, this.modelUuid, List.of(), List.of()));
        assertTrue(this.codec.decode(bytes, ModelCodec.sha256(bytes)).documents().isEmpty());
    }

    @Test
    void rejectsUnknownSchemaTypeFeatureAndAttributeKind() {
        StoredModel source = oneBlock();
        assertInvalid(new StoredModel("2.0", this.modelUuid, source.documents(), source.nodes()));
        var n = source.nodes().getFirst();
        assertInvalid(replace(source, new StoredModel.Node(n.uuid(), "NotAType", n.attributes(), n.references())));
        assertInvalid(replace(source, new StoredModel.Node(n.uuid(), n.type(), Map.of("typo", List.of("x")), n.references())));
        assertInvalid(replace(source, new StoredModel.Node(n.uuid(), n.type(), Map.of("ownedParts", List.of("x")), n.references())));
    }

    @Test
    void rejectsMissingAndWronglyTypedReferences() {
        var source = oneBlock(); var n = source.nodes().getFirst();
        assertInvalid(replace(source, new StoredModel.Node(n.uuid(), n.type(), n.attributes(), Map.of("superTypes", List.of(UUID.randomUUID())))));
        assertInvalid(replace(source, new StoredModel.Node(n.uuid(), n.type(), n.attributes(), Map.of("ownedParts", List.of(n.uuid())))));
    }

    @Test
    void rejectsDuplicateUuidRootsAndUnownedNodes() {
        var source = oneBlock(); var n = source.nodes().getFirst();
        assertInvalid(new StoredModel(ModelCodec.SCHEMA, this.modelUuid, source.documents(), List.of(n, n)));
        assertInvalid(new StoredModel(ModelCodec.SCHEMA, this.modelUuid,
                List.of(new StoredModel.Document(this.documentUuid, List.of(n.uuid(), n.uuid()))), List.of(n)));
        assertInvalid(new StoredModel(ModelCodec.SCHEMA, this.modelUuid, List.of(), List.of(n)));
    }

    @Test
    void rejectsMultipleContainmentOwnersBeforeEmfCanReparent() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), p = UUID.randomUUID();
        assertInvalid(new StoredModel(ModelCodec.SCHEMA, this.modelUuid, List.of(new StoredModel.Document(this.documentUuid, List.of(a, b))), List.of(
                new StoredModel.Node(a, "PartDefinition", Map.of(), Map.of("ownedParts", List.of(p))),
                new StoredModel.Node(b, "PartDefinition", Map.of(), Map.of("ownedParts", List.of(p))),
                new StoredModel.Node(p, "Part", Map.of(), Map.of()))));
    }

    @Test
    void rejectsInvalidEnumBooleanNumberAndCardinality() {
        var source = oneBlock(); var n = source.nodes().getFirst();
        for (var attributes : List.of(Map.of("layer", List.of("INVALID")), Map.of("isAbstract", List.of("nonsense")),
                Map.of("rev", List.of("not-a-number")), Map.of("name", List.of("one", "two")),
                Map.of("rev", List.<String>of()), Map.of("stereotypes", List.of("same", "same")))) {
            assertInvalid(replace(source, new StoredModel.Node(n.uuid(), n.type(), attributes, n.references())));
        }
    }

    @Test
    void rejectsBadHashMalformedDuplicateOrUnexpectedJsonAndOversizedInput() {
        byte[] valid = this.codec.encode(oneBlock());
        assertThrows(ModelCodec.InvalidSnapshot.class, () -> this.codec.decode(valid, "wrong"));
        for (String bad : List.of("{}", "null", "{", new String(valid, StandardCharsets.UTF_8) + " {}",
                new String(valid, StandardCharsets.UTF_8).replace("\"schemaVersion\":\"1.0\"", "\"schemaVersion\":\"1.0\",\"schemaVersion\":\"2.0\""),
                new String(valid, StandardCharsets.UTF_8).replace("\"schemaVersion\":\"1.0\"", "\"schemaVersion\":\"1.0\",\"extra\":true"))) {
            assertThrows(ModelCodec.InvalidSnapshot.class, () -> this.codec.read(bad.getBytes(StandardCharsets.UTF_8)), bad);
        }
        assertThrows(ModelCodec.InvalidSnapshot.class, () -> this.codec.read(new byte[ModelCodec.MAX_BYTES + 1]));
    }

    @Test
    void rejectsExternalReferencesWithoutResolvingThem() {
        var part = SysarchFactory.eINSTANCE.createPart();
        part.setTypedBy(SysarchFactory.eINSTANCE.createPartDefinition());
        Resource resource = new ResourceImpl(); resource.getContents().add(part);
        assertThrows(ModelCodec.InvalidSnapshot.class, () -> capture(resource, new ModelIdentityRegistry()));
    }

    @Test
    void identityRegistryRejectsDuplicateAndReassignedUuid() {
        var registry = new ModelIdentityRegistry(); var a = SysarchFactory.eINSTANCE.createPart(); var b = SysarchFactory.eINSTANCE.createPart();
        UUID id = registry.id(a);
        assertThrows(IllegalArgumentException.class, () -> registry.register(b, id));
        assertThrows(IllegalArgumentException.class, () -> registry.register(a, UUID.randomUUID()));
        assertEquals(id, registry.id(a));
    }

    private StoredModel oneBlock() {
        Resource resource = new ResourceImpl(); resource.getContents().add(SysarchFactory.eINSTANCE.createPartDefinition());
        return capture(resource, new ModelIdentityRegistry());
    }

    private StoredModel replace(StoredModel source, StoredModel.Node node) {
        return new StoredModel(ModelCodec.SCHEMA, this.modelUuid, source.documents(), List.of(node));
    }

    private void assertInvalid(StoredModel model) {
        assertThrows(ModelCodec.InvalidSnapshot.class, () -> this.codec.encode(model));
    }

    private StoredModel capture(Resource resource, ModelIdentityRegistry identities) {
        return this.codec.capture(this.modelUuid, List.of(new ModelCodec.SourceDocument(this.documentUuid, resource)), identities);
    }

    /** Includes every concrete class, populated attributes, ordered containments and cyclic noncontainment references. */
    @SuppressWarnings("unchecked")
    private Resource allTypes() {
        Resource resource = new ResourceImpl(URI.createURI("memory:/all-types"));
        List<EObject> roots = new ArrayList<>();
        for (var classifier : SysarchPackage.eINSTANCE.getEClassifiers()) {
            if (classifier instanceof EClass type && !type.isAbstract()) {
                EObject object = populated(type); roots.add(object); resource.getContents().add(object);
            }
        }
        for (EObject object : roots) {
            for (EReference reference : object.eClass().getEAllReferences()) {
                if (reference.isContainment()) {
                    EObject child = populated(reference.getEReferenceType());
                    if (reference.isMany()) {
                        ((List<EObject>) object.eGet(reference)).add(child);
                        ((List<EObject>) object.eGet(reference)).add(populated(reference.getEReferenceType()));
                    } else object.eSet(reference, child);
                } else if (!reference.isContainer()) {
                    EObject target = roots.stream().filter(o -> reference.getEReferenceType().isSuperTypeOf(o.eClass())
                            && !o.eClass().getName().equals("DiagramLayout")).findFirst().orElseThrow();
                    if (reference.isMany()) ((List<EObject>) object.eGet(reference)).add(target);
                    else object.eSet(reference, target);
                }
            }
        }
        return resource;
    }

    private EObject populated(EClass type) {
        EObject object = EcoreUtil.create(type);
        for (EAttribute attribute : type.getEAllAttributes()) {
            if (!attribute.isChangeable() || attribute.isDerived() || attribute.isTransient()) continue;
            EDataType data = attribute.getEAttributeType();
            String text = data instanceof EEnum e ? e.getELiterals().getLast().getLiteral()
                    : data.getInstanceClass() == String.class ? "中文\r\n" + attribute.getName()
                    : data.getInstanceClass() == boolean.class ? "true"
                    : data.getInstanceClass() == double.class ? "3.25" : "17";
            Object value = EcoreUtil.createFromString(data, text);
            if (attribute.isMany()) {
                @SuppressWarnings("unchecked") var values = (List<Object>) object.eGet(attribute);
                values.add(value);
                if (data.getInstanceClass() == String.class) values.add("second");
            } else object.eSet(attribute, value);
        }
        return object;
    }
}

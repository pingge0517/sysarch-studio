package com.example.sysarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.sysarch.model.Allocate;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.SysarchFactory;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.junit.jupiter.api.Test;

/** Browser test 2026-11-14 (B2): elements created in the UI get a readable id in the demo's series, not their UUID. */
class BusinessIdsTest {

    private static final SysarchFactory F = SysarchFactory.eINSTANCE;

    @Test
    void aNewElementContinuesItsSeriesAcrossTheModelsDocuments() {
        ResourceSetImpl set = new ResourceSetImpl();
        Resource demo = new ResourceImpl(URI.createURI("memory:/demo.json"));
        Resource other = new ResourceImpl(URI.createURI("memory:/other.json"));
        set.getResources().add(demo);
        set.getResources().add(other);
        PartDefinition system = F.createPartDefinition();
        system.setId("PD-006");
        demo.getContents().add(system);
        PartDefinition imported = F.createPartDefinition();
        imported.setId("IMP-PART-DEF-0001"); // another series: ignored
        other.getContents().add(imported);

        PartDefinition created = F.createPartDefinition();
        other.getContents().add(created);
        BusinessIds.assign(created);
        assertEquals("PD-007", created.getId(), "one past the highest PD of all documents");

        Part part = F.createPart();
        system.getOwnedParts().add(part);
        BusinessIds.assign(part);
        assertEquals("PT-001", part.getId(), "a contained element too; the first of its series");

        Allocate allocation = F.createAllocate();
        demo.getContents().add(allocation);
        BusinessIds.assign(allocation);
        assertEquals("AL-001", allocation.getId());
    }

    @Test
    void anExistingIdAndADetachedElementAreLeftAlone() {
        Resource r = new ResourceImpl(URI.createURI("memory:/x.json"));
        PartDefinition named = F.createPartDefinition();
        named.setId("CUSTOM");
        r.getContents().add(named);
        BusinessIds.assign(named);
        assertEquals("CUSTOM", named.getId());

        PartDefinition detached = F.createPartDefinition();
        BusinessIds.assign(detached);
        assertNull(detached.getId(), "not in a resource yet: nothing to count against");
    }
}

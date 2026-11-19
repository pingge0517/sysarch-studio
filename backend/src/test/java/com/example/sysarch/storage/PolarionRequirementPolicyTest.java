package com.example.sysarch.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PolarionRequirementPolicyTest {
    @Test void onlySameProjectIsAllowedByDefault() {
        var policy=new PolarionStoreProperties();
        assertTrue(policy.canReferenceRequirement("alpha","alpha"));
        assertFalse(policy.canReferenceRequirement("alpha","beta"));
    }
    @Test void explicitAllowlistIsDirectionalAndCannotBeChangedByCallerCollections() {
        var targets=new HashSet<>(Set.of("beta"));var map=new HashMap<String,Set<String>>();map.put("alpha",targets);
        var policy=new PolarionStoreProperties();policy.setRequirementProjects(map); targets.add("secret"); map.clear();
        assertTrue(policy.canReferenceRequirement("alpha","beta"));
        assertFalse(policy.canReferenceRequirement("beta","alpha"));
        assertFalse(policy.canReferenceRequirement("alpha","secret"));
        assertThrows(UnsupportedOperationException.class,()->policy.getRequirementProjects().clear());
    }
    @Test void invalidScopeFailsClosedWithoutReplacingTheCurrentPolicy() {
        var policy=new PolarionStoreProperties();
        assertThrows(IllegalArgumentException.class,()->policy.setRequirementProjects(Map.of("../secret",Set.of("beta"))));
        assertFalse(policy.canReferenceRequirement("alpha","beta"));
    }
}

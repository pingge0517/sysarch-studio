package com.example.sysarch.storage.polarion;

import com.example.sysarch.storage.StoredModel;
import java.util.List;
import java.util.Set;

/** Shared at-rest boundary: independently protected requirement content must never enter model snapshots. */
public final class ExternalRequirementContent {
    private ExternalRequirementContent() { }
    public static boolean referenceOnly(StoredModel.Node node) {
        if(node==null || !"Requirement".equals(node.type())) return false;
        var attributes=node.attributes();
        return Set.of("text","description","reqId","workItemId").stream().noneMatch(attributes::containsKey)
            && (!attributes.containsKey("name") || attributes.get("name").equals(List.of("Requirement reference")));
    }
}

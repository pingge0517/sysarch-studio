package com.example.sysarch;

import com.example.sysarch.model.ArchElement;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

/**
 * Business ids for elements created in the UI (palette tools, matrix actions): {@code PD-007}, {@code AL-004} … in the
 * series the demo and the imports use, so baseline diffs, reports and Polarion fields show a readable id instead of the
 * element's UUID (browser test 2026-11-14, finding B2). Only an element without an id gets one; the number is one past
 * the highest of its prefix in the whole model (all documents of the editing context), three digits at least.
 */
public final class BusinessIds {

    private static final Map<String, String> PREFIX = Map.ofEntries(
            Map.entry("PartDefinition", "PD"), Map.entry("Part", "PT"), Map.entry("Port", "PO"), Map.entry("Connection", "CO"),
            Map.entry("ItemFlow", "FL"), Map.entry("InterfaceDef", "IF"), Map.entry("Requirement", "RQ"), Map.entry("Allocate", "AL"),
            Map.entry("ConstraintBlock", "CB"), Map.entry("Parameter", "PR"), Map.entry("ConstraintExpr", "EX"), Map.entry("BindingConnector", "BC"),
            Map.entry("StateMachine", "SM"), Map.entry("State", "ST"), Map.entry("Transition", "TR"), Map.entry("Activity", "AC"),
            Map.entry("Action", "AN"), Map.entry("ActivityFlow", "AF"), Map.entry("UseCase", "UC"), Map.entry("Actor", "AT"),
            Map.entry("Interaction", "IA"), Map.entry("Lifeline", "LL"), Map.entry("Message", "MS"));

    private static final Pattern NUMBERED = Pattern.compile("([A-Z]{2})-(\\d+)");

    private BusinessIds() {
    }

    /** Gives {@code element} the next id of its prefix unless it has one; it must already be in its resource. */
    public static void assign(EObject element) {
        if (!(element instanceof ArchElement arch) || (arch.getId() != null && !arch.getId().isBlank())) {
            return;
        }
        String prefix = PREFIX.get(element.eClass().getName());
        Resource resource = element.eResource();
        if (prefix == null || resource == null) {
            return;
        }
        arch.setId(String.format("%s-%03d", prefix, highest(prefix, resource) + 1));
    }

    static int highest(String prefix, Resource resource) {
        int highest = 0;
        ResourceSet set = resource.getResourceSet();
        Iterable<Resource> resources = set == null ? java.util.List.of(resource) : set.getResources();
        for (Resource r : resources) {
            for (TreeIterator<EObject> it = r.getAllContents(); it.hasNext();) {
                if (it.next() instanceof ArchElement other && other.getId() != null) {
                    Matcher m = NUMBERED.matcher(other.getId());
                    if (m.matches() && m.group(1).equals(prefix)) {
                        highest = Math.max(highest, Integer.parseInt(m.group(2)));
                    }
                }
            }
        }
        return highest;
    }
}

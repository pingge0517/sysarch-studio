import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.uml2.uml.Abstraction;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Connector;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Port;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.UMLPackage;
import org.eclipse.uml2.uml.UMLPlugin;
import org.eclipse.uml2.uml.resources.util.UMLResourcesUtil;

/**
 * Not part of the build. P3-AC-6 Papyrus check (docs/xmi-export.md §6): opens SysArch's Papyrus-flavour export
 * ({@code "flavour": "papyrus"}) with Papyrus' own UML2 and SysML 1.6 profile, registered as Papyrus' plugin.xml does,
 * and asks UML2 which stereotypes are applied to each element (what Papyrus' editor shows). Run with Papyrus' bundled
 * Java 11 and its plugin jars (uml2 uml/resources/common/types/profile.standard, emf ecore/xmi/common,
 * mapping.ecore2xml, papyrus.sysml16) on the classpath:
 *   java -cp "<jars>" PapyrusCheck.java <papyrus jar dir> <file.uml>...
 */
public class PapyrusCheck {

    public static void main(String[] args) throws Exception {
        String sysmlJar = new File(args[0]).listFiles((d, n) -> n.startsWith("org.eclipse.papyrus.sysml16_") && n.endsWith(".jar"))[0].getAbsolutePath()
                .replace('\\', '/');
        int failures = 0;
        for (int i = 1; i < args.length; i++) {
            failures += check(sysmlJar, args[i]);
        }
        System.out.println(failures == 0 ? "ALL OK" : failures + " FILE(S) WITH PROBLEMS");
        System.exit(failures == 0 ? 0 : 1);
    }

    static ResourceSet papyrus(String sysmlJar) {
        ResourceSet rs = new ResourceSetImpl();
        UMLResourcesUtil.init(rs);
        rs.getURIConverter().getURIMap().put(URI.createURI("pathmap://SysML16_PROFILES/"), URI.createURI("jar:file:/" + sysmlJar + "!/resources/profile/"));
        rs.getURIConverter().getURIMap().put(URI.createURI("pathmap://SysML16_LIBRARIES/"), URI.createURI("jar:file:/" + sysmlJar + "!/resources/library/"));
        String[][] packages = { { "sysml.SysMLPackage", "SysML" }, { "blocks.BlocksPackage", "SysML.package_packagedElement_Blocks" },
                { "requirements.RequirementsPackage", "SysML.package_packagedElement_Requirements" },
                { "allocations.AllocationsPackage", "SysML.package_packagedElement_Allocations" },
                { "portsandflows.PortsAndFlowsPackage", "SysML.package_packagedElement_Ports_u0026Flows" },
                { "activities.ActivitiesPackage", "SysML.package_packagedElement_Activities" },
                { "constraintblocks.ConstraintBlocksPackage", "SysML.package_packagedElement_ConstraintBlocks" },
                { "deprecatedelements.DeprecatedElementsPackage", "SysML.package_packagedElement_DeprecatedElements" },
                { "modelelements.ModelElementsPackage", "SysML.package_packagedElement_ModelElements" } };
        for (String[] p : packages) {
            try {
                EPackage e = (EPackage) java.lang.Class.forName("org.eclipse.papyrus.sysml16." + p[0]).getField("eINSTANCE").get(null);
                EPackage.Registry.INSTANCE.put(e.getNsURI(), e);
                UMLPlugin.getEPackageNsURIToProfileLocationMap().put(e.getNsURI(), URI.createURI("pathmap://SysML16_PROFILES/SysML.profile.uml#" + p[1]));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return rs;
    }

    static int check(String sysmlJar, String path) {
        System.out.println("=== " + path);
        ResourceSet rs = papyrus(sysmlJar);
        Resource r;
        try {
            r = rs.getResource(URI.createFileURI(new File(path).getAbsolutePath()), true);
        } catch (RuntimeException e) {
            System.out.println("  LOAD FAILED: " + e.getMessage());
            return 1;
        }
        int problems = 0;
        for (Resource.Diagnostic d : r.getErrors()) {
            System.out.println("  LOAD ERROR: " + d.getMessage());
            problems++;
        }
        Model model = (Model) EcoreUtil.getObjectByType(r.getContents(), UMLPackage.Literals.MODEL);
        if (model == null) {
            System.out.println("  NO uml:Model (roots: " + r.getContents().stream().map(o -> o.eClass().getName()).collect(Collectors.toList()) + ")");
            return 1;
        }
        System.out.println("  profiles applied: " + model.getAllAppliedProfiles().stream().map(p -> p.getQualifiedName()).collect(Collectors.toList()));
        // the model only: Papyrus' derived stereotype features need its running Eclipse services (stereotypes are checked below)
        for (var proxy : EcoreUtil.UnresolvedProxyCrossReferencer.find(model).keySet()) {
            System.out.println("  UNRESOLVED: " + ((org.eclipse.emf.ecore.InternalEObject) proxy).eProxyURI());
            problems++;
        }
        Map<String, Integer> stereotyped = new TreeMap<>();
        int[] counts = new int[4]; // classes, ports, connectors, abstractions
        for (EObject o : (Iterable<EObject>) model::eAllContents) {
            if (o instanceof Class && !(o instanceof org.eclipse.uml2.uml.Behavior)) {
                counts[0]++;
                problems += expectStereotype((Element) o, stereotyped);
            } else if (o instanceof Port) {
                counts[1]++;
            } else if (o instanceof Connector) {
                counts[2]++;
            } else if (o instanceof Abstraction) {
                counts[3]++;
                problems += expectStereotype((Element) o, stereotyped);
            }
        }
        System.out.println("  classes " + counts[0] + ", ports " + counts[1] + ", connectors " + counts[2] + ", abstractions " + counts[3]);
        System.out.println("  stereotypes applied (as Papyrus sees them): " + stereotyped);
        for (Class c : model.getOwnedTypes().stream().filter(Class.class::isInstance).map(Class.class::cast).collect(Collectors.toList())) {
            Stereotype req = c.getAppliedStereotype("SysML::Requirements::Requirement");
            if (req != null) {
                System.out.println("  requirement " + c.getName() + ": id=" + c.getValue(req, "id") + " text=" + c.getValue(req, "text"));
                if (c.getValue(req, "id") == null || c.getValue(req, "text") == null) {
                    problems++;
                }
            }
        }
        System.out.println(problems == 0 ? "  OK" : "  " + problems + " PROBLEM(S)");
        return problems == 0 ? 0 : 1;
    }

    /** Every class and abstraction SysArch exports carries exactly one SysML stereotype. */
    static int expectStereotype(Element e, Map<String, Integer> out) {
        List<Stereotype> applied = e.getAppliedStereotypes();
        if (applied.size() != 1) {
            System.out.println("  NOT STEREOTYPED as expected: " + e + " -> " + applied.stream().map(Stereotype::getQualifiedName).collect(Collectors.toList()));
            return 1;
        }
        out.merge(applied.get(0).getQualifiedName(), 1, Integer::sum);
        return 0;
    }
}

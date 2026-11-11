import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.uml2.uml.Abstraction;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Connector;
import org.eclipse.uml2.uml.ConnectorEnd;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Port;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.UMLPackage;
import org.eclipse.uml2.uml.resource.XMI2UMLResource;
import org.eclipse.uml2.uml.resources.util.UMLResourcesUtil;

/**
 * Not part of the build. Run (P3-AC-6 proxy check, see docs/xmi-export.md §6):
 *   java -Dstdout.encoding=UTF-8 -cp "<UML2 5.5 jars: uml, uml.resources, uml.profile.standard, common, types>;
 *        <org.eclipse.emf.mapping.ecore2xml 2.13>;<org.eclipse.emf.ecore 2.37, ecore.xmi 2.38, common 2.31>" Uml2Check.java <file.xmi>...
 * (on this machine the downloaded jars are in C:\UML2Check; the EMF jars come from the Gradle cache)
 *
 * P3-AC-6 proxy check (not Cameo): load SysArch's XMI with Eclipse UML2 5.5 (the reference UML metamodel implementation,
 * what Papyrus is built on) through its OMG XMI loader, then report load problems, unresolved references, the UML
 * elements found, the SysML stereotype applications and their base elements, and UML2's own validation.
 * The SysML 1.6 profile is not part of UML2, so the six stereotypes SysArch writes are declared here with their SysML
 * 1.6 names, only so that their applications load and their base references can be checked.
 */
public class Uml2Check {

    static final String SYSML = "http://www.omg.org/spec/SysML/20181001/SysML";

    public static void main(String[] args) throws Exception {
        int failures = 0;
        for (String path : args) {
            failures += check(path);
        }
        System.out.println(failures == 0 ? "ALL OK" : failures + " FILE(S) WITH PROBLEMS");
        System.exit(failures == 0 ? 0 : 1);
    }

    static EPackage sysml() {
        EPackage p = EcoreFactory.eINSTANCE.createEPackage();
        p.setName("sysml");
        p.setNsPrefix("sysml");
        p.setNsURI(SYSML);
        for (String[] s : new String[][] { { "Block", "base_Class" }, { "InterfaceBlock", "base_Class" }, { "Requirement", "base_Class" },
                { "Satisfy", "base_Abstraction" }, { "Verify", "base_Abstraction" }, { "Allocate", "base_Abstraction" } }) {
            EClass c = EcoreFactory.eINSTANCE.createEClass();
            c.setName(s[0]);
            EReference base = EcoreFactory.eINSTANCE.createEReference();
            base.setName(s[1]);
            base.setEType(s[1].equals("base_Class") ? UMLPackage.Literals.CLASS : UMLPackage.Literals.ABSTRACTION);
            c.getEStructuralFeatures().add(base);
            if (s[0].equals("Requirement")) {
                for (String a : new String[] { "id", "text" }) {
                    var attr = EcoreFactory.eINSTANCE.createEAttribute();
                    attr.setName(a);
                    attr.setEType(EcorePackage.Literals.ESTRING);
                    c.getEStructuralFeatures().add(attr);
                }
            }
            p.getEClassifiers().add(c);
        }
        return p;
    }

    static int check(String path) {
        System.out.println("=== " + path);
        ResourceSet rs = new ResourceSetImpl();
        UMLResourcesUtil.init(rs);
        rs.getPackageRegistry().put(SYSML, sysml());
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", XMI2UMLResource.Factory.INSTANCE);
        rs.getLoadOptions().put(XMLResource.OPTION_RECORD_UNKNOWN_FEATURE, Boolean.TRUE);
        Resource r;
        try {
            // UML2 5.5 knows UML 2.5 (20131001), not 2.5.1 (20161101); the classes and features SysArch writes are the same in
            // both, so a temp copy with only the uml namespace changed is checked
            String text = java.nio.file.Files.readString(new File(path).toPath());
            String rewritten = text.replace("xmlns:uml=\"http://www.omg.org/spec/UML/20161101\"", "xmlns:uml=\"http://www.omg.org/spec/UML/20131001\"");
            System.out.println("  uml namespace 2.5.1 -> 2.5 for UML2: " + !rewritten.equals(text));
            File copy = File.createTempFile("uml2check", ".xmi");
            copy.deleteOnExit();
            java.nio.file.Files.writeString(copy.toPath(), rewritten);
            r = rs.getResource(URI.createFileURI(copy.getAbsolutePath()), true);
        } catch (RuntimeException | java.io.IOException e) {
            System.out.println("  LOAD FAILED: " + e);
            return 1;
        }
        int problems = 0;
        System.out.println("  resource type: " + r.getClass().getSimpleName());
        for (Resource.Diagnostic d : r.getErrors()) {
            System.out.println("  LOAD ERROR: " + d.getMessage() + " (line " + d.getLine() + ")");
            problems++;
        }
        for (Resource.Diagnostic d : r.getWarnings()) {
            System.out.println("  load warning: " + d.getMessage() + " (line " + d.getLine() + ")");
        }
        Model model = (Model) EcoreUtil.getObjectByType(r.getContents(), UMLPackage.Literals.MODEL);
        if (model == null) {
            System.out.println("  NO uml:Model; roots: " + r.getContents().stream().map(o -> o.eClass().getEPackage().getNsURI() + "#" + o.eClass().getName()).toList());
            return 1;
        }
        Map<String, Integer> counts = new TreeMap<>();
        model.eAllContents().forEachRemaining(o -> counts.merge("uml:" + o.eClass().getName(), 1, Integer::sum));
        System.out.println("  model '" + model.getName() + "': " + counts);
        Map<String, Integer> applications = new TreeMap<>();
        for (EObject root : r.getContents()) {
            if (root == model) {
                continue;
            }
            applications.merge(root.eClass().getEPackage().getNsPrefix() + ":" + root.eClass().getName(), 1, Integer::sum);
            for (EStructuralFeature f : root.eClass().getEAllStructuralFeatures()) {
                if (f.getName().startsWith("base_")) {
                    Object base = root.eGet(f);
                    if (!(base instanceof EObject b) || b.eIsProxy() || b.eResource() != r) {
                        System.out.println("  STEREOTYPE " + root.eClass().getName() + " without a base element: " + base);
                        problems++;
                    }
                }
            }
        }
        System.out.println("  stereotype applications: " + applications);
        // unresolved references (the SysML profile href is expected: tools resolve it to their own SysML profile)
        Map<EObject, java.util.Collection<EStructuralFeature.Setting>> unresolved = EcoreUtil.UnresolvedProxyCrossReferencer.find(r);
        for (EObject proxy : unresolved.keySet()) {
            String uri = ((org.eclipse.emf.ecore.InternalEObject) proxy).eProxyURI().toString();
            if (uri.startsWith("http://www.omg.org/spec/SysML/")) {
                System.out.println("  (expected) profile reference, resolved by tools with SysML: " + uri);
            } else {
                System.out.println("  UNRESOLVED: " + uri);
                problems++;
            }
        }
        // the things a SysML tool needs to show blocks and connectors
        List<String> structure = new ArrayList<>();
        Map<String, Integer> checks = new HashMap<>();
        model.eAllContents().forEachRemaining(o -> {
            if (o instanceof Property p && !(o instanceof Port) && p.getType() == null && p.getOwner() instanceof Class) {
                structure.add("part property without type: " + p.getQualifiedName());
            }
            if (o instanceof Connector c) {
                checks.merge("connectors", 1, Integer::sum);
                if (c.getEnds().size() != 2) {
                    structure.add("connector without 2 ends: " + c.getQualifiedName());
                }
                for (ConnectorEnd e : c.getEnds()) {
                    if (!(e.getRole() instanceof Port port)) {
                        structure.add("connector end role is not a port: " + c.getQualifiedName());
                    } else if (e.getPartWithPort() != null && e.getPartWithPort().getType() instanceof Class t && !t.getAllAttributes().contains(port)) {
                        structure.add("end port " + port.getName() + " is not a port of the part's type " + t.getName() + " (" + c.getQualifiedName() + ")");
                    } else {
                        checks.merge("connector ends ok", 1, Integer::sum);
                    }
                }
            }
            if (o instanceof Abstraction a && (a.getClients().isEmpty() || a.getSuppliers().isEmpty())) {
                structure.add("abstraction without client/supplier: " + a.getName());
            }
        });
        System.out.println("  connector checks: " + checks);
        for (String s : structure) {
            System.out.println("  STRUCTURE: " + s);
            problems++;
        }
        Diagnostic diagnostic = Diagnostician.INSTANCE.validate(model);
        Map<Integer, Integer> bySeverity = new TreeMap<>();
        List<Diagnostic> flat = new ArrayList<>();
        flatten(diagnostic, flat);
        // the applied SysML profile is a reference to the OMG standard URI, unresolvable without a SysML profile (expected)
        long profileProxy = flat.stream().filter(d -> d.getMessage().contains("unresolved proxy") && d.getMessage().contains("spec/SysML/")).count();
        flat.removeIf(d -> d.getMessage().contains("unresolved proxy") && d.getMessage().contains("spec/SysML/"));
        System.out.println("  (expected) validator reports on the SysML profile reference: " + profileProxy);
        for (Diagnostic d : flat) {
            bySeverity.merge(d.getSeverity(), 1, Integer::sum);
        }
        System.out.println("  UML2 validation (0=OK 1=INFO 2=WARNING 4=ERROR): other findings by severity " + bySeverity);
        flat.stream().filter(d -> d.getSeverity() >= Diagnostic.WARNING).limit(25).forEach(d -> System.out.println("    " + (d.getSeverity() == Diagnostic.ERROR ? "ERROR " : "warn  ") + d.getMessage()));
        if (flat.stream().anyMatch(d -> d.getSeverity() == Diagnostic.ERROR)) {
            problems++;
        }
        System.out.println(problems == 0 ? "  OK" : "  " + problems + " PROBLEM(S)");
        return problems == 0 ? 0 : 1;
    }

    static void flatten(Diagnostic d, List<Diagnostic> out) {
        if (d.getChildren().isEmpty()) {
            if (d.getSeverity() != Diagnostic.OK) {
                out.add(d);
            }
        } else {
            d.getChildren().forEach(c -> flatten(c, out));
        }
    }
}

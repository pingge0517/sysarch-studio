package com.example.sysarch.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.view.generated.IbdDiagramDescriptionProvider;
import com.example.sysarch.view.generated.BddDiagramDescriptionProvider;
import com.example.sysarch.view.projection.DiagramProjector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T9 step 2: the domain-controller demo has exactly the spec's content, is valid, and renders in both views. */
class DomainControllerDemoTest {

    private static ConstraintValidator constraints;

    private Resource document;

    @BeforeAll
    static void loadOcl() {
        constraints = new ConstraintValidator();
    }

    @AfterAll
    static void disposeOcl() {
        constraints.close();
    }

    @BeforeEach
    void setUp() {
        this.document = new ResourceImpl(URI.createURI("memory:/demo.json"));
        this.document.getContents().addAll(DomainControllerDemo.build());
    }

    /** "3 个逻辑部件、6 个端口、4 条连接、5 条需求，跨 LA/PA 两层". */
    @Test
    void hasTheSpecifiedContent() {
        Map<String, Integer> counts = new TreeMap<>();
        TreeSet<Layer> layers = new TreeSet<>();
        for (EObject object : this.all()) {
            counts.merge(object.eClass().getName(), 1, Integer::sum);
            if (object instanceof PartDefinition block) {
                layers.add(block.getLayer());
            }
        }
        assertEquals(Map.of("Allocate", 3, "Connection", 4, "InterfaceDef", 1, "Part", 3, "PartDefinition", 6, "Port", 6, "Requirement", 5), counts);
        assertEquals(new TreeSet<>(List.of(Layer.LOGICAL, Layer.PHYSICAL)), layers);
    }

    @Test
    void violatesNoConstraint() {
        assertEquals(List.of(), constraints.validateAll(this.all()));
    }

    @Test
    void theIbdOfTheDomainControllerShowsItsPartsPortsAndConnections() {
        PartDefinition system = (PartDefinition) this.document.getEObject(DomainControllerDemo.SYSTEM_ID);

        assertEquals("""
                node PT-001 [part] "感知融合" / "感知融合单元 [1]"
                node PT-002 [part] "决策规划" / "决策规划单元 [1]"
                node PT-003 [part] "执行控制" / "执行控制单元 [1]"
                node PO-001 [portOnPart] on PT-001 "objects"
                node PO-002 [portOnPart] on PT-001 "egoState"
                node PO-003 [portOnPart] on PT-002 "perception"
                node PO-004 [portOnPart] on PT-002 "trajectory"
                node PO-005 [portOnPart] on PT-003 "trajectory"
                node PO-006 [portOnPart] on PT-003 "egoState"
                edge PO-001 -> PO-003 [assembly] "ObjectList"
                edge PO-004 -> PO-005 [assembly] "Trajectory"
                edge PO-006 -> PO-002 [assembly] "VehicleState"
                edge PO-006 -> PO-003 [assembly] "VehicleState"
                """, DiagramProjector.project(new IbdDiagramDescriptionProvider().build(), system).describe());
    }

    @Test
    void theBddShowsEveryBlock() {
        PartDefinition system = (PartDefinition) this.document.getEObject(DomainControllerDemo.SYSTEM_ID);

        assertEquals("""
                node PD-001 [block] "域控制器" / "LOGICAL"
                  ports: -
                node PD-002 [block] "感知融合单元" / "LOGICAL"
                  ports: -
                node PD-003 [block] "决策规划单元" / "LOGICAL"
                  ports: -
                node PD-004 [block] "执行控制单元" / "LOGICAL"
                  ports: -
                node PD-005 [block] "域控 SoC" / "PHYSICAL"
                  ports: -
                node PD-006 [block] "安全 MCU" / "PHYSICAL"
                  ports: -
                edge PD-001 -> PD-002 [composition] "感知融合"
                edge PD-001 -> PD-003 [composition] "决策规划"
                edge PD-001 -> PD-004 [composition] "执行控制"
                """, DiagramProjector.project(new BddDiagramDescriptionProvider().build(), system).describe());
    }

    private List<EObject> all() {
        List<EObject> all = new ArrayList<>();
        this.document.getAllContents().forEachRemaining(o -> {
            if (o instanceof ArchElement) {
                all.add(o);
            }
        });
        return all;
    }
}

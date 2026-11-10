package com.example.sysarch.view.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.ElementIds;
import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.model.Allocate;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.view.api.CellCondition;
import com.example.sysarch.view.api.TableDescription;
import com.example.sysarch.view.generated.AllocationMatrixTableDescriptionProvider;
import com.example.sysarch.view.generated.RequirementTraceTableDescriptionProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.Test;

/**
 * P2 T7 "关系预计算" must not change a single cell: the indexed matrix equals the full rows × columns scan, on both real
 * tables, over random relations; conditions that are not anchored keep the full scan.
 */
class MatrixIndexTest {

    @Test
    void theIndexedMatrixEqualsTheFullScan() {
        Random random = new Random(7);
        for (int round = 0; round < 20; round++) {
            Resource model = new ResourceImpl(URI.createURI("memory:/m" + round + ".json"));
            model.getContents().addAll(DomainControllerDemo.build());
            List<PartDefinition> blocks = model.getContents().stream().filter(PartDefinition.class::isInstance).map(PartDefinition.class::cast).toList();
            List<ArchElement> structural = new ArrayList<>(blocks);
            blocks.forEach(b -> structural.addAll(b.getOwnedParts()));
            for (int i = 0; i < 15; i++) {
                Allocate allocate = SysarchFactory.eINSTANCE.createAllocate();
                allocate.setId("AL-R" + i);
                allocate.setName("r" + i);
                allocate.setFrom(random.nextInt(5) == 0 ? null : structural.get(random.nextInt(structural.size())));
                allocate.setTo(structural.get(random.nextInt(structural.size())));
                model.getContents().add(allocate);
            }
            model.getContents().stream().filter(Requirement.class::isInstance).map(Requirement.class::cast).forEach(r -> {
                r.getSatisfiedBy().add(blocks.get(random.nextInt(blocks.size())));
                if (random.nextBoolean()) {
                    r.getVerifiedBy().add(structural.get(random.nextInt(structural.size())));
                }
            });
            for (TableDescription table : List.of(new AllocationMatrixTableDescriptionProvider().build(), new RequirementTraceTableDescriptionProvider().build())) {
                MatrixProjector.Model projected = MatrixProjector.Model.of(table, List.of(model));
                assertEquals(fullScan(projected), ids(MatrixProjector.markedPairs(projected)), table.id() + " round " + round);
            }
        }
    }

    @Test
    void onlyAnchoredConditionsAreIndexed() {
        CellCondition from = CellCondition.equalTo("from", CellCondition.Side.ROW);
        CellCondition to = CellCondition.equalTo("to", CellCondition.Side.COLUMN);
        assertTrue(MatrixProjector.anchored(CellCondition.and(from, to), CellCondition.Side.ROW));
        assertTrue(MatrixProjector.anchored(CellCondition.or(CellCondition.includes("a", CellCondition.Side.COLUMN), to), CellCondition.Side.COLUMN));
        assertFalse(MatrixProjector.anchored(CellCondition.not(from), CellCondition.Side.ROW), "not row: almost every row");
        assertFalse(MatrixProjector.anchored(CellCondition.or(from, to), CellCondition.Side.ROW), "one way to hold says nothing of the row");
    }

    private static List<String> fullScan(MatrixProjector.Model model) {
        List<String> ids = new ArrayList<>();
        for (EObject row : model.rows) {
            for (EObject column : model.columns) {
                if (model.marked(row, column)) {
                    ids.add(ElementIds.of(row) + "→" + ElementIds.of(column));
                }
            }
        }
        return ids;
    }

    private static List<String> ids(List<EObject[]> pairs) {
        return pairs.stream().map(p -> ElementIds.of(p[0]) + "→" + ElementIds.of(p[1])).toList();
    }
}

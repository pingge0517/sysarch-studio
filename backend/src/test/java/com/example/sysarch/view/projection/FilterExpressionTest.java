package com.example.sysarch.view.projection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.model.Layer;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.SysarchFactory;

import org.junit.jupiter.api.Test;

/** The runtime side of the filter grammar (spec P1 §3.5); vsm-compiler's FilterSyntax checks the same cases at build time. */
class FilterExpressionTest {

    private final PartDefinition logical = block(Layer.LOGICAL, false, "ecu");

    private final PartDefinition physicalAbstract = block(Layer.PHYSICAL, true);

    @Test
    void layerIsComparedByLiteral() {
        assertTrue(FilterExpression.parse("layer == 'LOGICAL'").test(this.logical));
        assertFalse(FilterExpression.parse("layer == 'LOGICAL'").test(this.physicalAbstract));
        assertTrue(FilterExpression.parse("layer != 'LOGICAL'").test(this.physicalAbstract));
    }

    @Test
    void stereotypesMatchWhenOneOfThemIsTheLiteral() {
        assertTrue(FilterExpression.parse("stereotypes == 'ecu'").test(this.logical));
        assertFalse(FilterExpression.parse("stereotypes == 'ecu'").test(this.physicalAbstract));
        assertTrue(FilterExpression.parse("stereotypes != 'ecu'").test(this.physicalAbstract));
    }

    @Test
    void p0FlagsAndBooleanOperatorsStillWork() {
        assertTrue(FilterExpression.parse("not isAbstract").test(this.logical));
        assertFalse(FilterExpression.parse("not isAbstract").test(this.physicalAbstract));
        assertTrue(FilterExpression.parse("isAbstract and (layer == 'PHYSICAL' or layer == 'EPBS')").test(this.physicalAbstract));
        assertFalse(FilterExpression.parse("isAbstract and layer == 'LOGICAL'").test(this.physicalAbstract));
    }

    private static PartDefinition block(Layer layer, boolean isAbstract, String... stereotypes) {
        PartDefinition block = SysarchFactory.eINSTANCE.createPartDefinition();
        block.setLayer(layer);
        block.setIsAbstract(isAbstract);
        block.getStereotypes().addAll(java.util.List.of(stereotypes));
        return block;
    }
}

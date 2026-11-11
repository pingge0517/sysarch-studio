package com.example.sysarch.view.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.sysarch.model.Parameter;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.model.Transition;

import org.junit.jupiter.api.Test;

/** P3 T5: label templates leave out empty segments (spec "空段自动省略，不留空方括号"). */
class LabelTemplatesTest {

    private static final String TRANSITION = "{trigger}[{guard}]/{effect}";

    @Test
    void transitionLabelsHaveNoEmptySegments() {
        assertEquals("a[g]/e", render("a", "g", "e"));
        assertEquals("a[g]", render("a", "g", null));
        assertEquals("a/e", render("a", null, "e"));
        assertEquals("a", render("a", "", ""));
        assertEquals("[g]/e", render(null, "g", "e"));
        assertEquals("[g]", render(null, "g", null));
        assertEquals("/e", render(null, null, "e"));
        assertEquals("", render(null, null, null));
    }

    @Test
    void otherTemplatesKeepTheirTextWhenEverythingIsSet() {
        Parameter p = SysarchFactory.eINSTANCE.createParameter();
        p.setDirection("IN");
        p.setName("U");
        p.setUnit("V");
        assertEquals("IN U : V", LabelTemplates.render("{direction} {name} : {unit}", p));
        p.setUnit(null);
        assertEquals("IN U", LabelTemplates.render("{direction} {name} : {unit}", p), "no dangling ' : '");
        p.setDirection(null);
        assertEquals("U", LabelTemplates.render("{direction} {name} : {unit}", p), "no leading space");
        assertEquals("参数", LabelTemplates.render("参数", p), "a template without fields is literal");
        assertEquals("", LabelTemplates.render("", p));
        assertEquals("<<U>>", LabelTemplates.render("<<{name}>>", p), "text around a single field");
        p.setName("");
        assertEquals("", LabelTemplates.render("<<{name}>>", p));
    }

    @Test
    void fullWidthBracketsCloseTheirField() {
        Parameter p = SysarchFactory.eINSTANCE.createParameter();
        p.setName("U");
        assertEquals("U", LabelTemplates.render("{name}（{unit}）", p));
        p.setUnit("V");
        assertEquals("U（V）", LabelTemplates.render("{name}（{unit}）", p));
    }

    private static String render(String trigger, String guard, String effect) {
        Transition t = SysarchFactory.eINSTANCE.createTransition();
        t.setTrigger(trigger);
        t.setGuard(guard);
        t.setEffect(effect);
        return LabelTemplates.render(TRANSITION, t);
    }
}

package com.example.sysarch.sysmlimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.model.Layer;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Requirement;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * P3 T8 step 1 (spec §5.2): only our own XMI and a minimal Cameo export are accepted; anything else fails the whole
 * import and every refused element is listed. Plus: no DTD (XXE), not XMI at all.
 */
class XmiImportTest {

    /** What a minimal Cameo export looks like: packages, MagicDraw extensions, other profiles' stereotypes, «Block», a part. */
    private static final String CAMEO = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xmi:XMI xmlns:xmi="http://www.omg.org/spec/XMI/20131001" xmlns:uml="http://www.omg.org/spec/UML/20161101"
                     xmlns:sysml="http://www.omg.org/spec/SysML/20181001/SysML" xmlns:MD_Customization_for_SysML__additional_stereotypes="http://www.magicdraw.com/spec/Customization/190/SysML">
              <xmi:Documentation><xmi:exporter>MagicDraw Clean XMI Exporter</xmi:exporter></xmi:Documentation>
              <uml:Model xmi:type="uml:Model" xmi:id="eee_1045467100313_135436_1" name="Model">
                <xmi:Extension extender="MagicDraw UML 2021x"><modelExtension/></xmi:Extension>
                <packagedElement xmi:type="uml:Package" xmi:id="_pkg" name="Structure">
                  <packagedElement xmi:type="uml:Class" xmi:id="_car" name="Car">
                    <ownedAttribute xmi:type="uml:Property" xmi:id="_eng" name="engine" aggregation="composite">
                      <type xmi:idref="_engine"/>
                      <lowerValue xmi:type="uml:LiteralInteger" xmi:id="_l" value="1"/>
                      <upperValue xmi:type="uml:LiteralUnlimitedNatural" xmi:id="_u" value="1"/>
                    </ownedAttribute>
                    <ownedComment xmi:type="uml:Comment" xmi:id="_c" body="A car."><annotatedElement xmi:idref="_car"/></ownedComment>
                  </packagedElement>
                  <packagedElement xmi:type="uml:Class" xmi:id="_engine" name="Engine"/>
                  <packagedElement xmi:type="uml:Class" xmi:id="_req" name="Power"/>
                  <packagedElement xmi:type="uml:Abstraction" xmi:id="_sat"><client xmi:idref="_engine"/><supplier xmi:idref="_req"/></packagedElement>
                </packagedElement>
              </uml:Model>
              <sysml:Block xmi:id="_b1" base_Class="_car"/>
              <sysml:Block xmi:id="_b2" base_Class="_engine"/>
              <sysml:Requirement xmi:id="_r1" base_Class="_req" id="R-1" text="Enough power."/>
              <sysml:Satisfy xmi:id="_s1" base_Abstraction="_sat"/>
              <MD_Customization_for_SysML__additional_stereotypes:PartProperty xmi:id="_pp" base_Property="_eng"/>
            </xmi:XMI>
            """;

    @Test
    void aMinimalCameoExportIsAccepted() {
        XmiImporter.Result result = XmiImporter.read(CAMEO, Layer.SYSTEM);
        assertEquals(List.of(), result.problems());
        var roots = result.mapped().roots();
        PartDefinition car = (PartDefinition) roots.get(0);
        assertEquals("Car", car.getName());
        assertEquals("_car", car.getId(), "Cameo ids are kept");
        assertEquals("A car.", car.getDescription());
        assertEquals(Layer.SYSTEM, car.getLayer(), "no extension: the default layer");
        Part engine = car.getOwnedParts().get(0);
        assertEquals("Engine", engine.getTypedBy().getName());
        assertEquals("1", engine.getMultiplicity());
        Requirement power = (Requirement) roots.get(2);
        assertEquals("R-1", power.getReqId());
        assertEquals(List.of(roots.get(1)), power.getSatisfiedBy());
        assertEquals(4, result.mapped().defaultedLayer().size(), "listed: 3 classes and the part");
        assertTrue(result.ignored().stream().anyMatch(i -> i.contains("PartProperty")), "another profile's stereotype is ignored and reported: " + result.ignored());
        assertEquals("Model", result.mapped().packageName());
    }

    @Test
    void anythingElseFailsTheWholeImportAndIsListed() {
        String foreign = CAMEO.replace("<packagedElement xmi:type=\"uml:Class\" xmi:id=\"_engine\" name=\"Engine\"/>", """
                <packagedElement xmi:type="uml:Class" xmi:id="_engine" name="Engine">
                  <ownedAttribute xmi:type="uml:Property" xmi:id="_mass" name="mass"><type href="http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#Real"/></ownedAttribute>
                  <ownedOperation xmi:type="uml:Operation" xmi:id="_op" name="start"/>
                </packagedElement>
                <packagedElement xmi:type="uml:Activity" xmi:id="_act" name="Drive"/>
                <packagedElement xmi:type="uml:StateMachine" xmi:id="_sm" name="Modes"/>
                <packagedElement xmi:type="uml:Class" xmi:id="_plain" name="Plain"/>
                <packagedElement xmi:type="uml:Class" xmi:id="_cb" name="Newton"/>
                <packagedElement xmi:type="uml:Dependency" xmi:id="_dep"/>
                """).replace("<sysml:Block xmi:id=\"_b2\" base_Class=\"_engine\"/>", "<sysml:Block xmi:id=\"_b2\" base_Class=\"_engine\"/>\n<sysml:ConstraintBlock xmi:id=\"_cbs\" base_Class=\"_cb\"/>");
        XmiImporter.Result result = XmiImporter.read(foreign, Layer.LOGICAL);
        assertNull(result.mapped(), "nothing is imported");
        String all = String.join("\n", result.problems());
        for (String expected : List.of("uml:Activity「Drive」（_act", "uml:StateMachine「Modes」（_sm", "uml:Dependency「」（_dep", "uml:Operation「start」（_op",
                "不支持的属性「mass」（_mass）", "不支持的类「Plain」（_plain）：uml:Class 必须带", "不支持的类「Newton」（_cb）：构造型 «ConstraintBlock» 不在导入范围内")) {
            assertTrue(all.contains(expected), "listed: " + expected + "\n" + all);
        }
        assertEquals(7, result.problems().size(), all);
    }

    @Test
    void aDoctypeIsRefusedSoNoEntityIsResolved() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///C:/Windows/win.ini\">]><xmi:XMI xmlns:xmi=\"http://www.omg.org/spec/XMI/20131001\">&e;</xmi:XMI>";
        XmiImporter.Result result = XmiImporter.read(xxe, Layer.LOGICAL);
        assertTrue(result.problems().get(0).startsWith("不是可读的 XML"), result.problems().toString());
    }

    @Test
    void somethingThatIsNotXmiIsRefused() {
        assertEquals(List.of("不是 XMI 文件：根元素应为 xmi:XMI（XMI 2.5.1）"),
                XmiImporter.read("<?xml version=\"1.0\"?><model/>", Layer.LOGICAL).problems());
        assertTrue(XmiImporter.read("part def A;", Layer.LOGICAL).problems().get(0).startsWith("不是可读的 XML"));
    }
}

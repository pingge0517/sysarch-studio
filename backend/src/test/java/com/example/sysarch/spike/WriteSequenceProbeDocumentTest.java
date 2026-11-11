package com.example.sysarch.spike;

import com.example.sysarch.model.State;
import com.example.sysarch.model.StateMachine;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.model.Transition;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.junit.jupiter.api.Test;

/** SPIKE, one-off: 3 lifelines, 6 messages (spec §6.1's Go criterion) as a Sirius JSON document. */
class WriteSequenceProbeDocumentTest {

    @Test
    void write() throws Exception {
        var f = SysarchFactory.eINSTANCE;
        StateMachine m = f.createStateMachine();
        m.setId("SQ");
        m.setName("开门交互");
        State driver = state(m, "LL-1", "驾驶员");
        State door = state(m, "LL-2", "车门控制器");
        State body = state(m, "LL-3", "车身域");
        String[][] messages = { { "1", "按下开门键" }, { "2", "请求解锁" }, { "3", "解锁确认" }, { "4", "开门" }, { "5", "门已开" }, { "6", "提示" } };
        State[][] ends = { { driver, door }, { door, body }, { body, door }, { door, door }, { door, driver }, { body, driver } };
        for (int i = 0; i < 6; i++) {
            Transition t = f.createTransition();
            t.setId("MSG-" + messages[i][0]);
            t.setName(messages[i][1]);
            t.setTrigger(messages[i][1]);
            t.setSource(ends[i][0]);
            t.setTarget(ends[i][1]);
            m.getTransitions().add(t);
        }
        var set = new ResourceSetImpl();
        set.getPackageRegistry().put(SysarchPackage.eNS_URI, SysarchPackage.eINSTANCE);
        Resource r = new JSONResourceFactory().createResourceFromPath("sequence-probe");
        set.getResources().add(r);
        r.getContents().addAll(List.of(m));
        var out = new ByteArrayOutputStream();
        r.save(out, Map.of());
        Files.writeString(Path.of("build/sequence-probe.json"), out.toString(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    private static State state(StateMachine m, String id, String name) {
        State s = SysarchFactory.eINSTANCE.createState();
        s.setId(id);
        s.setName(name);
        m.getStates().add(s);
        return s;
    }
}

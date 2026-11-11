package com.example.sysarch.sequence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.model.Interaction;
import com.example.sysarch.model.Lifeline;
import com.example.sysarch.model.Message;
import com.example.sysarch.model.MessageKind;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.report.ReportConfiguration;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;

/**
 * Probe A's fallback: an interaction drawn as a read-only sequence picture. 3 lifelines and 6 messages (spec §6.1's
 * Go numbers), every message kind and a message to itself; a broken message is listed, not drawn. Writes
 * build/sequence/door.svg and door.png.
 */
class SequenceRendererTest {

    private static final SysarchFactory F = SysarchFactory.eINSTANCE;

    static Interaction door() {
        Interaction interaction = F.createInteraction();
        interaction.setId("SQ-1");
        interaction.setName("开门");
        PartDefinition controller = F.createPartDefinition();
        controller.setName("车门控制器");
        Lifeline driver = lifeline("LL-1", "驾驶员", null);
        Lifeline door = lifeline("LL-2", "门控", controller);
        Lifeline body = lifeline("LL-3", "车身域", null);
        interaction.getLifelines().addAll(java.util.List.of(driver, door, body));
        interaction.getMessages().add(message("M-1", "按下开门键", MessageKind.ASYNC, driver, door));
        interaction.getMessages().add(message("M-2", "请求解锁()", MessageKind.SYNC, door, body));
        interaction.getMessages().add(message("M-3", "解锁成功", MessageKind.REPLY, body, door));
        interaction.getMessages().add(message("M-4", "自检", MessageKind.SYNC, door, door));
        interaction.getMessages().add(message("M-5", "门已开", MessageKind.ASYNC, door, driver));
        interaction.getMessages().add(message("M-6", "提示音", MessageKind.ASYNC, body, driver));
        interaction.getMessages().add(message("M-7", "无效", MessageKind.SYNC, null, door)); // listed, not drawn
        return interaction;
    }

    private static Lifeline lifeline(String id, String name, PartDefinition represents) {
        Lifeline l = F.createLifeline();
        l.setId(id);
        l.setName(name);
        l.setRepresents(represents);
        return l;
    }

    private static Message message(String id, String name, MessageKind kind, Lifeline sender, Lifeline receiver) {
        Message m = F.createMessage();
        m.setId(id);
        m.setName(name);
        m.setKind(kind);
        m.setSender(sender);
        m.setReceiver(receiver);
        return m;
    }

    private final SequenceRenderer renderer = new SequenceRenderer(ReportConfiguration.defaultFonts());

    @Test
    void theSvgShowsLifelinesMessagesKindsAndTheBrokenMessage() throws Exception {
        String svg = this.renderer.svg(door());
        Files.createDirectories(Path.of("build/sequence"));
        Files.writeString(Path.of("build/sequence/door.svg"), svg, StandardCharsets.UTF_8);
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8))); // well-formed
        assertTrue(svg.contains(">sd 开门</text>"), "UML frame tab");
        for (String header : new String[] { ">驾驶员</text>", ">门控 : 车门控制器</text>", ">车身域</text>" }) {
            assertTrue(svg.contains(header), header);
        }
        for (int i = 1; i <= 6; i++) {
            assertTrue(svg.contains(">" + i + ": "), "message " + i);
        }
        assertTrue(svg.contains(">未画出 7: 无效：缺少发送方或接收方</text>"), "the broken message is listed");
        assertEquals(2, count(svg, "fill=\"#1A2B4A\" stroke"), "filled heads: the two SYNC messages");
        assertTrue(count(svg, "stroke-dasharray") >= 3 + 1, "dashed: 3 lifelines and the REPLY");
        assertEquals(svg, this.renderer.svg(door()), "deterministic");
    }

    @Test
    void thePngHasTheSameLayoutAndTextIsDrawn() throws Exception {
        byte[] png = this.renderer.png(door());
        Files.createDirectories(Path.of("build/sequence"));
        Files.write(Path.of("build/sequence/door.png"), png);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        SequenceRenderer.Layout layout = this.renderer.layout(door());
        assertEquals(layout.width(), image.getWidth());
        assertEquals(layout.height(), image.getHeight());
        assertTrue(darkPixels(image, layout.columns().get(1).x() - 40, 64, 80, 30) > 20, "the header text of the second lifeline is drawn");
        assertArrayEquals(png, this.renderer.png(door()), "deterministic");
    }

    /** Metamodel v1.4: an interaction is stored by Sirius' JSON resource and loads back drawing the same picture. */
    @Test
    void anInteractionSavesAndLoadsBack() throws Exception {
        var set = new org.eclipse.emf.ecore.resource.impl.ResourceSetImpl();
        set.getPackageRegistry().put(com.example.sysarch.model.SysarchPackage.eNS_URI, com.example.sysarch.model.SysarchPackage.eINSTANCE);
        var json = new org.eclipse.sirius.components.emf.services.JSONResourceFactory().createResourceFromPath("door");
        set.getResources().add(json);
        Interaction original = door();
        json.getContents().add(original);
        json.getContents().add(original.getLifelines().get(1).getRepresents()); // the block it represents is a root too
        var bytes = new java.io.ByteArrayOutputStream();
        json.save(bytes, java.util.Map.of());
        Files.writeString(Path.of("build/sequence/door-document.json"), bytes.toString(StandardCharsets.UTF_8), StandardCharsets.UTF_8); // for live checks
        var in = new org.eclipse.emf.ecore.resource.impl.ResourceSetImpl();
        in.getPackageRegistry().put(com.example.sysarch.model.SysarchPackage.eNS_URI, com.example.sysarch.model.SysarchPackage.eINSTANCE);
        var loaded = new org.eclipse.sirius.components.emf.services.JSONResourceFactory().createResourceFromPath("loaded");
        in.getResources().add(loaded);
        loaded.load(new ByteArrayInputStream(bytes.toByteArray()), java.util.Map.of());
        Interaction back = (Interaction) loaded.getContents().get(0);
        assertEquals(7, back.getMessages().size());
        assertEquals("门控", back.getMessages().get(1).getSender().getName());
        assertEquals(MessageKind.REPLY, back.getMessages().get(2).getKind());
        assertEquals(this.renderer.svg(door()), this.renderer.svg(back), "same picture after a reload");
    }

    @Test
    void anEmptyInteractionStillDrawsItsFrame() {
        Interaction empty = F.createInteraction();
        empty.setName("空");
        assertTrue(this.renderer.svg(empty).contains(">sd 空</text>"));
    }

    private static int darkPixels(BufferedImage image, int x0, int y0, int w, int h) {
        int n = 0;
        for (int x = Math.max(0, x0); x < Math.min(image.getWidth(), x0 + w); x++) {
            for (int y = y0; y < Math.min(image.getHeight(), y0 + h); y++) {
                if ((image.getRGB(x, y) & 0xFF) < 128) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int count(String text, String what) {
        return text.split(java.util.regex.Pattern.quote(what), -1).length - 1;
    }
}

package com.example.sysarch.sequence;

import com.example.sysarch.model.Interaction;
import com.example.sysarch.model.Lifeline;
import com.example.sysarch.model.Message;
import com.example.sysarch.model.MessageKind;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

/**
 * Probe A's fallback (spec P3 §6.1 No-Go: "时序图降级为只读导出 PNG"): draws an {@link Interaction} as a read-only UML
 * sequence picture, SVG or PNG, with plain Java (no renderer to install). One layout, drawn through {@link Canvas} twice.
 *
 * <ul>
 * <li>a UML frame with the tab "sd &lt;name&gt;";</li>
 * <li>a header box per lifeline ("name : represented element" when it represents one), a dashed line below;</li>
 * <li>one row per message in time order: SYNC solid with a filled head, ASYNC solid with an open head, REPLY dashed
 * with an open head; a message to the same lifeline is a small loop; the label is "i: name" above the line;</li>
 * <li>activation bars where a lifeline sends or receives, consecutive rows joined;</li>
 * <li>messages that cannot be drawn (an end missing or not a lifeline of this interaction) are listed under the frame.</li>
 * </ul>
 * Deterministic: same model, same bytes (SVG) / pixels (PNG).
 */
public final class SequenceRenderer {

    private static final int MARGIN = 20;

    private static final int TAB_HEIGHT = 24;

    private static final int HEADER_TOP = MARGIN + TAB_HEIGHT + 16;

    private static final int HEADER_HEIGHT = 36;

    private static final int FIRST_ROW = HEADER_TOP + HEADER_HEIGHT + 34;

    private static final int ROW = 42;

    private static final int MIN_COLUMN = 150;

    private static final int FONT_SIZE = 14;

    private static final String INK = "#1A2B4A";

    private final Font font;

    /** @param fonts candidate TrueType fonts with CJK glyphs (the report's {@code sysarch.report.fonts}); the first that exists */
    public SequenceRenderer(List<Path> fonts) {
        Font chosen = new Font(Font.SANS_SERIF, Font.PLAIN, FONT_SIZE);
        for (Path path : fonts) {
            if (Files.isRegularFile(path)) {
                try {
                    chosen = Font.createFont(Font.TRUETYPE_FONT, path.toFile()).deriveFont(Font.PLAIN, FONT_SIZE);
                    break;
                } catch (Exception e) {
                    // not a usable TrueType font: try the next
                }
            }
        }
        this.font = chosen;
    }

    public String svg(Interaction interaction) {
        SvgCanvas canvas = new SvgCanvas();
        Layout layout = this.layout(interaction);
        canvas.open(layout.width, layout.height);
        draw(layout, canvas);
        return canvas.close();
    }

    public byte[] png(Interaction interaction) {
        Layout layout = this.layout(interaction);
        BufferedImage image = new BufferedImage(layout.width, layout.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, layout.width, layout.height);
        draw(layout, new Java2dCanvas(g, this.font));
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ layout

    record Column(String label, int x) {
    }

    record Row(int index, String label, MessageKind kind, int from, int to, int y) {
    }

    record Layout(String title, List<Column> columns, List<Row> rows, List<String> notDrawn, int[] columnWidths, int width, int height, int bottom) {
    }

    Layout layout(Interaction interaction) {
        FontMetrics metrics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics().getFontMetrics(this.font);
        List<Lifeline> lifelines = interaction.getLifelines();
        List<Column> columns = new ArrayList<>();
        int[] widths = new int[lifelines.size()];
        int x = MARGIN + 10;
        for (int i = 0; i < lifelines.size(); i++) {
            Lifeline lifeline = lifelines.get(i);
            String label = nonNull(lifeline.getName()) + (lifeline.getRepresents() == null ? "" : " : " + nonNull(lifeline.getRepresents().getName()));
            widths[i] = Math.max(MIN_COLUMN, metrics.stringWidth(label) + 30);
            columns.add(new Column(label, x + widths[i] / 2));
            x += widths[i];
        }
        List<Row> rows = new ArrayList<>();
        List<String> notDrawn = new ArrayList<>();
        int number = 0;
        for (Message message : interaction.getMessages()) {
            number++;
            int from = lifelines.indexOf(message.getSender());
            int to = lifelines.indexOf(message.getReceiver());
            String label = number + ": " + nonNull(message.getName());
            if (from < 0 || to < 0) {
                notDrawn.add("未画出 " + label + "：" + (message.getSender() == null || message.getReceiver() == null ? "缺少发送方或接收方" : "端点不是本交互的生命线"));
                continue;
            }
            rows.add(new Row(number, label, message.getKind(), from, to, FIRST_ROW + rows.size() * ROW));
            int labelRight = Math.min(columns.get(from).x(), columns.get(to).x()) + metrics.stringWidth(label) + 20;
            x = Math.max(x, labelRight);
        }
        int bottom = FIRST_ROW + Math.max(1, rows.size()) * ROW;
        int width = Math.max(x + MARGIN + 10, MARGIN * 2 + metrics.stringWidth("sd " + nonNull(interaction.getName())) + 60);
        for (String note : notDrawn) {
            width = Math.max(width, MARGIN * 2 + metrics.stringWidth(note));
        }
        int height = bottom + MARGIN + 10 + notDrawn.size() * 20;
        return new Layout("sd " + nonNull(interaction.getName()), columns, rows, notDrawn, widths, width, height, bottom);
    }

    // ------------------------------------------------------------------ drawing (one routine, two canvases)

    interface Canvas {
        void rect(int x, int y, int w, int h, boolean filledWhite);

        void line(int x1, int y1, int x2, int y2, boolean dashed);

        void polygon(int[] xs, int[] ys, boolean filled);

        /** anchor: "start", "middle" */
        void text(int x, int y, String text, String anchor, boolean bold);
    }

    static void draw(Layout layout, Canvas c) {
        // frame with the "sd" tab
        int frameBottom = layout.bottom + 10;
        c.rect(MARGIN, MARGIN, layout.width - 2 * MARGIN, frameBottom - MARGIN, false);
        int tabWidth = Math.max(80, layout.title.length() * 9 + 30);
        c.polygon(new int[] { MARGIN, MARGIN + tabWidth, MARGIN + tabWidth, MARGIN + tabWidth - 10, MARGIN },
                new int[] { MARGIN, MARGIN, MARGIN + TAB_HEIGHT - 10, MARGIN + TAB_HEIGHT, MARGIN + TAB_HEIGHT }, false);
        c.text(MARGIN + 8, MARGIN + 17, layout.title, "start", true);
        // lifelines
        for (int i = 0; i < layout.columns.size(); i++) {
            Column column = layout.columns.get(i);
            int w = layout.columnWidths[i] - 20;
            c.rect(column.x() - w / 2, HEADER_TOP, w, HEADER_HEIGHT, true);
            c.text(column.x(), HEADER_TOP + 23, column.label(), "middle", false);
            c.line(column.x(), HEADER_TOP + HEADER_HEIGHT, column.x(), layout.bottom, true);
        }
        // activation bars: rows where the lifeline takes part, consecutive rows joined
        for (int i = 0; i < layout.columns.size(); i++) {
            int x = layout.columns.get(i).x();
            Integer start = null;
            int end = 0;
            for (Row row : layout.rows) {
                boolean active = row.from() == i || row.to() == i;
                if (active && start == null) {
                    start = row.y() - 12;
                }
                if (active) {
                    end = row.y() + 12;
                } else if (start != null) {
                    c.rect(x - 5, start, 10, end - start, true);
                    start = null;
                }
            }
            if (start != null) {
                c.rect(x - 5, start, 10, end - start, true);
            }
        }
        // messages
        for (Row row : layout.rows) {
            int x1 = layout.columns.get(row.from()).x();
            int x2 = layout.columns.get(row.to()).x();
            boolean dashed = row.kind() == MessageKind.REPLY;
            boolean filled = row.kind() == MessageKind.SYNC;
            if (row.from() == row.to()) { // to itself: a small loop on the right
                int r = x1 + 5;
                c.line(r, row.y() - 8, r + 30, row.y() - 8, dashed);
                c.line(r + 30, row.y() - 8, r + 30, row.y() + 8, dashed);
                c.line(r + 30, row.y() + 8, r, row.y() + 8, dashed);
                head(c, r, row.y() + 8, -1, filled);
                c.text(r + 36, row.y() - 4, row.label(), "start", false);
                continue;
            }
            int direction = x2 > x1 ? 1 : -1;
            int start = x1 + 5 * direction;
            int end = x2 - 5 * direction;
            c.line(start, row.y(), end, row.y(), dashed);
            head(c, end, row.y(), direction, filled);
            c.text((x1 + x2) / 2, row.y() - 6, row.label(), "middle", false);
        }
        int y = frameBottom + 22;
        for (String note : layout.notDrawn) {
            c.text(MARGIN, y, note, "start", false);
            y += 20;
        }
    }

    private static void head(Canvas c, int x, int y, int direction, boolean filled) {
        int[] xs = { x, x - 10 * direction, x - 10 * direction };
        int[] ys = { y, y - 5, y + 5 };
        if (filled) {
            c.polygon(xs, ys, true);
        } else {
            c.line(x, y, x - 10 * direction, y - 5, false);
            c.line(x, y, x - 10 * direction, y + 5, false);
        }
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }

    // ------------------------------------------------------------------ canvases

    static final class SvgCanvas implements Canvas {
        private final StringBuilder out = new StringBuilder();

        void open(int width, int height) {
            this.out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width).append("\" height=\"").append(height)
                    .append("\" viewBox=\"0 0 ").append(width).append(' ').append(height).append("\" font-family=\"'Microsoft YaHei', DengXian, 'Noto Sans CJK SC', 'Droid Sans Fallback', sans-serif\" font-size=\"")
                    .append(FONT_SIZE).append("\">\n<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n");
        }

        String close() {
            return this.out.append("</svg>\n").toString();
        }

        public void rect(int x, int y, int w, int h, boolean filledWhite) {
            this.out.append(String.format(Locale.ROOT, "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"%s\" stroke=\"%s\" stroke-width=\"1.2\"/>%n", x, y, w, h,
                    filledWhite ? "#FFFFFF" : "none", INK));
        }

        public void line(int x1, int y1, int x2, int y2, boolean dashed) {
            this.out.append(String.format(Locale.ROOT, "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"1.2\"%s/>%n", x1, y1, x2, y2, INK,
                    dashed ? " stroke-dasharray=\"5 4\"" : ""));
        }

        public void polygon(int[] xs, int[] ys, boolean filled) {
            StringBuilder points = new StringBuilder();
            for (int i = 0; i < xs.length; i++) {
                points.append(i == 0 ? "" : " ").append(xs[i]).append(',').append(ys[i]);
            }
            this.out.append(String.format(Locale.ROOT, "<polygon points=\"%s\" fill=\"%s\" stroke=\"%s\" stroke-width=\"1.2\"/>%n", points, filled ? INK : "#FFFFFF", INK));
        }

        public void text(int x, int y, String text, String anchor, boolean bold) {
            this.out.append(String.format(Locale.ROOT, "<text x=\"%d\" y=\"%d\" text-anchor=\"%s\" fill=\"%s\"%s>%s</text>%n", x, y, anchor, INK,
                    bold ? " font-weight=\"bold\"" : "", escape(text)));
        }

        private static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        }
    }

    static final class Java2dCanvas implements Canvas {
        private final Graphics2D g;

        private final Font font;

        private static final Color COLOR = Color.decode(INK);

        private static final Stroke SOLID = new BasicStroke(1.2f);

        private static final Stroke DASHED = new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 5f, 4f }, 0f);

        Java2dCanvas(Graphics2D g, Font font) {
            this.g = g;
            this.font = font;
        }

        public void rect(int x, int y, int w, int h, boolean filledWhite) {
            if (filledWhite) {
                this.g.setColor(Color.WHITE);
                this.g.fillRect(x, y, w, h);
            }
            this.g.setColor(COLOR);
            this.g.setStroke(SOLID);
            this.g.drawRect(x, y, w, h);
        }

        public void line(int x1, int y1, int x2, int y2, boolean dashed) {
            this.g.setColor(COLOR);
            this.g.setStroke(dashed ? DASHED : SOLID);
            this.g.drawLine(x1, y1, x2, y2);
        }

        public void polygon(int[] xs, int[] ys, boolean filled) {
            this.g.setColor(filled ? COLOR : Color.WHITE);
            this.g.fillPolygon(xs, ys, xs.length);
            this.g.setColor(COLOR);
            this.g.setStroke(SOLID);
            this.g.drawPolygon(xs, ys, xs.length);
        }

        public void text(int x, int y, String text, String anchor, boolean bold) {
            Font f = bold ? this.font.deriveFont(Font.BOLD) : this.font;
            this.g.setFont(f);
            this.g.setColor(COLOR);
            int width = this.g.getFontMetrics(f).stringWidth(text);
            this.g.drawString(text, "middle".equals(anchor) ? x - width / 2 : x, y);
        }
    }
}

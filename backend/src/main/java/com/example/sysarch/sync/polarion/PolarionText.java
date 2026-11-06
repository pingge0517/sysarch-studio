package com.example.sysarch.sync.polarion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Polarion's rich-text descriptions. Polarion 2512 stores a description sent as {@code text/plain} as
 * {@code text/html}: {@code <}, {@code >} and {@code &} escaped, line breaks as {@code <br/>}, runs of spaces as
 * {@code " &nbsp;"} (verified against the local install). {@link #toPlain} reverses that, so a Requirement's text
 * reads back exactly as it was written.
 */
public final class PolarionText {

    private static final Pattern BREAK = Pattern.compile("(?i)<br\\s*/?>|</p>\\s*<p[^>]*>");

    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private static final Pattern NUMERIC = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);");

    private PolarionText() {
    }

    /** The text of a description value: plain text as is, HTML as the plain text it renders. */
    public static String toPlain(String type, String value) {
        if (value == null || !"text/html".equalsIgnoreCase(type)) {
            return value;
        }
        String text = BREAK.matcher(value).replaceAll("\n");
        text = TAG.matcher(text).replaceAll("");
        text = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'");
        Matcher numeric = NUMERIC.matcher(text);
        StringBuilder decoded = new StringBuilder();
        while (numeric.find()) {
            String code = numeric.group(1);
            int codePoint = code.startsWith("x") ? Integer.parseInt(code.substring(1), 16) : Integer.parseInt(code);
            numeric.appendReplacement(decoded, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
        }
        numeric.appendTail(decoded);
        return decoded.toString().replace("&amp;", "&"); // last, so "&amp;lt;" stays the literal text "&lt;"
    }
}

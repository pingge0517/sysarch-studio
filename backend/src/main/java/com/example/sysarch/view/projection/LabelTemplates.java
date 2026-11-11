package com.example.sysarch.view.projection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Renders VSM label templates: {@code {feature}} and {@code {ref.feature}} are replaced with model values,
 * everything else is literal. vsm-compiler has already checked every path against the metamodel.
 *
 * <p>Values: missing or null gives an empty string, enums give their literal, referenced elements give their
 * {@code name}, and many-valued features are joined with {@code ", "}.
 *
 * <p><b>Empty segments are left out</b> (P3 T5, spec "空段自动省略，不留空方括号"). Each field owns the literal text
 * around it: the text between two fields belongs to the following field, except closing brackets ({@code ) ] } >} and
 * their full-width forms) at its start, which close the preceding field; text before the first field belongs to it, text
 * after the last to the last. A field whose value is empty is dropped together with the text it owns, and the result is
 * trimmed. So {@code {trigger}[{guard}]/{effect}} gives {@code a[g]/e}, {@code a/e}, {@code [g]}, {@code a} or nothing,
 * and {@code {direction} {name} : {unit}} without a unit gives {@code IN P}. A template without fields is literal.
 */
public final class LabelTemplates {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]*)}");

    private LabelTemplates() {
    }

    public static String render(String template, EObject element) {
        if (template == null) {
            return null;
        }
        List<String> literals = new ArrayList<>();
        List<String> values = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        int last = 0;
        while (matcher.find()) {
            literals.add(template.substring(last, matcher.start()));
            values.add(text(resolve(element, matcher.group(1).trim())));
            last = matcher.end();
        }
        if (values.isEmpty()) {
            return template;
        }
        literals.add(template.substring(last));
        return join(literals, values);
    }

    /**
     * @param literals the text before, between and after the fields ({@code values.size() + 1} entries)
     */
    static String join(List<String> literals, List<String> values) {
        int n = values.size();
        String[] prefix = new String[n];
        String[] suffix = new String[n];
        prefix[0] = literals.get(0);
        suffix[n - 1] = literals.get(n);
        for (int i = 1; i < n; i++) {
            String between = literals.get(i);
            int close = 0;
            while (close < between.length() && CLOSING.indexOf(between.charAt(close)) >= 0) {
                close++;
            }
            suffix[i - 1] = between.substring(0, close);
            prefix[i] = between.substring(close);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (!values.get(i).isEmpty()) {
                out.append(prefix[i]).append(values.get(i)).append(suffix[i]);
            }
        }
        return out.toString().strip();
    }

    private static final String CLOSING = ")]}>）】」』》";

    private static Object resolve(EObject element, String path) {
        Object current = element;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof EObject eObject)) {
                return null;
            }
            EStructuralFeature feature = eObject.eClass().getEStructuralFeature(segment);
            if (feature == null) {
                return null;
            }
            current = eObject.eGet(feature);
        }
        return current;
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Collection<?> values) {
            return values.stream().map(LabelTemplates::text).collect(Collectors.joining(", "));
        }
        if (value instanceof Enumerator enumerator) {
            return enumerator.getLiteral();
        }
        if (value instanceof EObject eObject) {
            EStructuralFeature name = eObject.eClass().getEStructuralFeature("name");
            return name == null ? "" : text(eObject.eGet(name));
        }
        return String.valueOf(value);
    }
}

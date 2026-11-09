package com.example.sysarch.view.projection;

import java.util.Collection;
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
 */
public final class LabelTemplates {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]*)}");

    private LabelTemplates() {
    }

    public static String render(String template, EObject element) {
        if (template == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(text(resolve(element, matcher.group(1).trim()))));
        }
        matcher.appendTail(out);
        return out.toString();
    }

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

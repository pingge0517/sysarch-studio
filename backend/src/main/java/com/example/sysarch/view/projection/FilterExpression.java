package com.example.sysarch.view.projection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * VSM filter expression: a boolean over the element's features, e.g. {@code not isAbstract} or
 * {@code layer == 'SYSTEM'}. An active filter keeps the elements for which the expression is true and hides the
 * others; the model is never changed (spec §5.1 invariant 3).
 *
 * <p>Grammar (spec P1 §3.5; vsm-compiler's {@code FilterSyntax} checks the same grammar at build time, including the
 * whitelist {@code layer}, {@code isAbstract}, {@code stereotypes}, so here every expression is already valid):
 * <pre>
 * expr       := and ('or' and)*
 * and        := unary ('and' unary)*
 * unary      := 'not' unary | '(' expr ')' | 'true' | 'false' | feature ('==' | '!=') 'literal' | feature
 * </pre>
 * A bare feature is true when its value is {@code Boolean.TRUE}. {@code feature == 'x'} compares an enum by its literal
 * and anything else by its text; on a many-valued feature ({@code stereotypes}) it is true when one of the values
 * matches. An element that does not have the feature counts as false (and as not equal).
 */
public final class FilterExpression implements Predicate<EObject> {

    private static final Pattern TOKEN = Pattern.compile("\\s*(\\(|\\)|==|!=|'[^']*'|[A-Za-z_][A-Za-z0-9_]*)");

    private final Predicate<EObject> predicate;

    private final List<String> tokens;

    private int position;

    private FilterExpression(String expression) {
        this.tokens = tokenize(expression);
        this.predicate = this.parseOr();
        if (this.position != this.tokens.size()) {
            throw new IllegalArgumentException("unexpected '" + this.tokens.get(this.position) + "' in filter expression: " + expression);
        }
    }

    public static FilterExpression parse(String expression) {
        return new FilterExpression(expression);
    }

    @Override
    public boolean test(EObject element) {
        return this.predicate.test(element);
    }

    private Predicate<EObject> parseOr() {
        Predicate<EObject> left = this.parseAnd();
        while (this.accept("or")) {
            left = left.or(this.parseAnd());
        }
        return left;
    }

    private Predicate<EObject> parseAnd() {
        Predicate<EObject> left = this.parseUnary();
        while (this.accept("and")) {
            left = left.and(this.parseUnary());
        }
        return left;
    }

    private Predicate<EObject> parseUnary() {
        if (this.accept("not")) {
            return this.parseUnary().negate();
        }
        if (this.accept("(")) {
            Predicate<EObject> inner = this.parseOr();
            if (!this.accept(")")) {
                throw new IllegalArgumentException("missing ')' in filter expression");
            }
            return inner;
        }
        if (this.position >= this.tokens.size()) {
            throw new IllegalArgumentException("incomplete filter expression");
        }
        String token = this.tokens.get(this.position++);
        if (token.equals("true")) {
            return e -> true;
        }
        if (token.equals("false")) {
            return e -> false;
        }
        boolean equal = this.accept("==");
        if (equal || this.accept("!=")) {
            if (this.position >= this.tokens.size() || !this.tokens.get(this.position).startsWith("'")) {
                throw new IllegalArgumentException("'" + token + "' comparison needs a quoted literal");
            }
            String quoted = this.tokens.get(this.position++);
            String literal = quoted.substring(1, quoted.length() - 1);
            Predicate<EObject> matches = element -> matches(element, token, literal);
            return equal ? matches : matches.negate();
        }
        return element -> {
            EStructuralFeature feature = element.eClass().getEStructuralFeature(token);
            return feature != null && Boolean.TRUE.equals(element.eGet(feature));
        };
    }

    private static boolean matches(EObject element, String featureName, String literal) {
        EStructuralFeature feature = element.eClass().getEStructuralFeature(featureName);
        if (feature == null) {
            return false;
        }
        Object value = element.eGet(feature);
        if (value instanceof Collection<?> values) {
            return values.stream().anyMatch(v -> literal.equals(text(v)));
        }
        return literal.equals(text(value));
    }

    private static String text(Object value) {
        return value instanceof Enumerator enumerator ? enumerator.getName() : Objects.toString(value, null); // by name, as the compiler checks it
    }

    private boolean accept(String token) {
        if (this.position < this.tokens.size() && this.tokens.get(this.position).equals(token)) {
            this.position++;
            return true;
        }
        return false;
    }

    private static List<String> tokenize(String expression) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(expression);
        int end = 0;
        while (matcher.find() && matcher.start() == end) {
            tokens.add(matcher.group(1));
            end = matcher.end();
        }
        if (!expression.substring(end).isBlank()) {
            throw new IllegalArgumentException("invalid filter expression: " + expression);
        }
        return tokens;
    }
}

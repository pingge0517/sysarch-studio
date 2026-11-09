package com.example.vsm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The VSM filter expression language (spec P1 §3.5), checked at compile time. The backend evaluates the same grammar
 * ({@code view.projection.FilterExpression}); both are tested on the same cases.
 *
 * <pre>
 * expr       := and ('or' and)*
 * and        := unary ('and' unary)*
 * unary      := 'not' unary | '(' expr ')' | 'true' | 'false' | comparison | field
 * comparison := field ('==' | '!=') 'literal'
 * field      := layer | isAbstract | stereotypes          (the whitelist; anything else fails)
 * </pre>
 *
 * A bare field must be boolean ({@code isAbstract}). {@code layer == 'X'} compares the enum literal;
 * {@code stereotypes == 'x'} is true when the element has that stereotype. Only these three identifiers exist, so an
 * expression cannot reach anything else of the model (spec: no {@code self.getOwner().getLayer()}).
 */
final class FilterSyntax {

    /** The only fields an expression may name. */
    static final Set<String> FIELDS = Set.of("layer", "isAbstract", "stereotypes");

    private static final Set<String> KEYWORDS = Set.of("not", "and", "or", "true", "false");

    private static final Pattern TOKEN = Pattern.compile("\\s*(\\(|\\)|==|!=|'[^']*'|[A-Za-z_][A-Za-z0-9_.]*|\\S)");

    /** A parsed expression. */
    sealed interface Expr {

        /** Evaluates against field values: {@code layer} a literal name, {@code isAbstract} a Boolean, {@code stereotypes} a collection. */
        boolean test(Map<String, ?> element);

        /** Every (field, literal) comparison, for checking literals against the metamodel. */
        default void comparisons(List<Comparison> out) {
        }

        /** Every field named. */
        void fields(Set<String> out);
    }

    record Not(Expr inner) implements Expr {
        public boolean test(Map<String, ?> element) {
            return !this.inner.test(element);
        }

        public void comparisons(List<Comparison> out) {
            this.inner.comparisons(out);
        }

        public void fields(Set<String> out) {
            this.inner.fields(out);
        }
    }

    record Binary(boolean and, Expr left, Expr right) implements Expr {
        public boolean test(Map<String, ?> element) {
            return this.and ? this.left.test(element) && this.right.test(element) : this.left.test(element) || this.right.test(element);
        }

        public void comparisons(List<Comparison> out) {
            this.left.comparisons(out);
            this.right.comparisons(out);
        }

        public void fields(Set<String> out) {
            this.left.fields(out);
            this.right.fields(out);
        }
    }

    record Constant(boolean value) implements Expr {
        public boolean test(Map<String, ?> element) {
            return this.value;
        }

        public void fields(Set<String> out) {
        }
    }

    record Flag(String field) implements Expr {
        public boolean test(Map<String, ?> element) {
            return Boolean.TRUE.equals(element.get(this.field));
        }

        public void fields(Set<String> out) {
            out.add(this.field);
        }
    }

    record Comparison(String field, boolean equal, String literal) implements Expr {
        public boolean test(Map<String, ?> element) {
            Object value = element.get(this.field);
            boolean matches = value instanceof Collection<?> values ? values.stream().anyMatch(v -> this.literal.equals(String.valueOf(v)))
                    : this.literal.equals(Objects.toString(value, null));
            return matches == this.equal;
        }

        public void comparisons(List<Comparison> out) {
            out.add(this);
        }

        public void fields(Set<String> out) {
            out.add(this.field);
        }
    }

    /** A syntax or whitelist error. */
    static final class FilterSyntaxException extends Exception {

        private static final long serialVersionUID = 1L;

        FilterSyntaxException(String message) {
            super(message);
        }
    }

    private final List<String> tokens;

    private int position;

    private FilterSyntax(List<String> tokens) {
        this.tokens = tokens;
    }

    static Expr parse(String expression) throws FilterSyntaxException {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(expression);
        int end = 0;
        while (matcher.find() && matcher.start() == end) {
            tokens.add(matcher.group(1));
            end = matcher.end();
        }
        if (!expression.substring(end).isBlank()) {
            throw new FilterSyntaxException("cannot read '" + expression.substring(end).trim() + "'");
        }
        FilterSyntax parser = new FilterSyntax(tokens);
        Expr result = parser.or();
        if (parser.position < tokens.size()) {
            throw new FilterSyntaxException("unexpected '" + tokens.get(parser.position) + "'");
        }
        return result;
    }

    private Expr or() throws FilterSyntaxException {
        Expr left = this.and();
        while (this.accept("or")) {
            left = new Binary(false, left, this.and());
        }
        return left;
    }

    private Expr and() throws FilterSyntaxException {
        Expr left = this.unary();
        while (this.accept("and")) {
            left = new Binary(true, left, this.unary());
        }
        return left;
    }

    private Expr unary() throws FilterSyntaxException {
        if (this.accept("not")) {
            return new Not(this.unary());
        }
        if (this.accept("(")) {
            Expr inner = this.or();
            if (!this.accept(")")) {
                throw new FilterSyntaxException("missing ')'");
            }
            return inner;
        }
        if (this.position >= this.tokens.size()) {
            throw new FilterSyntaxException("incomplete expression");
        }
        String token = this.tokens.get(this.position++);
        if (token.equals("true") || token.equals("false")) {
            return new Constant(token.equals("true"));
        }
        if (!FIELDS.contains(token)) {
            throw new FilterSyntaxException(KEYWORDS.contains(token) || !Character.isLetter(token.charAt(0)) ? "unexpected '" + token + "'"
                    : "'" + token + "' is not allowed: filter expressions may only use " + String.join(", ", List.of("layer", "isAbstract", "stereotypes")));
        }
        boolean equal = this.accept("==");
        if (equal || this.accept("!=")) {
            if (this.position >= this.tokens.size() || !this.tokens.get(this.position).startsWith("'")) {
                throw new FilterSyntaxException("'" + token + (equal ? " ==" : " !=") + "' needs a quoted literal");
            }
            String literal = this.tokens.get(this.position++);
            return new Comparison(token, equal, literal.substring(1, literal.length() - 1));
        }
        return new Flag(token);
    }

    private boolean accept(String token) {
        if (this.position < this.tokens.size() && this.tokens.get(this.position).equals(token)) {
            this.position++;
            return true;
        }
        return false;
    }
}

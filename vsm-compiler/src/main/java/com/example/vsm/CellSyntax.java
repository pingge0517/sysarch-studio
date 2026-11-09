package com.example.vsm;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The table cell expression ({@code cells.existsExpression}, spec P1 §3.3/§3.4): when a matrix cell is marked.
 *
 * <pre>
 * expr     := and ('or' and)*
 * and      := unary ('and' unary)*
 * unary    := 'not' unary | '(' expr ')' | atom
 * atom     := path '==' side | path '-&gt;' 'includes' '(' side ')'
 * path     := ['self' '.'] feature
 * side     := 'row' | 'column'
 * </pre>
 *
 * {@code self} is the element the expression is about (see {@code TableDescription} for which one). The compiler turns
 * the expression into a {@code CellCondition} tree in the generated code, so the backend never parses it.
 */
final class CellSyntax {

    enum Side {
        ROW, COLUMN
    }

    sealed interface Expr {
    }

    record And(Expr left, Expr right) implements Expr {
    }

    record Or(Expr left, Expr right) implements Expr {
    }

    record Not(Expr inner) implements Expr {
    }

    /** {@code feature == side}: a single-valued reference holds the row or column element. */
    record Equals(String feature, Side side) implements Expr {
    }

    /** {@code feature->includes(side)}: a many-valued reference contains it. */
    record Includes(String feature, Side side) implements Expr {
    }

    static final class CellSyntaxException extends Exception {

        private static final long serialVersionUID = 1L;

        CellSyntaxException(String message) {
            super(message);
        }
    }

    private static final Pattern TOKEN = Pattern.compile("\\s*(\\(|\\)|==|->|\\.|[A-Za-z_][A-Za-z0-9_]*|\\S)");

    private final List<String> tokens;

    private int position;

    private CellSyntax(List<String> tokens) {
        this.tokens = tokens;
    }

    static Expr parse(String expression) throws CellSyntaxException {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(expression);
        int end = 0;
        while (matcher.find() && matcher.start() == end) {
            tokens.add(matcher.group(1));
            end = matcher.end();
        }
        if (!expression.substring(end).isBlank()) {
            throw new CellSyntaxException("cannot read '" + expression.substring(end).trim() + "'");
        }
        CellSyntax parser = new CellSyntax(tokens);
        Expr result = parser.or();
        if (parser.position < tokens.size()) {
            throw new CellSyntaxException("unexpected '" + tokens.get(parser.position) + "'");
        }
        return result;
    }

    /** Every atom of the expression, in order. */
    static List<Expr> atoms(Expr expr) {
        List<Expr> out = new ArrayList<>();
        collect(expr, out);
        return out;
    }

    /** The atoms if the expression is only {@code ==} atoms joined by {@code and} (what create-link can set); else null. */
    static List<Equals> conjunctionOfEquals(Expr expr) {
        return switch (expr) {
            case Equals equals -> List.of(equals);
            case And and -> {
                List<Equals> left = conjunctionOfEquals(and.left());
                List<Equals> right = conjunctionOfEquals(and.right());
                if (left == null || right == null) {
                    yield null;
                }
                List<Equals> all = new ArrayList<>(left);
                all.addAll(right);
                yield all;
            }
            default -> null;
        };
    }

    /** The Java expression that builds this condition with the descriptor API's {@code CellCondition}. */
    static String java(Expr expr) {
        return switch (expr) {
            case And and -> "CellCondition.and(" + java(and.left()) + ", " + java(and.right()) + ")";
            case Or or -> "CellCondition.or(" + java(or.left()) + ", " + java(or.right()) + ")";
            case Not not -> "CellCondition.not(" + java(not.inner()) + ")";
            case Equals equals -> "CellCondition.equalTo(\"" + equals.feature() + "\", CellCondition.Side." + equals.side() + ")";
            case Includes includes -> "CellCondition.includes(\"" + includes.feature() + "\", CellCondition.Side." + includes.side() + ")";
        };
    }

    private static void collect(Expr expr, List<Expr> out) {
        switch (expr) {
            case And and -> {
                collect(and.left(), out);
                collect(and.right(), out);
            }
            case Or or -> {
                collect(or.left(), out);
                collect(or.right(), out);
            }
            case Not not -> collect(not.inner(), out);
            default -> out.add(expr);
        }
    }

    private Expr or() throws CellSyntaxException {
        Expr left = this.and();
        while (this.accept("or")) {
            left = new Or(left, this.and());
        }
        return left;
    }

    private Expr and() throws CellSyntaxException {
        Expr left = this.unary();
        while (this.accept("and")) {
            left = new And(left, this.unary());
        }
        return left;
    }

    private Expr unary() throws CellSyntaxException {
        if (this.accept("not")) {
            return new Not(this.unary());
        }
        if (this.accept("(")) {
            Expr inner = this.or();
            this.expect(")");
            return inner;
        }
        if (this.accept("self")) {
            this.expect(".");
        }
        String feature = this.next("a feature");
        if (!Character.isLetter(feature.charAt(0)) || List.of("and", "or", "not", "row", "column", "self").contains(feature)) {
            throw new CellSyntaxException("expected a feature, got '" + feature + "'");
        }
        if (this.accept("==")) {
            return new Equals(feature, this.side());
        }
        if (this.accept("->")) {
            this.expect("includes");
            this.expect("(");
            Side side = this.side();
            this.expect(")");
            return new Includes(feature, side);
        }
        throw new CellSyntaxException("expected '== row|column' or '->includes(row|column)' after '" + feature + "'");
    }

    private Side side() throws CellSyntaxException {
        String token = this.next("row or column");
        return switch (token) {
            case "row" -> Side.ROW;
            case "column" -> Side.COLUMN;
            default -> throw new CellSyntaxException("expected row or column, got '" + token + "'");
        };
    }

    private String next(String what) throws CellSyntaxException {
        if (this.position >= this.tokens.size()) {
            throw new CellSyntaxException("incomplete expression: expected " + what);
        }
        return this.tokens.get(this.position++);
    }

    private void expect(String token) throws CellSyntaxException {
        if (!this.accept(token)) {
            throw new CellSyntaxException("expected '" + token + "'" + (this.position < this.tokens.size() ? ", got '" + this.tokens.get(this.position) + "'" : ""));
        }
    }

    private boolean accept(String token) {
        if (this.position < this.tokens.size() && this.tokens.get(this.position).equals(token)) {
            this.position++;
            return true;
        }
        return false;
    }
}

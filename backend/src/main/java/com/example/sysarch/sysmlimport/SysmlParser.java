package com.example.sysarch.sysmlimport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SysML v2 textual notation, the structural subset (P2 §5, T6 step 1): text → an intermediate tree ({@link Decl}), never
 * the model. Supported: {@code package}, {@code import}, {@code [abstract] part def}, {@code part}, {@code port def},
 * {@code [in|out|inout] port}, {@code connection … connect a.b to c.d} / {@code connect …}, {@code requirement},
 * {@code doc /* … *\/}, {@code comment}, {@code metadata def} and {@code @Metadata { name = "…"; }} usages, short names
 * {@code <'id'>}, unrestricted names {@code 'name'}, typing {@code :}, specialization {@code :>}, conjugation {@code ~},
 * multiplicity {@code [..]}.
 *
 * <p>Everything else (attributes, actions, states, calculations, constraints, flows, redefinitions, …) is
 * <b>unsupported</b>: the parser records each occurrence with its line and keeps going, so one run lists them all
 * (T6 step 2: the whole import then fails, with the list; nothing is half imported).
 */
public final class SysmlParser {

    /** A declaration of the tree. Mutable while parsing. */
    public static final class Decl {

        public final String kind;

        public final int line;

        public String shortName;

        public String name;

        public String type;

        public boolean conjugated;

        public boolean isAbstract;

        public String direction;

        public String multiplicity;

        public final List<String> superTypes = new ArrayList<>();

        public List<String> sourceEnd;

        public List<String> targetEnd;

        public final List<String> docs = new ArrayList<>();

        /** Metadata usages by metadata name: attribute → value. */
        public final Map<String, Map<String, String>> metadata = new LinkedHashMap<>();

        public final List<Decl> children = new ArrayList<>();

        Decl(String kind, int line) {
            this.kind = kind;
            this.line = line;
        }

        @Override
        public String toString() {
            return this.kind + " " + (this.shortName == null ? "" : "<" + this.shortName + "> ") + (this.name == null ? "" : this.name) + " (line " + this.line + ")";
        }
    }

    /** Something the importer cannot take, with where it is. */
    public record Problem(int line, int column, String message) {

        @Override
        public String toString() {
            return "第 " + this.line + " 行：" + this.message;
        }
    }

    /** The tree (one root per package, nested packages flattened into their parent) and the problems. */
    public record Parsed(Decl root, List<Problem> problems) {
    }

    // ------------------------------------------------------------------ lexer

    enum T {
        IDENT, NAME, STRING, NUMBER, SYMBOL, COMMENT, EOF
    }

    record Token(T type, String text, int line, int column) {

        boolean is(String symbolOrKeyword) {
            return (this.type == T.SYMBOL || this.type == T.IDENT) && this.text.equals(symbolOrKeyword);
        }
    }

    private static final List<String> SYMBOLS = List.of("::>", ":>>", "::", ":>", "..", "=>", "->", ";", "{", "}", "[", "]", ":", ",", ".", "=", "~", "@",
            "<", ">", "*", "#", "(", ")", "+", "-", "/", "?", "!", "&", "|", "^", "%", "$");

    /** Keywords of SysML v2 whose declarations the importer does not take (P2 §5.1 "不支持"). */
    static final Set<String> UNSUPPORTED = Set.of("attribute", "action", "state", "calc", "constraint", "perform", "exhibit", "transition", "then", "succession",
            "flow", "message", "item", "enum", "occurrence", "individual", "snapshot", "timeslice", "analysis", "verification", "use", "case", "concern",
            "view", "viewpoint", "rendering", "allocation", "allocate", "satisfy", "verify", "assert", "assume", "require", "subject", "actor", "stakeholder",
            "objective", "interface", "binding", "bind", "first", "if", "while", "loop", "accept", "send", "assign", "decide", "merge", "fork",
            "join", "entry", "do", "exit", "ref", "derived", "readonly", "variation", "variant", "event", "expose", "filter", "alias", "library", "standard",
            "dependency", "frame", "include", "return", "in", "out", "inout", "end", "abstract", "def", "class", "struct", "assoc", "datatype", "feature",
            "classifier", "type", "subset", "redefines", "specializes", "conjugates", "disjoint", "metaclass", "predicate", "function", "expr", "behavior",
            "step", "connector", "multiplicity", "inv");

    private static final Map<String, String> UNSUPPORTED_LABELS = Map.ofEntries(Map.entry("attribute", "属性/参数（attribute）"),
            Map.entry("action", "动作（action）"), Map.entry("state", "状态机（state）"), Map.entry("transition", "状态迁移（transition）"),
            Map.entry("calc", "计算（calc）"), Map.entry("constraint", "约束（constraint）"), Map.entry("perform", "行为（perform）"),
            Map.entry("exhibit", "状态行为（exhibit）"), Map.entry("flow", "流（flow）"), Map.entry("item", "项（item）"), Map.entry("enum", "枚举（enum）"),
            Map.entry("succession", "顺序（succession）"), Map.entry("then", "顺序（then）"), Map.entry("interface", "接口用法（interface）"),
            Map.entry("allocate", "分配语法（allocate）"), Map.entry("allocation", "分配语法（allocation）"), Map.entry("satisfy", "满足语法（satisfy）"),
            Map.entry("verify", "验证语法（verify）"), Map.entry("use", "用例（use case）"), Map.entry("ref", "引用特征（ref）"),
            Map.entry("occurrence", "事件发生（occurrence）"), Map.entry("analysis", "分析（analysis）"), Map.entry("verification", "验证（verification）"));

    private final List<Token> tokens = new ArrayList<>();

    private final List<Problem> problems = new ArrayList<>();

    private int at;

    private SysmlParser() {
    }

    public static Parsed parse(String text) {
        SysmlParser parser = new SysmlParser();
        parser.lex(text);
        Decl root = new Decl("root", 1);
        parser.members(root, false);
        return new Parsed(root, List.copyOf(parser.problems));
    }

    private void lex(String text) {
        int line = 1;
        int column = 1;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\n') {
                line++;
                column = 1;
                i++;
                continue;
            }
            if (Character.isWhitespace(c) || c == '﻿') {
                i++;
                column++;
                continue;
            }
            int startLine = line;
            int startColumn = column;
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                while (i < text.length() && text.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                if (end < 0) {
                    this.problems.add(new Problem(startLine, startColumn, "注释没有结束（缺少 */）"));
                    end = text.length() - 2;
                }
                String body = text.substring(i + 2, Math.max(i + 2, end));
                for (char ch : text.substring(i, Math.min(text.length(), end + 2)).toCharArray()) {
                    if (ch == '\n') {
                        line++;
                        column = 1;
                    } else {
                        column++;
                    }
                }
                this.tokens.add(new Token(T.COMMENT, body, startLine, startColumn));
                i = end + 2;
                continue;
            }
            if (c == '\'' || c == '"') {
                StringBuilder value = new StringBuilder();
                int j = i + 1;
                while (j < text.length() && text.charAt(j) != c && text.charAt(j) != '\n') {
                    if (text.charAt(j) == '\\' && j + 1 < text.length()) {
                        j++;
                    }
                    value.append(text.charAt(j));
                    j++;
                }
                if (j >= text.length() || text.charAt(j) != c) {
                    this.problems.add(new Problem(startLine, startColumn, (c == '\'' ? "名称" : "字符串") + "没有结束（缺少 " + c + "）"));
                }
                column += j + 1 - i;
                i = j + 1;
                this.tokens.add(new Token(c == '\'' ? T.NAME : T.STRING, value.toString(), startLine, startColumn));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < text.length() && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_')) {
                    j++;
                }
                this.tokens.add(new Token(T.IDENT, text.substring(i, j), startLine, startColumn));
                column += j - i;
                i = j;
                continue;
            }
            if (Character.isDigit(c)) {
                int j = i;
                while (j < text.length() && (Character.isDigit(text.charAt(j)) || text.charAt(j) == '.' && j + 1 < text.length()
                        && Character.isDigit(text.charAt(j + 1)))) {
                    j++;
                }
                this.tokens.add(new Token(T.NUMBER, text.substring(i, j), startLine, startColumn));
                column += j - i;
                i = j;
                continue;
            }
            String symbol = null;
            for (String s : SYMBOLS) {
                if (text.startsWith(s, i)) {
                    symbol = s;
                    break;
                }
            }
            if (symbol == null) {
                this.problems.add(new Problem(startLine, startColumn, "无法识别的字符「" + c + "」"));
                i++;
                column++;
                continue;
            }
            this.tokens.add(new Token(T.SYMBOL, symbol, startLine, startColumn));
            i += symbol.length();
            column += symbol.length();
        }
        this.tokens.add(new Token(T.EOF, "", line, column));
    }

    // ------------------------------------------------------------------ parser

    /** Members of a namespace or a definition body, up to the closing brace (or the end for the root). */
    private void members(Decl container, boolean braced) {
        while (true) {
            if (this.peekRaw().type() == T.COMMENT) {
                this.at++; // raw: a plain comment, not part of the model
                continue;
            }
            Token token = this.peek();
            if (token.type() == T.EOF) {
                if (braced) {
                    this.problems.add(new Problem(token.line(), token.column(), "缺少 }（" + container + " 没有结束）"));
                }
                return;
            }
            if (token.is("}")) {
                if (braced) {
                    this.next();
                } else {
                    this.problem(token, "多余的 }");
                    this.next();
                    continue;
                }
                return;
            }
            this.member(container);
        }
    }

    private void member(Decl container) {
        Token first = this.peek();
        if (first.is("private") || first.is("public") || first.is("protected")) {
            this.next();
            first = this.peek();
        }
        if (first.is(";")) {
            this.next();
            return;
        }
        if (first.is("import")) {
            this.skipStatement();
            return;
        }
        if (first.is("package")) {
            this.next();
            Decl named = new Decl("package", first.line());
            this.optionalName(named);
            if (container.name == null) {
                container.name = named.name; // the first package names the import
            }
            this.expect("{");
            this.members(container, true); // nested packages: their members belong to the enclosing scope
            return;
        }
        if (first.is("doc")) {
            this.next();
            this.optionalShortName();
            Token comment = this.peekRaw();
            if (comment.type() == T.COMMENT) {
                this.at++; // raw: the comment token itself
                container.docs.add(docText(comment.text()));
            } else {
                this.problem(comment, "doc 后面应是 /* … */ 注释");
            }
            return;
        }
        if (first.is("comment")) {
            this.next();
            while (this.peekRaw().type() != T.COMMENT && this.peekRaw().type() != T.EOF && !this.peekRaw().is(";")) {
                this.at++; // raw: the comment token itself
            }
            if (this.peekRaw().type() == T.COMMENT) {
                this.at++; // raw: the comment token itself
            }
            return;
        }
        if (first.is("@")) {
            this.next();
            this.metadataUsage(container);
            return;
        }
        if (first.is("metadata")) {
            this.next();
            if (this.peek().is("def")) {
                this.next();
                this.optionalShortName();
                this.optionalName();
                this.skipBodyOrSemicolon();
            } else {
                this.metadataUsage(container); // "metadata X { … }" is a usage too
            }
            return;
        }
        boolean isAbstract = false;
        if (first.is("abstract")) {
            this.next();
            isAbstract = true;
            first = this.peek();
        }
        String direction = null;
        if (first.is("in") || first.is("out") || first.is("inout")) {
            if (this.peek(1).is("port")) {
                direction = first.text();
                this.next();
                first = this.peek();
            }
        }
        if (first.is("part")) {
            this.next();
            if (this.peek().is("def")) {
                this.next();
                Decl decl = this.declaration("part def", first, container);
                decl.isAbstract = isAbstract;
                if (this.peek().is(":>")) {
                    this.next();
                    decl.superTypes.add(this.qualifiedName());
                    while (this.peek().is(",")) {
                        this.next();
                        decl.superTypes.add(this.qualifiedName());
                    }
                }
                this.bodyOrSemicolon(decl);
            } else {
                Decl decl = this.declaration("part", first, container);
                this.typing(decl);
                this.bodyOrSemicolon(decl);
            }
            return;
        }
        if (first.is("port")) {
            this.next();
            if (this.peek().is("def")) {
                this.next();
                Decl decl = this.declaration("port def", first, container);
                if (this.peek().is(":>")) {
                    this.next();
                    decl.superTypes.add(this.qualifiedName());
                    while (this.peek().is(",")) {
                        this.next();
                        decl.superTypes.add(this.qualifiedName());
                    }
                }
                this.bodyOrSemicolon(decl);
            } else {
                Decl decl = this.declaration("port", first, container);
                decl.direction = direction;
                this.typing(decl);
                this.bodyOrSemicolon(decl);
            }
            return;
        }
        if (first.is("connection") || first.is("connect")) {
            if (first.is("connection") && this.peek(1).is("def")) {
                this.unsupported(first, "连接定义（connection def）");
                return;
            }
            Decl decl = new Decl("connection", first.line());
            container.children.add(decl);
            this.next();
            if (first.is("connection")) {
                this.optionalShortName(decl);
                this.optionalName(decl);
                if (this.peek().is(":")) {
                    this.next();
                    this.qualifiedName(); // a connection definition as type: no sysarch counterpart, ignored
                }
                if (!this.peek().is("connect")) {
                    this.problem(this.peek(), "connection 后面应是 connect a.b to c.d");
                    this.skipBodyOrSemicolon();
                    return;
                }
                this.next();
            }
            decl.sourceEnd = this.featureChain();
            if (!this.peek().is("to")) {
                this.problem(this.peek(), "connect 之后应是「to」");
                this.skipBodyOrSemicolon();
                return;
            }
            this.next();
            decl.targetEnd = this.featureChain();
            this.bodyOrSemicolon(decl);
            return;
        }
        if (first.is("requirement")) {
            if (this.peek(1).is("def")) {
                this.unsupported(first, "需求定义（requirement def），只支持 requirement 用法");
                return;
            }
            this.next();
            Decl decl = this.declaration("requirement", first, container);
            if (this.peek().is(":")) {
                this.next();
                this.qualifiedName();
            }
            this.bodyOrSemicolon(decl);
            return;
        }
        // v1.4: interactions, in the form SysArch exports them (occurrence def + part lifelines + messages + successions)
        if (first.is("occurrence") && this.peek(1).is("def")) {
            this.next();
            this.next();
            Decl decl = this.declaration("occurrence def", first, container);
            this.bodyOrSemicolon(decl);
            return;
        }
        if (first.is("message")) {
            this.next();
            Decl decl = this.declaration("message", first, container);
            if (this.peek().is(":")) {
                this.next();
                this.qualifiedName(); // a message's payload type: no sysarch counterpart
            }
            if (this.peek().is("from")) {
                this.next();
                decl.sourceEnd = this.featureChain();
                if (!this.peek().is("to")) {
                    this.problem(this.peek(), "message … from a 之后应是「to b」");
                    this.skipBodyOrSemicolon();
                    return;
                }
                this.next();
                decl.targetEnd = this.featureChain();
            }
            this.bodyOrSemicolon(decl);
            return;
        }
        if (first.is("first")) {
            Decl decl = new Decl("succession", first.line());
            container.children.add(decl);
            this.next();
            decl.sourceEnd = this.featureChain();
            if (!this.peek().is("then")) {
                this.problem(this.peek(), "first a 之后应是「then b」");
                this.skipBodyOrSemicolon();
                return;
            }
            this.next();
            decl.targetEnd = this.featureChain();
            this.bodyOrSemicolon(decl);
            return;
        }
        if (first.type() == T.IDENT && UNSUPPORTED.contains(first.text()) || first.type() == T.IDENT) {
            this.unsupported(first, UNSUPPORTED_LABELS.getOrDefault(first.text(), "「" + first.text() + "」声明"));
            return;
        }
        this.problem(first, "此处不应出现「" + first.text() + "」");
        this.next();
    }

    private Decl declaration(String kind, Token first, Decl container) {
        Decl decl = new Decl(kind, first.line());
        container.children.add(decl);
        this.optionalShortName(decl);
        this.optionalName(decl);
        return decl;
    }

    /** {@code : [~]Type} and {@code [mult]} of a usage; subsetting and redefinition are unsupported. */
    private void typing(Decl decl) {
        if (this.peek().is(":")) {
            this.next();
            if (this.peek().is("~")) {
                this.next();
                decl.conjugated = true;
            }
            decl.type = this.qualifiedName();
        }
        if (this.peek().is("[")) {
            this.next();
            StringBuilder multiplicity = new StringBuilder();
            while (!this.peek().is("]") && this.peek().type() != T.EOF) {
                multiplicity.append(this.next().text());
            }
            this.expect("]");
            decl.multiplicity = multiplicity.toString();
        }
        if (this.peek().is(":>") || this.peek().is(":>>") || this.peek().is("redefines") || this.peek().is("subsets")) {
            this.unsupported(this.peek(), "子集/重定义（" + this.peek().text() + "）");
        }
    }

    private void bodyOrSemicolon(Decl decl) {
        if (this.peek().is(";")) {
            this.next();
            return;
        }
        if (this.peek().is("=") || this.peek().is(":=")) {
            this.unsupported(this.peek(), "取值（=）");
            return;
        }
        if (this.expect("{")) {
            this.members(decl, true);
        }
    }

    private void metadataUsage(Decl container) {
        String name = this.qualifiedName();
        Map<String, String> values = new LinkedHashMap<>();
        if (this.peek().is("{")) {
            this.next();
            while (!this.peek().is("}") && this.peek().type() != T.EOF) {
                Token key = this.next();
                if (key.type() == T.IDENT && this.peek().is("=")) {
                    this.next();
                    Token value = this.next();
                    values.put(key.text(), value.text());
                    if (this.peek().is(";")) {
                        this.next();
                    }
                } else if (!key.is(";")) {
                    this.problem(key, "元数据 @" + name + " 里应是「名称 = 值;」");
                }
            }
            this.expect("}");
        } else if (this.peek().is(";")) {
            this.next();
        }
        container.metadata.put(name, values);
    }

    /** {@code a.b.c} (names or quoted names) as its segments. */
    private List<String> featureChain() {
        List<String> chain = new ArrayList<>();
        chain.add(this.qualifiedName());
        while (this.peek().is(".")) {
            this.next();
            chain.add(this.qualifiedName());
        }
        return chain;
    }

    /** {@code A::B::C} → its last segment (names resolve within the imported text). */
    private String qualifiedName() {
        String name = this.nameToken();
        while (this.peek().is("::")) {
            this.next();
            name = this.nameToken();
        }
        return name;
    }

    private String nameToken() {
        Token token = this.peek();
        if (token.type() == T.NAME || token.type() == T.IDENT) {
            this.next();
            return token.text();
        }
        this.problem(token, "这里应是名称");
        return "";
    }

    private void optionalShortName(Decl decl) {
        if (this.peek().is("<") && (this.peek(1).type() == T.NAME || this.peek(1).type() == T.IDENT) && this.peek(2).is(">")) {
            decl.shortName = this.peek(1).text();
            this.next();
            this.next();
            this.next();
        }
    }

    private void optionalShortName() {
        this.optionalShortName(new Decl("ignored", 0));
    }

    private void optionalName(Decl decl) {
        Token token = this.peek();
        if (token.type() == T.NAME || token.type() == T.IDENT && !Set.of("connect", "def").contains(token.text())) {
            decl.name = token.text();
            this.next();
        }
    }

    private void optionalName() {
        this.optionalName(new Decl("ignored", 0));
    }

    /** Records an unsupported declaration and skips it whole. */
    private void unsupported(Token at, String what) {
        this.problems.add(new Problem(at.line(), at.column(), "不支持的语法：" + what));
        this.skipStatement();
    }

    /** Skips to the end of the statement: a {@code ;} at this depth, or a balanced {@code { … }}. */
    private void skipStatement() {
        int depth = 0;
        while (this.peekRaw().type() != T.EOF) {
            Token token = this.nextRaw();
            if (token.is("{")) {
                depth++;
            } else if (token.is("}")) {
                depth--;
                if (depth <= 0) {
                    return;
                }
            } else if (token.is(";") && depth == 0) {
                return;
            }
        }
    }

    private void skipBodyOrSemicolon() {
        this.skipStatement();
    }

    private boolean expect(String symbol) {
        if (this.peek().is(symbol)) {
            this.next();
            return true;
        }
        this.problem(this.peek(), "这里应是「" + symbol + "」");
        this.skipStatement();
        return false;
    }

    private void problem(Token at, String message) {
        this.problems.add(new Problem(at.line(), at.column(), message + (at.type() == T.EOF ? "（已到文本末尾）" : "，实际是「" + at.text() + "」")));
    }

    /** The next token, plain comments skipped. */
    private Token peek() {
        return this.peek(0);
    }

    private Token peek(int ahead) {
        int i = this.at;
        int seen = -1;
        while (i < this.tokens.size()) {
            if (this.tokens.get(i).type() != T.COMMENT && ++seen == ahead) {
                return this.tokens.get(i);
            }
            i++;
        }
        return this.tokens.get(this.tokens.size() - 1);
    }

    private Token next() {
        while (this.tokens.get(this.at).type() == T.COMMENT) {
            this.next();
        }
        return this.tokens.get(this.at++);
    }

    private Token peekRaw() {
        return this.tokens.get(Math.min(this.at, this.tokens.size() - 1));
    }

    private Token nextRaw() {
        return this.tokens.get(this.at++);
    }

    /** The text of a doc comment: the leading " * " of each line removed, trimmed. */
    static String docText(String body) {
        List<String> lines = new ArrayList<>();
        for (String line : body.split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.startsWith("*")) {
                trimmed = trimmed.substring(1);
                if (trimmed.startsWith(" ")) {
                    trimmed = trimmed.substring(1);
                }
            }
            lines.add(line.strip().equals("*") ? "" : trimmed.replace("* /", "*/"));
        }
        while (!lines.isEmpty() && lines.get(0).isBlank()) {
            lines.remove(0);
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
        return String.join("\n", lines);
    }
}

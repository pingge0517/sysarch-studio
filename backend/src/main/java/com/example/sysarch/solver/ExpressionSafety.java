package com.example.sysarch.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The first of the three lines of defence of spec P3 §3.4 / §9 (static scan, empty namespace, isolated process): an
 * equation the user wrote may hold only parameter names, numbers, {@code + - * / ** ( )}, spaces and one {@code =}.
 * Anything else (import, open, eval, attribute access, quotes, brackets, underscores…) is refused before any process
 * runs; the reason names what was found. The other two lines: the Python script runs the code with no builtins, and
 * {@link PythonSolver} runs it in a separate process with an empty environment and a time limit (the container with no
 * network and a read-only file system is the deployment's, see the Helm chart's solver).
 */
public final class ExpressionSafety {

    /** Names that are refused even as parameter names (spec T4 step 5: import/open/eval, and their relatives). */
    static final Set<String> FORBIDDEN = Set.of("import", "open", "eval", "exec", "compile", "lambda", "globals", "locals", "getattr", "setattr", "delattr",
            "vars", "dir", "input", "print", "breakpoint", "help", "exit", "quit", "class", "def", "for", "while", "with", "yield", "return", "from", "as",
            "global", "nonlocal", "del", "pass", "raise", "try", "except", "finally", "assert", "if", "else", "elif", "and", "or", "not", "is", "in", "None",
            "True", "False", "async", "await", "os", "sys", "subprocess", "builtins");

    /** Names: letters (also Chinese) and digits, single underscores inside only (no {@code __dunder__}). */
    private static final Pattern TOKEN = Pattern.compile("\\s+|\\p{L}[\\p{L}\\p{N}]*(?:_[\\p{L}\\p{N}]+)*|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?|\\*\\*|[-+*/()=]");

    private static final Pattern NAME = Pattern.compile("\\p{L}[\\p{L}\\p{N}_]*");

    private ExpressionSafety() {
    }

    /** One equation's tokens, or the reasons it is refused (an empty token list then). */
    public record Scan(List<String> tokens, List<String> problems) {

        public boolean ok() {
            return this.problems.isEmpty();
        }
    }

    /**
     * Scans one equation {@code lhs = rhs}.
     *
     * @param names the parameter names it may use
     */
    public static Scan scan(String equation, Set<String> names) {
        List<String> tokens = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(equation);
        int at = 0;
        while (at < equation.length()) {
            if (!matcher.find(at) || matcher.start() != at) {
                problems.add("不允许的字符「" + equation.charAt(at) + "」（只能用参数名、数字和 + - * / ** ( ) =）");
                break;
            }
            String token = matcher.group();
            at = matcher.end();
            if (token.isBlank()) {
                continue;
            }
            if (NAME.matcher(token).matches()) {
                if (FORBIDDEN.contains(token)) {
                    problems.add("不允许的关键字「" + token + "」：表达式只能写算式，不能调用或导入任何东西");
                } else if (!names.contains(token)) {
                    problems.add("「" + token + "」不是这个约束块的参数");
                }
            }
            tokens.add(token);
        }
        if (problems.isEmpty() && tokens.stream().filter("="::equals).count() != 1) {
            problems.add("每行必须是一个等式「左边 = 右边」");
        }
        return new Scan(problems.isEmpty() ? tokens : List.of(), problems);
    }
}

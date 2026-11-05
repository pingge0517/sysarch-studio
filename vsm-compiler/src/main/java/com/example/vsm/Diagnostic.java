package com.example.vsm;

import java.util.Comparator;

/**
 * One compile error. Printed as {@code views/<file>:<line> <message>} (spec §4.5). There are no warnings:
 * every diagnostic fails the build (spec §4.2).
 *
 * @param file path relative to the repository root, with forward slashes
 */
record Diagnostic(String file, int line, String message) {

    static final Comparator<Diagnostic> ORDER = Comparator.comparing(Diagnostic::file)
            .thenComparingInt(Diagnostic::line)
            .thenComparing(Diagnostic::message);

    @Override
    public String toString() {
        return this.file + ":" + this.line + " " + this.message;
    }
}

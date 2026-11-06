package com.example.sysarch.sync.polarion;

import java.util.Objects;

/**
 * The caller's Polarion credential, passed through on every call and never stored (spec §6.1, T7 step 2). It comes
 * from the incoming request's {@code Authorization: Bearer ...} header; this service has no account of its own.
 * {@link #toString()} is redacted so the token cannot end up in logs.
 */
public record PolarionToken(String value) {

    public PolarionToken {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("empty Polarion token");
        }
    }

    /** From an {@code Authorization} header value; empty or non-bearer headers give null. */
    public static PolarionToken fromAuthorizationHeader(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7) || header.substring(7).isBlank()) {
            return null;
        }
        return new PolarionToken(header.substring(7).trim());
    }

    String authorizationHeader() {
        return "Bearer " + this.value;
    }

    @Override
    public String toString() {
        return "PolarionToken[***]";
    }
}

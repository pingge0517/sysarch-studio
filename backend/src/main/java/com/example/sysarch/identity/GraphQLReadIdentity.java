package com.example.sysarch.identity;

import java.util.Optional;
import java.util.function.Supplier;

/** One-use handoff from the native WS listener to synchronous GraphQL.execute; removed before async work starts. */
final class GraphQLReadIdentity {
    private static final ThreadLocal<Supplier<Optional<Principal>>> NEXT = new ThreadLocal<>();
    static void prime(Supplier<Optional<Principal>> principal) { NEXT.set(principal); }
    static Supplier<Optional<Principal>> take() {
        var identity = NEXT.get();
        NEXT.remove();
        return identity;
    }
    static void clear() { NEXT.remove(); }
    private GraphQLReadIdentity() { }
}

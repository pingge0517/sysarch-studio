package com.example.sysarch.identity;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Who is making a request. Since P2 T8 the principal is resolved by {@link IdentityFilter} ({@link Identities}: the
 * session cookie, a Polarion token, or in dev mode a typed name) and kept on the request. Only when no filter ran (unit
 * tests that call controllers directly) the old rule applies: the {@value #HEADER} header names the user.
 */
public final class CurrentUser {

    /** Dev mode, and the fallback without the filter: the user's name. */
    public static final String HEADER = "X-Sysarch-User";

    /** Dev mode: the name typed on the :3000 shell (P2 T1's temporary identity). */
    public static final String COOKIE = "sysarch_user";

    static final String ATTRIBUTE = Principal.class.getName();

    private CurrentUser() {
    }

    /** Resolves the principal at most once per request, when first asked (asking Polarion about a token may be slow). */
    static void attach(HttpServletRequest request, Supplier<Optional<Principal>> resolver) {
        Optional<Principal>[] memo = new Optional[1];
        request.setAttribute(ATTRIBUTE, (Supplier<Optional<Principal>>) () -> {
            if (memo[0] == null) {
                memo[0] = resolver.get();
            }
            return memo[0];
        });
    }

    @SuppressWarnings("unchecked")
    public static Optional<Principal> principal(HttpServletRequest request) {
        Object attached = request.getAttribute(ATTRIBUTE);
        if (attached instanceof Supplier<?> supplier) {
            return (Optional<Principal>) supplier.get();
        }
        return Optional.ofNullable(clean(request.getHeader(HEADER))).map(user -> Principal.dev(user, List.of()));
    }

    /** The user of a request, or empty for an anonymous one. */
    public static Optional<String> of(HttpServletRequest request) {
        return principal(request).map(Principal::user);
    }

    /** The editor of a model change made for this request. */
    public static Editors.Editor editor(HttpServletRequest request) {
        return principal(request).map(Editors.Editor::of).orElse(Editors.Editor.ANONYMOUS);
    }

    /** Trimmed, at most 128 characters (the lock table's owner column), or null when blank. */
    static String clean(String user) {
        if (user == null || user.isBlank()) {
            return null;
        }
        String trimmed = user.strip();
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }
}

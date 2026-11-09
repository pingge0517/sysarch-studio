package com.example.sysarch.identity;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Who is making a request (P2 T1, user's decision "临时身份，T8 再替换"). Until T8 maps the Polarion login, the user
 * is a name the person types on the :3000 shell, kept in the {@value #COOKIE} cookie. Cookies are per host, not per
 * port, so Sirius Web on :8080 receives it too. API clients may send the {@value #HEADER} header instead (it wins).
 * No password and no token is involved or stored; T8 replaces this with the Polarion identity.
 */
public final class CurrentUser {

    public static final String HEADER = "X-Sysarch-User";

    public static final String COOKIE = "sysarch_user";

    private CurrentUser() {
    }

    /** The user of a request, or empty for an anonymous one. */
    public static Optional<String> of(HttpServletRequest request) {
        String header = clean(request.getHeader(HEADER));
        if (header != null) {
            return Optional.of(header);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE.equals(cookie.getName())) {
                    return Optional.ofNullable(clean(URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8)));
                }
            }
        }
        return Optional.empty();
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

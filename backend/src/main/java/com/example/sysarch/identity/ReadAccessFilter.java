package com.example.sysarch.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * P4 T8, security finding F4: reading needs a signed-in user. Sirius Web has no read permissions, and P2 T8 checked
 * writes only, so before this anyone who reached SysArch could read every model (GraphQL queries, exports, tables).
 * Requests to {@code /api/**} and to the GraphQL subscriptions ({@code /subscriptions}) without a principal now get 401,
 * except: signing in ({@code /api/v1/auth/**}), the Polarion webhook (verified by its HMAC signature), Sirius' images
 * ({@code /api/images/**}, icons of the UI), CORS preflights. {@code /actuator} is not under {@code /api} (health stays
 * open, metrics need their token). Static pages are not covered: they hold no model data, and the application shows the
 * sign-in page when {@code /api/v1/auth/me} says nobody is signed in.
 *
 * <p>{@code sysarch.auth.anonymous-read} ({@code SYSARCH_ANONYMOUS_READ}) switches it: unset = off in polarion mode
 * (reads need sign-in), on in dev mode (local tests and scripts).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15) // after IdentityFilter (+10), before GraphQLEditorFilter (+20)
public class ReadAccessFilter extends OncePerRequestFilter {

    private static final List<String> OPEN = List.of("/api/v1/auth/", "/api/v1/webhooks/polarion", "/api/images/");

    private final boolean anonymousRead;

    public ReadAccessFilter(AuthProperties properties) {
        this.anonymousRead = properties.getAnonymousRead() != null ? properties.getAnonymousRead() : properties.dev();
    }

    static boolean protectedPath(String path) {
        boolean covered = path.equals("/api") || path.startsWith("/api/") || path.equals("/subscriptions") || path.startsWith("/subscriptions/");
        return covered && OPEN.stream().noneMatch(path::startsWith);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (this.anonymousRead || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !protectedPath(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (CurrentUser.principal(request).isEmpty()) {
            // the status itself, not sendError (Sirius' SPA route would answer /error with 200, see MetricsTokenFilter)
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\",\"detail\":\"请先登录（Polarion 单点登录或个人访问令牌）\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** For tests. */
    boolean anonymousRead() {
        return this.anonymousRead;
    }
}

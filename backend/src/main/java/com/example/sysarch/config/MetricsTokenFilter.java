package com.example.sysarch.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * P4 T5: {@code /actuator/prometheus} answers only with {@code Authorization: Bearer <SYSARCH_METRICS_TOKEN>}
 * (Prometheus' {@code authorization} / {@code bearer_token_file}). nginx and the Sirius ingress forward
 * {@code /actuator} to the backend, so without this the metrics would be public; a separate management port would also
 * move {@code /actuator/health}, which the probes and the acceptance scripts use. No token configured = 404 (as if the
 * endpoint did not exist); a wrong or missing token = 401.
 */
@Component
public class MetricsTokenFilter extends OncePerRequestFilter {

    static final String PATH = "/actuator/prometheus";

    private final byte[] token;

    public MetricsTokenFilter(@Value("${sysarch.metrics.token:}") String token) {
        this.token = token == null || token.isBlank() ? new byte[0] : token.strip().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.equals(PATH) || path.startsWith(PATH + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (this.token.length == 0) {
            refuse(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String header = request.getHeader("Authorization");
        byte[] given = header != null && header.startsWith("Bearer ") ? header.substring(7).strip().getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (!MessageDigest.isEqual(this.token, given)) {
            response.setHeader("WWW-Authenticate", "Bearer");
            refuse(response, HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * The status itself, not sendError: sendError forwards to /error, which Sirius' single-page route answers with 200 and
     * its HTML page (found live; the same trap ApiErrorHandler avoids for the API).
     */
    private static void refuse(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(status == HttpServletResponse.SC_NOT_FOUND ? "Not Found" : "Unauthorized");
    }
}

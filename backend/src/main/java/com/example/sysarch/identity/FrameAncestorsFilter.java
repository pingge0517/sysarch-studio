package com.example.sysarch.identity;

import com.example.sysarch.sync.SyncProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Who may show our pages in a frame (embedding in Polarion): every response carries
 * {@code Content-Security-Policy: frame-ancestors 'self' <sources>}, the sources being
 * {@code sysarch.auth.frame-ancestors} (space or comma separated) or, when that is empty, the origin of
 * {@code sysarch.polarion.base-url}. Any other site that frames SysArch gets a blank frame (clickjacking). Sirius Web
 * sets no framing header of its own, so this one decides.
 *
 * <p>P4 T8 (ZAP baseline, finding F7): the header also carries a full policy before {@code frame-ancestors}
 * ({@link #DEFAULT_POLICY}, or {@code sysarch.auth.content-security-policy} / {@code SYSARCH_CSP}): scripts, connections
 * (incl. the GraphQL WebSocket), images and fonts from this origin only, no plugins, no {@code <base>} or form posts to
 * other sites. Styles allow inline: MUI / emotion in Sirius Web insert {@code <style>} elements at run time. Neither
 * front end has inline scripts or eval.
 */
@Component
public class FrameAncestorsFilter extends OncePerRequestFilter {

    public static final String HEADER = "Content-Security-Policy";

    public static final String DEFAULT_POLICY = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; "
            + "font-src 'self' data:; connect-src 'self'; worker-src 'self' blob:; frame-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'";

    private static final java.util.regex.Pattern ORIGIN = java.util.regex.Pattern.compile("https?://[A-Za-z0-9.-]+(:\\d{1,5})?");

    private final String policy;

    public FrameAncestorsFilter(AuthProperties auth, SyncProperties polarion) {
        String base = auth.getContentSecurityPolicy() == null || auth.getContentSecurityPolicy().isBlank() ? DEFAULT_POLICY
                : auth.getContentSecurityPolicy().strip().replaceAll("[;\\s]+$", "");
        this.policy = base + "; " + policy(auth.getFrameAncestors(), polarion.getBaseUrl());
    }

    static String policy(String configured, String polarionBaseUrl) {
        Set<String> sources = new LinkedHashSet<>();
        sources.add("'self'");
        if (configured != null && !configured.isBlank()) {
            for (String source : configured.split("[\\s,]+")) {
                if (ORIGIN.matcher(source.strip()).matches()) { // explicit origins only: no wildcard, no injected directive
                    sources.add(source.strip());
                }
            }
        } else {
            String origin = origin(polarionBaseUrl);
            if (origin != null) {
                sources.add(origin);
            }
        }
        return "frame-ancestors " + String.join(" ", sources);
    }

    /** {@code http://host[:port]} of a URL, or null. */
    static String origin(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.strip());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String policy() {
        return this.policy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (!response.containsHeader(HEADER)) {
            response.setHeader(HEADER, this.policy);
        }
        // P4 T8 (security review): no MIME sniffing (reports and SVGs carry model text), no full URLs to other sites
        if (!response.containsHeader("X-Content-Type-Options")) {
            response.setHeader("X-Content-Type-Options", "nosniff");
        }
        if (!response.containsHeader("Referrer-Policy")) {
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        }
        chain.doFilter(request, response);
    }
}

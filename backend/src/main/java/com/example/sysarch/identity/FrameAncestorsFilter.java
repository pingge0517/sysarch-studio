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
 */
@Component
public class FrameAncestorsFilter extends OncePerRequestFilter {

    public static final String HEADER = "Content-Security-Policy";

    private static final java.util.regex.Pattern ORIGIN = java.util.regex.Pattern.compile("https?://[A-Za-z0-9.-]+(:\\d{1,5})?");

    private final String policy;

    public FrameAncestorsFilter(AuthProperties auth, SyncProperties polarion) {
        this.policy = policy(auth.getFrameAncestors(), polarion.getBaseUrl());
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
        chain.doFilter(request, response);
    }
}

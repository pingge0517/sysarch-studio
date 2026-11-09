package com.example.sysarch.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Records the user of every Sirius GraphQL mutation (P2 T1): Sirius' frontend posts {@code /api/graphql} with the
 * mutation's input, whose {@code id} is the input id the editing context sees. The body is read once here and handed
 * on unchanged. The user is {@link CurrentUser} (the {@code sysarch_user} cookie the :3000 shell sets); none recorded
 * means anonymous. Additive: Sirius' own request handling is untouched.
 */
@Component
public class GraphQLEditorFilter extends OncePerRequestFilter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Editors editors;

    public GraphQLEditorFilter(Editors editors) {
        this.editors = Objects.requireNonNull(editors);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String type = request.getContentType();
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().equals("/api/graphql") || type == null
                || !type.toLowerCase().startsWith("application/json");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        String inputId = inputId(body);
        if (inputId != null) {
            this.editors.record(inputId, CurrentUser.of(request).map(Editors.Editor::new).orElse(Editors.Editor.ANONYMOUS));
        }
        chain.doFilter(new CachedBody(request, body), response);
    }

    /** {@code variables.input.id} of a mutation, or null (queries, or a body that is not a single operation). */
    public static String inputId(byte[] body) {
        try {
            JsonNode root = JSON.readTree(body);
            if (root == null || !root.path("query").asText("").stripLeading().startsWith("mutation")) {
                return null;
            }
            JsonNode id = root.path("variables").path("input").path("id");
            return id.isTextual() ? id.asText() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static final class CachedBody extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBody(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(this.body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(this.getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return this.body.length;
        }

        @Override
        public long getContentLengthLong() {
            return this.body.length;
        }
    }
}

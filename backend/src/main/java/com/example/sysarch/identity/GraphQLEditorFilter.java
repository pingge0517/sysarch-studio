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
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 20) // after IdentityFilter
public class GraphQLEditorFilter extends OncePerRequestFilter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String UPLOAD = "/api/graphql/upload";

    private final Editors editors;

    private final com.example.sysarch.access.Access access;

    public GraphQLEditorFilter(Editors editors, com.example.sysarch.access.Access access) {
        this.editors = Objects.requireNonNull(editors);
        this.access = Objects.requireNonNull(access);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String type = request.getContentType();
        if (!"POST".equals(request.getMethod()) || type == null) {
            return true;
        }
        String uri = request.getRequestURI();
        return !(uri.equals("/api/graphql") && type.toLowerCase().startsWith("application/json")
                || uri.equals(UPLOAD) && type.toLowerCase().startsWith("multipart/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (request.getRequestURI().equals(UPLOAD)) {
            // P2 T8: an uploaded document is an edit by the uploader (before, it passed as a system input, which no
            // lock or role applies to). The operation is the multipart field "operations".
            jakarta.servlet.http.Part operations = request.getPart("operations");
            if (operations != null) {
                Editors.Editor editor = CurrentUser.editor(request);
                inputIds(operations.getInputStream().readAllBytes()).forEach(id -> this.editors.record(id, editor));
            }
            chain.doFilter(request, response);
            return;
        }
        byte[] body = request.getInputStream().readAllBytes();
        String refusal = this.projectRefusal(request, body);
        if (refusal != null) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(refusal.getBytes(StandardCharsets.UTF_8));
            return;
        }
        Editors.Editor editor = CurrentUser.editor(request);
        inputIds(body).forEach(id -> this.editors.record(id, editor));
        chain.doFilter(new CachedBody(request, body), response);
    }

    /**
     * P2 T8: Sirius' project mutations outside any model edit. Deleting or renaming a project needs its administrator
     * (§7.1 "全量"); creating one needs a role that may model in unbound projects (so not anonymously). A refusal is
     * answered the GraphQL way, as an {@code ErrorPayload} with the reason, which Sirius' frontend shows.
     */
    String projectRefusal(HttpServletRequest request, byte[] body) {
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (IOException e) {
            return null;
        }
        if (root == null || !root.path("query").asText("").stripLeading().startsWith("mutation")) {
            return null;
        }
        String query = root.path("query").asText();
        JsonNode input = root.path("variables").path("input");
        String field;
        String projectId = null;
        com.example.sysarch.access.Permission permission;
        if (query.contains("deleteProject")) {
            field = "deleteProject";
            projectId = input.path("projectId").asText(null);
            permission = com.example.sysarch.access.Permission.PROJECT_ADMIN;
        } else if (query.contains("renameProject")) {
            field = "renameProject";
            projectId = input.path("projectId").asText(null);
            permission = com.example.sysarch.access.Permission.PROJECT_ADMIN;
        } else if (query.contains("createProject")) {
            field = "createProject";
            permission = com.example.sysarch.access.Permission.MODEL_EDIT;
        } else {
            return null;
        }
        try {
            this.access.require(CurrentUser.principal(request).orElse(null), projectId, permission, null);
            return null;
        } catch (com.example.sysarch.access.ForbiddenException e) {
            var payload = JSON.createObjectNode();
            var error = payload.putObject("data").putObject(field);
            error.put("__typename", "ErrorPayload");
            error.put("id", input.path("id").asText(""));
            var message = error.putArray("messages").addObject();
            message.put("body", e.getMessage());
            message.put("level", "ERROR");
            return payload.toString();
        }
    }

    /** {@code variables.input.id} of a mutation (Sirius' frontend), or null (queries, or not a single operation). */
    public static String inputId(byte[] body) {
        java.util.List<String> ids = inputIds(body);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * Every input id of a mutation (P2 T8): {@code variables.input.id} first, then the {@code id} of any other variable
     * object, whatever the client named it. An input the filter cannot see (an inline literal) stays unrecorded and is
     * then treated as anonymous by the edit guard.
     */
    public static java.util.List<String> inputIds(byte[] body) {
        try {
            JsonNode root = JSON.readTree(body);
            if (root == null || !root.path("query").asText("").stripLeading().startsWith("mutation")) {
                return java.util.List.of();
            }
            java.util.List<String> ids = new java.util.ArrayList<>();
            JsonNode input = root.path("variables").path("input").path("id");
            if (input.isTextual()) {
                ids.add(input.asText());
            }
            root.path("variables").fields().forEachRemaining(variable -> {
                JsonNode id = variable.getValue().path("id");
                if (id.isTextual() && !ids.contains(id.asText())) {
                    ids.add(id.asText());
                }
            });
            return ids;
        } catch (IOException e) {
            return java.util.List.of();
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

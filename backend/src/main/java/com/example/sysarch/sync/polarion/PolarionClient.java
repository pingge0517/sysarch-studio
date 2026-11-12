package com.example.sysarch.sync.polarion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Client for the Polarion REST API v1 ({@code <base>/rest/v1}, JSON:API), limited to the Work Item calls sync needs.
 *
 * <ul>
 * <li>The caller's {@link PolarionToken} is sent as-is on every call and never stored.</li>
 * <li>Every request first takes a permit from the shared {@link TokenBucket} (default 10 per second).</li>
 * <li>A 429 is normal (spec §11.2 #6): the client waits for {@code Retry-After} and retries, up to
 * {@code maxRetries} times, then throws {@link PolarionException.RateLimited}.</li>
 * </ul>
 *
 * <p>Work Item ids are the short ids (e.g. {@code SYS-12}); JSON:API resource ids are {@code project/SYS-12}.
 */
public class PolarionClient {

    /** Waiting for Retry-After, replaceable in tests. */
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    /** Never wait longer than this for one Retry-After, whatever Polarion says. */
    static final Duration MAX_RETRY_WAIT = Duration.ofSeconds(30);

    private static final String TYPE = "workitems";

    private final HttpClient http;

    private final ObjectMapper json;

    private final String restRoot;

    private final TokenBucket rateLimit;

    private final int maxRetries;

    private final Sleeper sleeper;

    /** @param baseUrl the Polarion server, e.g. {@code https://polarion.example.com/polarion} */
    public PolarionClient(String baseUrl, TokenBucket rateLimit, int maxRetries, ObjectMapper json, Sleeper sleeper) {
        this.restRoot = Objects.requireNonNull(baseUrl).replaceAll("/+$", "") + "/rest/v1";
        this.rateLimit = Objects.requireNonNull(rateLimit);
        this.maxRetries = maxRetries;
        this.json = Objects.requireNonNull(json);
        this.sleeper = Objects.requireNonNull(sleeper);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** A Polarion user and their roles: global role ids, project roles as {@code <project>/<role>} (P2 T8). */
    public record PolarionUser(String id, String name, List<String> globalRoles, List<String> projectRoles) {
    }

    /**
     * The owner of a token (P2 T8): Polarion REST has no "current user" resource, but its query language knows
     * {@code $[user.id]}, so the users matching {@code id:$[user.id]} are exactly the caller. Empty when Polarion
     * refuses the token (401/403).
     */
    public Optional<PolarionUser> currentUser(PolarionToken token) {
        HttpResponse<String> found = this.send(token, "GET", this.restRoot + "/users?query=" + encode("id:$[user.id]"), null);
        if (found.statusCode() == 401 || found.statusCode() == 403) {
            return Optional.empty();
        }
        this.requireSuccess(found, "find the token's user");
        JsonNode first = this.read(found).path("data").path(0).path("id");
        if (!first.isTextual()) {
            return Optional.empty();
        }
        return this.user(first.asText(), token);
    }

    /**
     * A user and their roles, read with any token allowed to read users (the service account's, for Polarion single
     * sign-on): empty when Polarion has no such user.
     */
    public Optional<PolarionUser> user(String id, PolarionToken token) {
        HttpResponse<String> user = this.send(token, "GET", this.restRoot + "/users/" + encode(id) + "?fields%5Busers%5D=%40all", null);
        if (user.statusCode() == 404) {
            return Optional.empty();
        }
        this.requireSuccess(user, "read user " + id);
        JsonNode data = this.read(user).path("data");
        String name = data.path("attributes").path("name").asText(id);
        return Optional.of(new PolarionUser(id, name, ids(data.path("relationships").path("globalRoles").path("data")),
                ids(data.path("relationships").path("projectRoles").path("data"))));
    }

    private static List<String> ids(JsonNode items) {
        List<String> ids = new ArrayList<>();
        items.forEach(item -> {
            if (item.path("id").isTextual()) {
                ids.add(item.path("id").asText());
            }
        });
        return ids;
    }

    /** The Work Item, or empty if Polarion answers 404 (deleted or never existed). */
    public Optional<WorkItem> getWorkItem(String projectId, String workItemId, PolarionToken token) {
        HttpResponse<String> response = this.send(token, "GET", this.workItemUrl(projectId, workItemId) + "?fields%5Bworkitems%5D=%40all", null);
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        this.requireSuccess(response, "get work item " + projectId + "/" + workItemId);
        return Optional.of(this.workItem(this.read(response).path("data")));
    }

    /** Creates one Work Item and returns its short id. */
    public String createWorkItem(String projectId, String type, Map<String, Object> attributes, PolarionToken token) {
        Map<String, Object> withType = new LinkedHashMap<>(attributes);
        withType.put("type", type);
        ObjectNode body = this.json.createObjectNode();
        ArrayNode data = body.putArray("data");
        ObjectNode resource = data.addObject();
        resource.put("type", TYPE);
        resource.set("attributes", this.json.valueToTree(withType));
        HttpResponse<String> response = this.send(token, "POST", this.projectUrl(projectId) + "/workitems", body);
        this.requireSuccess(response, "create work item in " + projectId);
        JsonNode created = this.read(response).path("data").path(0).path("id");
        if (created.isMissingNode()) {
            throw new PolarionException(response.statusCode(), "create work item in " + projectId + ": no id in response");
        }
        return shortId(created.asText());
    }

    /** Sets the given attributes; attributes not listed are left unchanged. */
    public void updateWorkItem(String projectId, String workItemId, Map<String, Object> attributes, PolarionToken token) {
        ObjectNode body = this.json.createObjectNode();
        ObjectNode data = body.putObject("data");
        data.put("type", TYPE);
        data.put("id", projectId + "/" + workItemId);
        data.set("attributes", this.json.valueToTree(attributes));
        HttpResponse<String> response = this.send(token, "PATCH", this.workItemUrl(projectId, workItemId), body);
        this.requireSuccess(response, "update work item " + projectId + "/" + workItemId);
    }

    /** Deletes Work Items; used only to undo Work Items a failed push created. */
    public void deleteWorkItems(String projectId, List<String> workItemIds, PolarionToken token) {
        if (workItemIds.isEmpty()) {
            return;
        }
        ObjectNode body = this.json.createObjectNode();
        ArrayNode data = body.putArray("data");
        workItemIds.forEach(id -> data.addObject().put("type", TYPE).put("id", projectId + "/" + id));
        HttpResponse<String> response = this.send(token, "DELETE", this.projectUrl(projectId) + "/workitems", body);
        this.requireSuccess(response, "delete work items in " + projectId);
    }

    /** Adds outgoing links to a Work Item (P1 T6: satisfy/verify as native Polarion links). */
    public void addLinks(String projectId, String workItemId, List<WorkItemLink> links, PolarionToken token) {
        if (links.isEmpty()) {
            return;
        }
        ObjectNode body = this.json.createObjectNode();
        ArrayNode data = body.putArray("data");
        for (WorkItemLink link : links) {
            ObjectNode resource = data.addObject();
            resource.put("type", "linkedworkitems");
            resource.putObject("attributes").put("role", link.role());
            resource.putObject("relationships").putObject("workItem").putObject("data").put("type", TYPE)
                    .put("id", link.targetProject() + "/" + link.targetId());
        }
        HttpResponse<String> response = this.send(token, "POST", this.workItemUrl(projectId, workItemId) + "/linkedworkitems", body);
        this.requireSuccess(response, "link work item " + projectId + "/" + workItemId);
    }

    /** Removes outgoing links of a Work Item. */
    public void removeLinks(String projectId, String workItemId, List<WorkItemLink> links, PolarionToken token) {
        if (links.isEmpty()) {
            return;
        }
        ObjectNode body = this.json.createObjectNode();
        ArrayNode data = body.putArray("data");
        links.forEach(link -> data.addObject().put("type", "linkedworkitems").put("id", link.id(projectId, workItemId)));
        HttpResponse<String> response = this.send(token, "DELETE", this.workItemUrl(projectId, workItemId) + "/linkedworkitems", body);
        this.requireSuccess(response, "unlink work item " + projectId + "/" + workItemId);
    }

    /** Work Items matching a Polarion (Lucene) query, all pages. */
    public List<WorkItem> queryWorkItems(String projectId, String query, PolarionToken token) {
        List<WorkItem> result = new ArrayList<>();
        for (int page = 1;; page++) {
            String url = this.projectUrl(projectId) + "/workitems?query=" + encode(query) + "&fields%5Bworkitems%5D=%40all&page%5Bsize%5D=100&page%5Bnumber%5D=" + page;
            HttpResponse<String> response = this.send(token, "GET", url, null);
            this.requireSuccess(response, "query work items in " + projectId);
            JsonNode body = this.read(response);
            body.path("data").forEach(node -> result.add(this.workItem(node)));
            if (body.path("data").size() < 100 || !body.path("links").has("next")) {
                return result;
            }
        }
    }

    // ---- transport ----

    private HttpResponse<String> send(PolarionToken token, String method, String url, JsonNode body) {
        Objects.requireNonNull(token, "a Polarion token is required");
        if (!this.restRoot.startsWith("http")) {
            throw new PolarionException(0, "Polarion is not configured: set POLARION_BASE_URL (sysarch.polarion.base-url)");
        }
        for (int attempt = 0;; attempt++) {
            HttpResponse<String> response;
            try {
                this.rateLimit.acquire();
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", token.authorizationHeader())
                        .header("Accept", "application/json");
                if (body == null) {
                    request.method(method, HttpRequest.BodyPublishers.noBody());
                } else {
                    request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(this.json.writeValueAsString(body)));
                }
                response = this.http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                // No query string (it can be long), and the exception type when there is no message (a refused connection has none).
                throw new PolarionException(method + " " + URI.create(url).getPath() + " failed: "
                        + (e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage()), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PolarionException(method + " " + url + " interrupted", e);
            }
            if (response.statusCode() != 429) {
                return response;
            }
            long retryAfter = retryAfterSeconds(response);
            if (attempt >= this.maxRetries) {
                throw new PolarionException.RateLimited(retryAfter);
            }
            try {
                this.sleeper.sleep(Duration.ofSeconds(retryAfter).compareTo(MAX_RETRY_WAIT) > 0 ? MAX_RETRY_WAIT : Duration.ofSeconds(retryAfter));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PolarionException(method + " " + url + " interrupted while backing off", e);
            }
        }
    }

    /** Retry-After as seconds or an HTTP date; 1 second if absent or unreadable. */
    static long retryAfterSeconds(HttpResponse<?> response) {
        Optional<String> header = response.headers().firstValue("Retry-After");
        if (header.isEmpty()) {
            return 1;
        }
        String value = header.get().trim();
        try {
            return Math.max(0, Long.parseLong(value));
        } catch (NumberFormatException e) {
            try {
                long seconds = Duration.between(OffsetDateTime.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)).getSeconds();
                return Math.max(0, seconds);
            } catch (DateTimeParseException ignored) {
                return 1;
            }
        }
    }

    private void requireSuccess(HttpResponse<String> response, String what) {
        if (response.statusCode() / 100 != 2) {
            throw new PolarionException(response.statusCode(), what + ": HTTP " + response.statusCode() + " " + abbreviate(response.body()));
        }
    }

    private JsonNode read(HttpResponse<String> response) {
        try {
            return response.body() == null || response.body().isBlank() ? this.json.createObjectNode() : this.json.readTree(response.body());
        } catch (IOException e) {
            throw new PolarionException(response.statusCode(), "unreadable Polarion response: " + abbreviate(response.body()));
        }
    }

    private WorkItem workItem(JsonNode resource) {
        JsonNode attributes = resource.path("attributes");
        Map<String, Object> values = this.json.convertValue(attributes, this.json.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        String updated = attributes.path("updated").asText(null);
        List<WorkItemLink> links = new ArrayList<>();
        resource.path("relationships").path("linkedWorkItems").path("data").forEach(link -> {
            WorkItemLink parsed = WorkItemLink.parse(link.path("id").asText(""));
            if (parsed != null) {
                links.add(parsed);
            }
        });
        return new WorkItem(shortId(resource.path("id").asText()), attributes.path("type").asText(null), attributes.path("title").asText(null), values,
                updated == null ? null : OffsetDateTime.parse(updated), links);
    }

    private String projectUrl(String projectId) {
        return this.restRoot + "/projects/" + encode(projectId);
    }

    private String workItemUrl(String projectId, String workItemId) {
        return this.projectUrl(projectId) + "/workitems/" + encode(workItemId);
    }

    private static String shortId(String resourceId) {
        return resourceId.substring(resourceId.lastIndexOf('/') + 1);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    /**
     * A Work Item as read from Polarion.
     *
     * @param id short id, e.g. {@code SYS-12}
     * @param attributes every attribute Polarion returned, including custom fields
     * @param updated last change, used as the Work Item revision
     */
    public record WorkItem(String id, String type, String title, Map<String, Object> attributes, OffsetDateTime updated, List<WorkItemLink> links) {

        public WorkItem {
            links = links == null ? List.of() : List.copyOf(links);
        }

        public WorkItem(String id, String type, String title, Map<String, Object> attributes, OffsetDateTime updated) {
            this(id, type, title, attributes, updated, List.of());
        }

        public Object attribute(String name) {
            return this.attributes.get(name);
        }
    }

    /**
     * An outgoing Work Item link (P1 T6): this Work Item {@code role} the target, e.g. {@code implements}. Polarion 2512
     * names a link {@code <project>/<source>/<role>/<project>/<target>} and returns a Work Item's outgoing links in
     * {@code relationships.linkedWorkItems} when it is read with all fields (verified against the local install).
     */
    public record WorkItemLink(String role, String targetProject, String targetId) {

        /** {@code null} for an id that is not a Work Item link id. */
        static WorkItemLink parse(String linkId) {
            String[] parts = linkId.split("/");
            return parts.length == 5 ? new WorkItemLink(parts[2], parts[3], parts[4]) : null;
        }

        String id(String sourceProject, String sourceId) {
            return sourceProject + "/" + sourceId + "/" + this.role + "/" + this.targetProject + "/" + this.targetId;
        }
    }
}

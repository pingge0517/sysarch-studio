package com.example.sysarch.identity;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.polarion.sso.Assertions;
import com.example.sysarch.sync.SyncProperties;
import com.example.sysarch.sync.polarion.PolarionClient;
import com.example.sysarch.sync.polarion.TokenBucket;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/** Embedding in Polarion: the extension's assertions, checked by the backend, and the sign-on endpoint. */
class PolarionSsoTest {

    private static final String KEY = "shared-secret";

    private static final Instant NOW = Instant.parse("2026-11-12T08:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private WireMockServer polarion;

    @BeforeEach
    void start() {
        this.polarion = new WireMockServer(wireMockConfig().dynamicPort());
        this.polarion.start();
    }

    @AfterEach
    void stop() {
        this.polarion.stop();
    }

    private PolarionSso sso() {
        return new PolarionSso(KEY, Duration.ofMinutes(2), this.clock);
    }

    private static String issue(String user, long now) {
        return Assertions.issue(user, KEY.getBytes(StandardCharsets.UTF_8), now);
    }

    /** A hand-made assertion with these claims, signed with {@code key}. */
    private static String forge(String json, String key) {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return "v1." + payload + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(PolarionSso.sign(key.getBytes(StandardCharsets.UTF_8), "v1." + payload));
    }

    @Test
    void theExtensionsAssertionNamesTheUserOnceOnly() {
        PolarionSso sso = this.sso();
        String assertion = issue("alice", NOW.getEpochSecond());

        assertEquals("alice", sso.verify(assertion));
        assertThrows(PolarionSso.InvalidAssertionException.class, () -> sso.verify(assertion), "a replay is refused");
    }

    @Test
    void anythingElseIsRefused() {
        PolarionSso sso = this.sso();
        long now = NOW.getEpochSecond();
        String good = issue("alice", now);
        String[] parts = good.split("\\.");
        String nonce = "\"nonce\":\"0123456789abcdef0123456789abcdef\"";
        for (String bad : new String[] { null, "", "x", "v2." + parts[1] + "." + parts[2], parts[0] + "." + parts[1] + "x." + parts[2],
                Assertions.issue("alice", "other-key".getBytes(StandardCharsets.UTF_8), now), issue("alice", now - 200), issue("alice", now + 120),
                forge("{\"sub\":\"alice\",\"iat\":" + now + ",\"exp\":" + (now + 3600) + "," + nonce + ",\"aud\":\"sysarch\"}", KEY),
                forge("{\"sub\":\"alice\",\"iat\":" + now + ",\"exp\":" + (now + 60) + "," + nonce + ",\"aud\":\"elsewhere\"}", KEY),
                forge("{\"sub\":\"\",\"iat\":" + now + ",\"exp\":" + (now + 60) + "," + nonce + ",\"aud\":\"sysarch\"}", KEY) }) {
            assertThrows(PolarionSso.InvalidAssertionException.class, () -> sso.verify(bad), String.valueOf(bad));
        }
        assertThrows(PolarionSso.InvalidAssertionException.class, () -> new PolarionSso("", Duration.ofMinutes(2), this.clock).verify(good),
                "not configured");
    }

    private AuthController controller(String serviceToken) {
        this.polarion.stubFor(get(urlPathEqualTo("/polarion/rest/v1/users/alice")).withHeader("Authorization", equalTo("Bearer svc"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{\"type\":\"users\",\"id\":\"alice\",\"attributes\":{\"id\":\"alice\",\"name\":\"Alice Architect\"},"
                                + "\"relationships\":{\"globalRoles\":{\"data\":[{\"type\":\"globalroles\",\"id\":\"user\"}]},"
                                + "\"projectRoles\":{\"data\":[{\"type\":\"projectroles\",\"id\":\"sysarch/project_user\"}]}}}}")));
        this.polarion.stubFor(get(urlPathEqualTo("/polarion/rest/v1/users/ghost")).willReturn(aResponse().withStatus(404)));
        PolarionClient client = new PolarionClient(this.polarion.baseUrl() + "/polarion", new TokenBucket(1000), 0, new ObjectMapper(), d -> {
        });
        SyncProperties sync = new SyncProperties();
        sync.getProjects().put("sysarch", "model of sysarch");
        sync.setSyncToken(serviceToken);
        AuthProperties properties = new AuthProperties();
        properties.setCookieSameSite("None");
        properties.setSecureCookie(true);
        SessionCookies cookies = new SessionCookies("k", Clock.systemUTC());
        return new AuthController(new Identities(properties, client, sync, cookies), cookies,
                new PolarionSso(KEY, Duration.ofMinutes(2), Clock.systemUTC()));
    }

    @Test
    void signingOnSetsTheSessionCookieWithPolarionsRolesAndGoesOn() {
        AuthController controller = this.controller("svc");
        long now = Instant.now().getEpochSecond();

        ResponseEntity<String> answer = controller.polarionSso(issue("alice", now), "/?embed=1&workItem=SYS-7");

        assertEquals(303, answer.getStatusCode().value());
        assertEquals("/?embed=1&workItem=SYS-7", answer.getHeaders().getFirst(HttpHeaders.LOCATION));
        String cookie = answer.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertTrue(cookie.startsWith(SessionCookies.COOKIE + "=") && cookie.contains("HttpOnly") && cookie.contains("SameSite=None") && cookie.contains("Secure"),
                cookie);
        Principal principal = new SessionCookies("k", Clock.systemUTC()).read(cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'))).orElseThrow();
        assertEquals("alice", principal.user());
        assertEquals("Alice Architect", principal.name());
        assertEquals(Set.of("project_user"), principal.projectRoles().get("sysarch"));

        assertEquals("/", controller.polarionSso(issue("alice", now), "https://evil.example/").getHeaders().getFirst(HttpHeaders.LOCATION),
                "never off this host");
        assertEquals(401, controller.polarionSso(issue("alice", now - 600), "/").getStatusCode().value());
        assertEquals(401, controller.polarionSso(issue("ghost", now), "/").getStatusCode().value(), "no such Polarion user");
        assertEquals(503, this.controller("").polarionSso(issue("alice", now), "/").getStatusCode().value(), "no service account");
        PolarionClient client = new PolarionClient(this.polarion.baseUrl() + "/polarion", new TokenBucket(1000), 0, new ObjectMapper(), d -> {
        });
        SessionCookies cookies = new SessionCookies("k", Clock.systemUTC());
        assertEquals(503, new AuthController(new Identities(new AuthProperties(), client, new SyncProperties(), cookies), cookies)
                .polarionSso(issue("alice", now), "/").getStatusCode().value(), "single sign-on not configured");
    }

    /** Polarion on another site: SameSite=None, Secure and, with cookie-partitioned, Partitioned (CHIPS). */
    @Test
    void theSessionCookieCanBePartitionedForAnotherSite() {
        AuthProperties properties = new AuthProperties();
        String lax = AuthController.sessionCookie(properties, "v", Duration.ofHours(8)).toString();
        assertTrue(lax.contains("SameSite=Lax") && !lax.contains("Partitioned") && !lax.contains("Secure"), lax);

        properties.setCookieSameSite("None");
        properties.setSecureCookie(true);
        properties.setCookiePartitioned(true);
        String chips = AuthController.sessionCookie(properties, "v", Duration.ofHours(8)).toString();
        assertTrue(chips.contains("SameSite=None") && chips.contains("Secure") && chips.contains("Partitioned") && chips.contains("HttpOnly"), chips);
        assertTrue(AuthController.sessionCookie(properties, "", Duration.ZERO).toString().contains("Partitioned"),
                "logout clears the partitioned cookie, which only a partitioned Set-Cookie can address");
    }

    /** P4 T8: every response carries the framing policy, no MIME sniffing and a restrained referrer. */
    @Test
    void everyResponseCarriesTheSecurityHeaders() throws Exception {
        SyncProperties polarion = new SyncProperties();
        polarion.setBaseUrl("http://localhost/polarion");
        FrameAncestorsFilter filter = new FrameAncestorsFilter(new AuthProperties(), polarion);
        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();

        filter.doFilter(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/reports/x/files/html"), response,
                new org.springframework.mock.web.MockFilterChain());

        assertEquals("frame-ancestors 'self' http://localhost", response.getHeader("Content-Security-Policy"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("strict-origin-when-cross-origin", response.getHeader("Referrer-Policy"));
    }

    @Test
    void framingIsOnlyForUsAndPolarion() {
        assertEquals("frame-ancestors 'self' http://localhost", FrameAncestorsFilter.policy("", "http://localhost/polarion"));
        assertEquals("frame-ancestors 'self' https://polarion.example.com:8443", FrameAncestorsFilter.policy(null, "https://polarion.example.com:8443/polarion"));
        assertEquals("frame-ancestors 'self' https://a.example https://b.example", FrameAncestorsFilter.policy("https://a.example, https://b.example", "http://x"));
        assertEquals("frame-ancestors 'self'", FrameAncestorsFilter.policy("", ""), "no Polarion: nobody else");
        assertEquals("frame-ancestors 'self'", FrameAncestorsFilter.policy("x;script-src *", ""), "no injected directives");
        assertEquals("frame-ancestors 'self' https://ok.example", FrameAncestorsFilter.policy("* https://ok.example 'unsafe-inline'", ""), "explicit origins only");
    }
}

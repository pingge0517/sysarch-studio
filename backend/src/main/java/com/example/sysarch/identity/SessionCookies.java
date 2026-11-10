package com.example.sysarch.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.LoggerFactory;

/**
 * The session cookie (P2 T8, user's decision "验证令牌后发签名 cookie"): once a token was verified against Polarion,
 * the browser keeps {@code base64url(json) . base64url(HMAC-SHA256)} with the user, their Polarion roles and an
 * expiry. The token is in neither the cookie nor the server, and the server keeps no session state. Tampering breaks
 * the signature; an expired cookie is ignored.
 */
public class SessionCookies {

    public static final String COOKIE = "sysarch_session";

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Base64.Encoder ENCODE = Base64.getUrlEncoder().withoutPadding();

    private static final Base64.Decoder DECODE = Base64.getUrlDecoder();

    private final byte[] key;

    private final Clock clock;

    public SessionCookies(String secret, Clock clock) {
        this.clock = clock;
        if (secret == null || secret.isBlank()) {
            this.key = new byte[32];
            new SecureRandom().nextBytes(this.key);
            LoggerFactory.getLogger(SessionCookies.class)
                    .warn("sysarch.auth.session-secret is not set: sessions use a random key and end when the backend restarts (set SYSARCH_SESSION_SECRET)");
        } else {
            this.key = secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String issue(Principal principal, Instant expires) {
        ObjectNode body = JSON.createObjectNode();
        body.put("u", principal.user());
        body.put("n", principal.name());
        body.put("s", principal.source());
        body.put("exp", expires.getEpochSecond());
        ArrayNode global = body.putArray("g");
        principal.globalRoles().stream().sorted().forEach(global::add);
        ObjectNode projects = body.putObject("p");
        new TreeMap<>(principal.projectRoles()).forEach((project, roles) -> {
            ArrayNode list = projects.putArray(project);
            roles.stream().sorted().forEach(list::add);
        });
        String payload = ENCODE.encodeToString(body.toString().getBytes(StandardCharsets.UTF_8));
        return payload + "." + ENCODE.encodeToString(this.sign(payload));
    }

    /** The principal of a valid, unexpired cookie value. */
    public Optional<Principal> read(String value) {
        if (value == null) {
            return Optional.empty();
        }
        int dot = value.indexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        String payload = value.substring(0, dot);
        try {
            if (!MessageDigest.isEqual(this.sign(payload), DECODE.decode(value.substring(dot + 1)))) {
                return Optional.empty();
            }
            JsonNode body = JSON.readTree(DECODE.decode(payload));
            if (body.path("exp").asLong(0) <= this.clock.instant().getEpochSecond()) {
                return Optional.empty();
            }
            Set<String> global = new TreeSet<>();
            body.path("g").forEach(g -> global.add(g.asText()));
            Map<String, Set<String>> projects = new TreeMap<>();
            body.path("p").fields().forEachRemaining(e -> {
                Set<String> roles = new TreeSet<>();
                e.getValue().forEach(r -> roles.add(r.asText()));
                projects.put(e.getKey(), roles);
            });
            return Optional.of(new Principal(body.path("u").asText(), body.path("n").asText(null), global, projects, body.path("s").asText("polarion")));
        } catch (RuntimeException | java.io.IOException e) {
            return Optional.empty();
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(this.key, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}

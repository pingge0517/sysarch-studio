package com.example.sysarch.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Polarion single sign-on (embedding in Polarion). The SysArch extension installed in Polarion
 * ({@code polarion-sso/}) runs behind Polarion's own login, so it knows who is logged in; it signs that as an
 * assertion and posts it to {@code POST /api/v1/auth/polarion-sso}. No password or token leaves Polarion.
 *
 * <p>Format, shared with {@code com.example.sysarch.polarion.sso.Assertions}:
 * {@code v1.<base64url(JSON)>.<base64url(HMAC-SHA256(key, "v1." + base64url(JSON)))>}, the JSON being
 * {@code {"sub": user id, "iat": seconds, "exp": seconds, "nonce": random, "aud": "sysarch"}}. Accepted only with a
 * valid signature, the audience {@code sysarch}, not expired, not issued in the future (30 s clock skew allowed), not
 * living longer than {@code polarion-sso-max-age}, and only once (nonces are remembered until they expire).
 */
public class PolarionSso {

    public static final String VERSION = "v1";

    public static final String AUDIENCE = "sysarch";

    private static final long SKEW_SECONDS = 30;

    /** An assertion that is not accepted; the message says why (logged, not shown in detail to the browser). */
    public static class InvalidAssertionException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public InvalidAssertionException(String message) {
            super(message);
        }
    }

    private final byte[] key;

    private final Duration maxAge;

    private final Clock clock;

    private final ObjectMapper json = new ObjectMapper();

    /** nonce → expiry (epoch seconds). */
    private final Map<String, Long> used = new ConcurrentHashMap<>();

    public PolarionSso(String key, Duration maxAge, Clock clock) {
        this.key = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
        this.maxAge = Objects.requireNonNull(maxAge);
        this.clock = Objects.requireNonNull(clock);
    }

    public boolean enabled() {
        return this.key.length > 0;
    }

    /** The Polarion user id the assertion names. */
    public String verify(String assertion) {
        if (!this.enabled()) {
            throw new InvalidAssertionException("single sign-on is not configured");
        }
        String[] parts = assertion == null ? new String[0] : assertion.strip().split("\\.");
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new InvalidAssertionException("not a v1 assertion");
        }
        byte[] expected = sign(this.key, VERSION + "." + parts[1]);
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new InvalidAssertionException("bad signature encoding");
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new InvalidAssertionException("bad signature");
        }
        JsonNode claims;
        try {
            claims = this.json.readTree(Base64.getUrlDecoder().decode(parts[1]));
        } catch (Exception e) {
            throw new InvalidAssertionException("bad payload");
        }
        String user = claims.path("sub").asText("");
        String nonce = claims.path("nonce").asText("");
        long iat = claims.path("iat").asLong(-1);
        long exp = claims.path("exp").asLong(-1);
        long now = this.clock.instant().getEpochSecond();
        if (!AUDIENCE.equals(claims.path("aud").asText())) {
            throw new InvalidAssertionException("wrong audience");
        }
        if (user.isBlank() || nonce.length() < 16 || iat < 0 || exp < 0) {
            throw new InvalidAssertionException("incomplete claims");
        }
        if (exp <= now - SKEW_SECONDS || iat > now + SKEW_SECONDS || exp - iat > this.maxAge.toSeconds()) {
            throw new InvalidAssertionException("expired or too long-lived");
        }
        this.used.values().removeIf(e -> e < now - SKEW_SECONDS);
        if (this.used.putIfAbsent(nonce, exp) != null) {
            throw new InvalidAssertionException("already used");
        }
        return user;
    }

    /** HMAC-SHA256, as the extension computes it. */
    public static byte[] sign(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}

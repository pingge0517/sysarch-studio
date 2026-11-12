package com.example.sysarch.polarion.sso;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The signed statement "this Polarion user is logged in", checked by SysArch's {@code identity.PolarionSso}:
 * {@code v1.<base64url(JSON)>.<base64url(HMAC-SHA256(key, "v1." + base64url(JSON)))>}, the JSON being
 * {@code {"sub": user id, "iat": seconds, "exp": seconds, "nonce": random, "aud": "sysarch"}}. Plain JDK only, so the
 * extension carries no library into Polarion.
 */
public final class Assertions {

    public static final long LIFETIME_SECONDS = 60;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Assertions() {
    }

    public static String issue(String user, byte[] key, long nowSeconds) {
        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);
        String json = "{\"sub\":\"" + escape(user) + "\",\"iat\":" + nowSeconds + ",\"exp\":" + (nowSeconds + LIFETIME_SECONDS) + ",\"nonce\":\""
                + HexFormat.of().formatHex(nonce) + "\",\"aud\":\"sysarch\"}";
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return "v1." + payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(key, "v1." + payload));
    }

    static byte[] sign(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** JSON string escaping (user ids are plain, but never trust that). */
    static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}

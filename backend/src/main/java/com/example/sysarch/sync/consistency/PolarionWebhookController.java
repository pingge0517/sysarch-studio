package com.example.sysarch.sync.consistency;

import com.example.sysarch.sync.SyncProperties;
import com.example.sysarch.sync.api.model.WebhooksPolarionPostRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code POST /api/v1/webhooks/polarion} (spec §6.1, T8 step 3): verifies the signature, queues the event, answers 202.
 * It never changes data; {@link PolarionEventConsumer} turns queued events into marks for the next report.
 *
 * <p>The signature is HMAC-SHA256 of the raw request body with {@code sysarch.polarion.webhook-secret}
 * ({@code POLARION_WEBHOOK_SECRET}), hex encoded, optionally prefixed {@code sha256=}. Hand-written rather than the
 * generated {@code WebhooksApi}, which hands over a parsed body: the HMAC must be computed on the exact bytes sent.
 */
@RestController
public class PolarionWebhookController {

    private final Logger logger = LoggerFactory.getLogger(PolarionWebhookController.class);

    private final ConsistencyStore store;

    private final SyncProperties properties;

    private final ObjectMapper json;

    public PolarionWebhookController(ConsistencyStore store, SyncProperties properties, ObjectMapper json) {
        this.store = Objects.requireNonNull(store);
        this.properties = Objects.requireNonNull(properties);
        this.json = Objects.requireNonNull(json);
    }

    @PostMapping(value = "/api/v1/webhooks/polarion", consumes = "application/json")
    public ResponseEntity<Void> receive(@RequestHeader("X-Polarion-Signature") String signature, @RequestBody byte[] body) {
        String secret = this.properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "webhooks are disabled: POLARION_WEBHOOK_SECRET is not set");
        }
        if (!verify(secret, body, signature)) {
            this.logger.warn("Rejected a Polarion webhook with an invalid signature");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid X-Polarion-Signature");
        }
        WebhooksPolarionPostRequest event;
        try {
            event = this.json.readValue(body, WebhooksPolarionPostRequest.class);
        } catch (IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unreadable webhook event");
        }
        if (event.getEvent() == null || blank(event.getProjectId()) || blank(event.getWorkItemId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "event, projectId and workItemId are required");
        }
        this.store.enqueue(event.getProjectId(), event.getEvent().getValue(), shortId(event.getProjectId(), event.getWorkItemId()), event.getRevision());
        return ResponseEntity.accepted().build();
    }

    /** Constant-time comparison of the expected and the sent signature. */
    static boolean verify(String secret, byte[] body, String signature) {
        if (signature == null) {
            return false;
        }
        String sent = signature.trim().toLowerCase(Locale.ROOT);
        if (sent.startsWith("sha256=")) {
            sent = sent.substring("sha256=".length());
        }
        return MessageDigest.isEqual(sign(secret, body).getBytes(StandardCharsets.US_ASCII), sent.getBytes(StandardCharsets.US_ASCII));
    }

    static String sign(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Polarion REST ids are {@code project/WI-1}; bindings store the short id. */
    private static String shortId(String projectId, String workItemId) {
        return workItemId.startsWith(projectId + "/") ? workItemId.substring(projectId.length() + 1) : workItemId;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

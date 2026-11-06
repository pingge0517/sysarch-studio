package com.example.sysarch.sync.polarion;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T7 step 2: the Polarion REST client against a WireMock Polarion (JSON:API shapes of REST API v1). */
class PolarionClientTest {

    private static final PolarionToken TOKEN = new PolarionToken("user-pat-123");

    private static final String WORK_ITEM = "/polarion/rest/v1/projects/sysarch/workitems/SYS-1";

    private WireMockServer polarion;

    private final List<Duration> sleeps = new ArrayList<>();

    private PolarionClient client;

    @BeforeEach
    void start() {
        this.polarion = new WireMockServer(wireMockConfig().dynamicPort());
        this.polarion.start();
        this.client = new PolarionClient(this.polarion.baseUrl() + "/polarion", new TokenBucket(1000), 3, new ObjectMapper(), this.sleeps::add);
    }

    @AfterEach
    void stop() {
        this.polarion.stop();
    }

    @Test
    void readsAWorkItemAndPassesTheCallersTokenThrough() {
        this.polarion.stubFor(get(urlPathEqualTo(WORK_ITEM)).willReturn(okJson("""
                {"data": {"type": "workitems", "id": "sysarch/SYS-1",
                  "attributes": {"type": "architecture_element", "title": "Domain controller", "updated": "2026-11-01T10:00:00Z",
                                 "sysarchName": "DomainController"}}}""")));

        var workItem = this.client.getWorkItem("sysarch", "SYS-1", TOKEN).orElseThrow();

        assertEquals("SYS-1", workItem.id());
        assertEquals("architecture_element", workItem.type());
        assertEquals("Domain controller", workItem.title());
        assertEquals("DomainController", workItem.attribute("sysarchName"));
        this.polarion.verify(getRequestedFor(urlPathEqualTo(WORK_ITEM)).withHeader("Authorization", equalTo("Bearer user-pat-123")));
    }

    @Test
    void aMissingWorkItemIsEmptyNotAnError() {
        this.polarion.stubFor(get(urlPathEqualTo(WORK_ITEM)).willReturn(aResponse().withStatus(404)));

        assertTrue(this.client.getWorkItem("sysarch", "SYS-1", TOKEN).isEmpty());
    }

    @Test
    void createsAWorkItemAsJsonApi() {
        this.polarion.stubFor(post(urlPathEqualTo("/polarion/rest/v1/projects/sysarch/workitems"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"data\": [{\"type\": \"workitems\", \"id\": \"sysarch/SYS-7\"}]}")));
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("title", "ECU");
        attributes.put("sysarchName", "ECU");

        assertEquals("SYS-7", this.client.createWorkItem("sysarch", "architecture_element", attributes, TOKEN));

        this.polarion.verify(postRequestedFor(urlPathEqualTo("/polarion/rest/v1/projects/sysarch/workitems")).withRequestBody(equalToJson("""
                {"data": [{"type": "workitems", "attributes": {"title": "ECU", "sysarchName": "ECU", "type": "architecture_element"}}]}""")));
    }

    @Test
    void updatesOnlyTheGivenAttributes() {
        this.polarion.stubFor(patch(urlPathEqualTo(WORK_ITEM)).willReturn(aResponse().withStatus(204)));

        this.client.updateWorkItem("sysarch", "SYS-1", Map.of("sysarchLayer", "PHYSICAL"), TOKEN);

        this.polarion.verify(patchRequestedFor(urlPathEqualTo(WORK_ITEM)).withRequestBody(equalToJson("""
                {"data": {"type": "workitems", "id": "sysarch/SYS-1", "attributes": {"sysarchLayer": "PHYSICAL"}}}""")));
    }

    @Test
    void deletesWorkItemsByResourceId() {
        this.polarion.stubFor(delete(urlPathEqualTo("/polarion/rest/v1/projects/sysarch/workitems")).willReturn(aResponse().withStatus(204)));

        this.client.deleteWorkItems("sysarch", List.of("SYS-7", "SYS-8"), TOKEN);

        this.polarion.verify(deleteRequestedFor(urlPathEqualTo("/polarion/rest/v1/projects/sysarch/workitems")).withRequestBody(equalToJson("""
                {"data": [{"type": "workitems", "id": "sysarch/SYS-7"}, {"type": "workitems", "id": "sysarch/SYS-8"}]}""")));
    }

    /** Spec §6.3 / §11.2 #6: a 429 is waited out per Retry-After, not passed on. */
    @Test
    void backsOffOnRateLimitAndSucceeds() {
        this.polarion.stubFor(get(urlPathEqualTo(WORK_ITEM)).inScenario("limit").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "2")).willSetStateTo("once"));
        this.polarion.stubFor(get(urlPathEqualTo(WORK_ITEM)).inScenario("limit").whenScenarioStateIs("once")
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1")).willSetStateTo("free"));
        this.polarion.stubFor(get(urlPathEqualTo(WORK_ITEM)).inScenario("limit").whenScenarioStateIs("free")
                .willReturn(okJson("{\"data\": {\"type\": \"workitems\", \"id\": \"sysarch/SYS-1\", \"attributes\": {\"title\": \"t\"}}}")));

        assertEquals("t", this.client.getWorkItem("sysarch", "SYS-1", TOKEN).orElseThrow().title());
        assertEquals(List.of(Duration.ofSeconds(2), Duration.ofSeconds(1)), this.sleeps);
    }

    @Test
    void givesUpAfterThreeRetries() {
        this.polarion.stubFor(get(urlPathEqualTo(WORK_ITEM)).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "5")));

        var limited = assertThrows(PolarionException.RateLimited.class, () -> this.client.getWorkItem("sysarch", "SYS-1", TOKEN));

        assertEquals(5, limited.retryAfterSeconds());
        assertEquals(3, this.sleeps.size(), "three retries");
        this.polarion.verify(4, getRequestedFor(urlPathEqualTo(WORK_ITEM)));
    }

    @Test
    void neverWaitsLongerThanTheCapForOneRetryAfter() {
        this.polarion.stubFor(get(urlPathEqualTo(WORK_ITEM)).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "3600")));

        assertThrows(PolarionException.RateLimited.class, () -> this.client.getWorkItem("sysarch", "SYS-1", TOKEN));
        assertTrue(this.sleeps.stream().allMatch(d -> d.equals(PolarionClient.MAX_RETRY_WAIT)), String.valueOf(this.sleeps));
    }

    @Test
    void otherErrorsCarryTheStatus() {
        this.polarion.stubFor(get(urlPathEqualTo(WORK_ITEM)).willReturn(aResponse().withStatus(403).withBody("forbidden")));

        var error = assertThrows(PolarionException.class, () -> this.client.getWorkItem("sysarch", "SYS-1", TOKEN));
        assertEquals(403, error.status());
        assertFalse(error instanceof PolarionException.RateLimited);
    }

    @Test
    void tokenIsRedactedAndParsedFromTheAuthorizationHeader() {
        assertEquals("PolarionToken[***]", TOKEN.toString());
        assertEquals("abc", PolarionToken.fromAuthorizationHeader("Bearer abc").value());
        assertNull(PolarionToken.fromAuthorizationHeader("Basic abc"));
        assertNull(PolarionToken.fromAuthorizationHeader(null));
    }

    /** 10 per second: the 11th immediate call waits about 100 ms. */
    @Test
    void tokenBucketLimitsTheRate() throws InterruptedException {
        long[] now = {0};
        List<Long> waits = new ArrayList<>();
        var bucket = new TokenBucket(10, new TokenBucket.Clock() {
            @Override
            public long nanoTime() {
                return now[0];
            }

            @Override
            public void sleepNanos(long nanos) {
                waits.add(nanos);
                now[0] += nanos;
            }
        });
        for (int i = 0; i < 10; i++) {
            bucket.acquire();
        }
        assertTrue(waits.isEmpty(), "a burst of 10 is free");

        bucket.acquire();

        assertEquals(1, waits.size());
        assertEquals(100_000_000L, waits.get(0), 1_000_000L);
    }
}

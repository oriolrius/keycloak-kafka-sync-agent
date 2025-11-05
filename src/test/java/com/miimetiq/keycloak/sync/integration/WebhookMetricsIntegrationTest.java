package com.miimetiq.keycloak.sync.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miimetiq.keycloak.sync.metrics.SyncMetrics;
import com.miimetiq.keycloak.sync.webhook.KeycloakAdminEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for webhook metrics.
 * <p>
 * Tests that webhook events properly track metrics including:
 * - sync_webhook_received_total counter
 * - sync_queue_backlog gauge
 * - sync_webhook_processing_duration_seconds histogram
 * - sync_webhook_signature_failures_total counter
 */
@QuarkusTest
@DisplayName("Webhook Metrics Integration Tests")
class WebhookMetricsIntegrationTest {

    @Inject
    MeterRegistry registry;

    @Inject
    SyncMetrics syncMetrics;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "keycloak.webhook-hmac-secret")
    String hmacSecret;

    private static final String WEBHOOK_ENDPOINT = "/api/kc/events";
    private static final String SIGNATURE_HEADER = "X-Keycloak-Signature";

    @BeforeEach
    void setUp() {
        // Ensure metrics are initialized
        syncMetrics.init();
    }

    @Test
    @DisplayName("Queue backlog gauge should be registered and track queue size")
    void testQueueBacklogGauge() {
        // When: checking for gauge registration
        Gauge queueBacklogGauge = registry.find("sync_queue_backlog").gauge();

        // Then: gauge should exist
        assertNotNull(queueBacklogGauge, "sync_queue_backlog gauge should be registered");

        // And: gauge should report current queue size (initially 0 or small)
        assertTrue(queueBacklogGauge.value() >= 0,
                "Queue backlog should be non-negative");
    }

    @Test
    @DisplayName("Webhook received counter should track successful events")
    void testWebhookReceivedCounterSuccess() throws Exception {
        // Given: a valid Keycloak admin event
        KeycloakAdminEvent event = createTestEvent("test-realm", "USER", "CREATE");
        String payload = objectMapper.writeValueAsString(event);
        String signature = computeHmac(payload, hmacSecret);

        // When: sending the event
        given()
                .contentType(ContentType.JSON)
                .header(SIGNATURE_HEADER, signature)
                .body(payload)
                .when()
                .post(WEBHOOK_ENDPOINT)
                .then()
                .statusCode(200);

        // Then: counter should be incremented with SUCCESS result
        Counter counter = registry.find("sync_webhook_received_total")
                .tag("realm", "test-realm")
                .tag("event_type", "USER")
                .tag("result", "SUCCESS")
                .counter();

        assertNotNull(counter, "Webhook received counter should exist");
        assertTrue(counter.count() >= 1.0,
                "Counter should have at least one increment for successful event");
    }

    @Test
    @DisplayName("Webhook received counter should track invalid signature events")
    void testWebhookReceivedCounterInvalidSignature() throws Exception {
        // Given: a valid event but invalid signature
        KeycloakAdminEvent event = createTestEvent("test-realm", "USER", "CREATE");
        String payload = objectMapper.writeValueAsString(event);
        String invalidSignature = "invalid_signature_abc123";

        // When: sending the event with invalid signature
        given()
                .contentType(ContentType.JSON)
                .header(SIGNATURE_HEADER, invalidSignature)
                .body(payload)
                .when()
                .post(WEBHOOK_ENDPOINT)
                .then()
                .statusCode(401);

        // Then: counter should be incremented with INVALID_SIGNATURE result
        Counter counter = registry.find("sync_webhook_received_total")
                .tag("realm", "unknown")
                .tag("event_type", "unknown")
                .tag("result", "INVALID_SIGNATURE")
                .counter();

        assertNotNull(counter, "Webhook received counter for invalid signature should exist");
        assertTrue(counter.count() >= 1.0,
                "Counter should have at least one increment for invalid signature");
    }

    @Test
    @DisplayName("Webhook signature failure counter should track validation failures")
    void testSignatureFailureCounter() throws Exception {
        // Given: a valid event but invalid signature
        KeycloakAdminEvent event = createTestEvent("test-realm", "USER", "CREATE");
        String payload = objectMapper.writeValueAsString(event);
        String invalidSignature = "invalid_signature_abc123";

        // Record initial count
        Counter counterBefore = registry.find("sync_webhook_signature_failures_total").counter();
        double initialCount = counterBefore != null ? counterBefore.count() : 0.0;

        // When: sending the event with invalid signature
        given()
                .contentType(ContentType.JSON)
                .header(SIGNATURE_HEADER, invalidSignature)
                .body(payload)
                .when()
                .post(WEBHOOK_ENDPOINT)
                .then()
                .statusCode(401);

        // Then: signature failure counter should be incremented
        Counter counterAfter = registry.find("sync_webhook_signature_failures_total").counter();

        assertNotNull(counterAfter, "Signature failure counter should exist");
        assertEquals(initialCount + 1.0, counterAfter.count(), 0.001,
                "Counter should be incremented by 1 for signature failure");
    }

    @Test
    @DisplayName("Webhook processing duration histogram should track processing time")
    void testWebhookProcessingDurationHistogram() throws Exception {
        // Given: a valid event
        KeycloakAdminEvent event = createTestEvent("test-realm", "USER", "UPDATE");
        String payload = objectMapper.writeValueAsString(event);
        String signature = computeHmac(payload, hmacSecret);

        // When: sending the event
        given()
                .contentType(ContentType.JSON)
                .header(SIGNATURE_HEADER, signature)
                .body(payload)
                .when()
                .post(WEBHOOK_ENDPOINT)
                .then()
                .statusCode(200);

        // Then: processing duration histogram should have recorded the event
        Timer timer = registry.find("sync_webhook_processing_duration_seconds")
                .tag("realm", "test-realm")
                .tag("event_type", "USER")
                .timer();

        assertNotNull(timer, "Processing duration histogram should exist");
        assertTrue(timer.count() >= 1,
                "Histogram should have at least one recording");
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) >= 0,
                "Histogram should have recorded non-negative duration");
    }

    @Test
    @DisplayName("Webhook received counter should track invalid payload events")
    void testWebhookReceivedCounterInvalidPayload() {
        // Given: an invalid JSON payload
        String invalidPayload = "{invalid json}";
        String signature = computeHmac(invalidPayload, hmacSecret);

        // When: sending the invalid payload
        given()
                .contentType(ContentType.JSON)
                .header(SIGNATURE_HEADER, signature)
                .body(invalidPayload)
                .when()
                .post(WEBHOOK_ENDPOINT)
                .then()
                .statusCode(400);

        // Then: counter should be incremented with INVALID_PAYLOAD result
        Counter counter = registry.find("sync_webhook_received_total")
                .tag("realm", "unknown")
                .tag("event_type", "unknown")
                .tag("result", "INVALID_PAYLOAD")
                .counter();

        assertNotNull(counter, "Webhook received counter for invalid payload should exist");
        assertTrue(counter.count() >= 1.0,
                "Counter should have at least one increment for invalid payload");
    }

    @Test
    @DisplayName("All webhook metrics should be exposed via /metrics endpoint")
    void testMetricsExposedViaPrometheusEndpoint() {
        // When: fetching metrics endpoint
        String metricsOutput = given()
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        // Then: all webhook metrics should be present
        assertTrue(metricsOutput.contains("sync_webhook_received_total"),
                "Metrics should include sync_webhook_received_total");
        assertTrue(metricsOutput.contains("sync_queue_backlog"),
                "Metrics should include sync_queue_backlog");
        assertTrue(metricsOutput.contains("sync_webhook_processing_duration_seconds"),
                "Metrics should include sync_webhook_processing_duration_seconds");
        assertTrue(metricsOutput.contains("sync_webhook_signature_failures_total"),
                "Metrics should include sync_webhook_signature_failures_total");
    }

    @Test
    @DisplayName("Webhook received counter should track different realms separately")
    void testWebhookReceivedCounterDifferentRealms() throws Exception {
        // Given: events from different realms
        KeycloakAdminEvent event1 = createTestEvent("realm-1", "USER", "CREATE");
        KeycloakAdminEvent event2 = createTestEvent("realm-2", "USER", "CREATE");

        String payload1 = objectMapper.writeValueAsString(event1);
        String payload2 = objectMapper.writeValueAsString(event2);

        String signature1 = computeHmac(payload1, hmacSecret);
        String signature2 = computeHmac(payload2, hmacSecret);

        // When: sending events from both realms
        given()
                .contentType(ContentType.JSON)
                .header(SIGNATURE_HEADER, signature1)
                .body(payload1)
                .when()
                .post(WEBHOOK_ENDPOINT)
                .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .header(SIGNATURE_HEADER, signature2)
                .body(payload2)
                .when()
                .post(WEBHOOK_ENDPOINT)
                .then()
                .statusCode(200);

        // Then: counters should track realms separately
        Counter realm1Counter = registry.find("sync_webhook_received_total")
                .tag("realm", "realm-1")
                .tag("event_type", "USER")
                .tag("result", "SUCCESS")
                .counter();

        Counter realm2Counter = registry.find("sync_webhook_received_total")
                .tag("realm", "realm-2")
                .tag("event_type", "USER")
                .tag("result", "SUCCESS")
                .counter();

        assertNotNull(realm1Counter, "Counter for realm-1 should exist");
        assertNotNull(realm2Counter, "Counter for realm-2 should exist");

        assertTrue(realm1Counter.count() >= 1.0,
                "realm-1 counter should have at least one increment");
        assertTrue(realm2Counter.count() >= 1.0,
                "realm-2 counter should have at least one increment");
    }

    @Test
    @DisplayName("Webhook received counter should track different event types separately")
    void testWebhookReceivedCounterDifferentEventTypes() throws Exception {
        // Given: events with different resource types
        KeycloakAdminEvent userEvent = createTestEvent("test-realm", "USER", "CREATE");
        KeycloakAdminEvent groupEvent = createTestEvent("test-realm", "GROUP", "CREATE");

        String userPayload = objectMapper.writeValueAsString(userEvent);
        String groupPayload = objectMapper.writeValueAsString(groupEvent);

        String userSignature = computeHmac(userPayload, hmacSecret);
        String groupSignature = computeHmac(groupPayload, hmacSecret);

        // When: sending both event types
        given()
                .contentType(ContentType.JSON)
                .header(SIGNATURE_HEADER, userSignature)
                .body(userPayload)
                .when()
                .post(WEBHOOK_ENDPOINT)
                .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .header(SIGNATURE_HEADER, groupSignature)
                .body(groupPayload)
                .when()
                .post(WEBHOOK_ENDPOINT)
                .then()
                .statusCode(200);

        // Then: counters should track event types separately
        Counter userCounter = registry.find("sync_webhook_received_total")
                .tag("realm", "test-realm")
                .tag("event_type", "USER")
                .tag("result", "SUCCESS")
                .counter();

        Counter groupCounter = registry.find("sync_webhook_received_total")
                .tag("realm", "test-realm")
                .tag("event_type", "GROUP")
                .tag("result", "SUCCESS")
                .counter();

        assertNotNull(userCounter, "Counter for USER events should exist");
        assertNotNull(groupCounter, "Counter for GROUP events should exist");

        assertTrue(userCounter.count() >= 1.0,
                "USER counter should have at least one increment");
        assertTrue(groupCounter.count() >= 1.0,
                "GROUP counter should have at least one increment");
    }

    // ========== Helper Methods ==========

    /**
     * Create a test Keycloak admin event.
     */
    private KeycloakAdminEvent createTestEvent(String realmId, String resourceType, String operationType) {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setRealmId(realmId);
        event.setResourceType(resourceType);
        event.setOperationType(operationType);
        event.setResourcePath("/users/test-user-123");
        event.setTime(System.currentTimeMillis());
        return event;
    }

    /**
     * Compute HMAC-SHA256 signature for a payload.
     */
    private String computeHmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }
}

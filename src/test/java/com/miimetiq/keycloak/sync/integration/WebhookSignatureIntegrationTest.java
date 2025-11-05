package com.miimetiq.keycloak.sync.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration test for webhook HMAC signature validation.
 * <p>
 * Tests signature validation with configured HMAC secret, including
 * valid signatures, invalid signatures, and missing signatures.
 */
@QuarkusTest
@QuarkusTestResource(IntegrationTestResource.class)
@TestProfile(WebhookSignatureIntegrationTest.SignatureTestProfile.class)
@DisplayName("Webhook Signature Validation Integration Tests")
class WebhookSignatureIntegrationTest {

    private static final String TEST_SECRET = "test-webhook-secret-for-integration";
    private static final String SIGNATURE_HEADER = "X-Keycloak-Signature";

    @Test
    @DisplayName("Valid signature should allow event processing")
    void testValidSignature() throws Exception {
        String payload = """
                {
                    "id": "test-event-001",
                    "time": 1699000000000,
                    "realmId": "test-realm",
                    "resourceType": "USER",
                    "operationType": "CREATE",
                    "resourcePath": "users/test-user-id"
                }
                """;

        String signature = computeHmacSha256(payload, TEST_SECRET);

        given()
                .contentType(ContentType.JSON)
                .header(SIGNATURE_HEADER, signature)
                .body(payload)
                .when()
                .post("/api/kc/events")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("correlationId", notNullValue())
                .body("message", equalTo("Event received successfully"))
                .body("resourceType", equalTo("USER"))
                .body("operationType", equalTo("CREATE"));
    }

    @Test
    @DisplayName("Invalid signature should return 401 Unauthorized")
    void testInvalidSignature() {
        String payload = """
                {
                    "id": "test-event-002",
                    "time": 1699000000000,
                    "realmId": "test-realm",
                    "resourceType": "USER",
                    "operationType": "UPDATE",
                    "resourcePath": "users/test-user-id"
                }
                """;

        String invalidSignature = "0000000000000000000000000000000000000000000000000000000000000000";

        given()
                .contentType(ContentType.JSON)
                .header(SIGNATURE_HEADER, invalidSignature)
                .body(payload)
                .when()
                .post("/api/kc/events")
                .then()
                .statusCode(401)
                .contentType(ContentType.JSON)
                .body("error", containsString("Signature validation failed"));
    }

    @Test
    @DisplayName("Missing signature header should return 401 Unauthorized")
    void testMissingSignature() {
        String payload = """
                {
                    "id": "test-event-003",
                    "time": 1699000000000,
                    "realmId": "test-realm",
                    "resourceType": "USER",
                    "operationType": "DELETE",
                    "resourcePath": "users/test-user-id"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/kc/events")
                .then()
                .statusCode(401)
                .contentType(ContentType.JSON)
                .body("error", containsString("Signature validation failed"));
    }

    @Test
    @DisplayName("Tampered payload should fail signature validation")
    void testTamperedPayload() throws Exception {
        String originalPayload = """
                {
                    "id": "test-event-004",
                    "time": 1699000000000,
                    "realmId": "test-realm",
                    "resourceType": "USER",
                    "operationType": "CREATE",
                    "resourcePath": "users/test-user-id"
                }
                """;

        // Compute signature for original payload
        String signature = computeHmacSha256(originalPayload, TEST_SECRET);

        // Tamper with the payload
        String tamperedPayload = """
                {
                    "id": "test-event-004",
                    "time": 1699000000000,
                    "realmId": "test-realm",
                    "resourceType": "USER",
                    "operationType": "DELETE",
                    "resourcePath": "users/test-user-id"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .header(SIGNATURE_HEADER, signature)
                .body(tamperedPayload)
                .when()
                .post("/api/kc/events")
                .then()
                .statusCode(401)
                .contentType(ContentType.JSON)
                .body("error", containsString("Signature validation failed"));
    }

    @Test
    @DisplayName("Signature with wrong secret should fail")
    void testWrongSecret() throws Exception {
        String payload = """
                {
                    "id": "test-event-005",
                    "time": 1699000000000,
                    "realmId": "test-realm",
                    "resourceType": "USER",
                    "operationType": "CREATE",
                    "resourcePath": "users/test-user-id"
                }
                """;

        // Compute signature with wrong secret
        String wrongSignature = computeHmacSha256(payload, "wrong-secret");

        given()
                .contentType(ContentType.JSON)
                .header(SIGNATURE_HEADER, wrongSignature)
                .body(payload)
                .when()
                .post("/api/kc/events")
                .then()
                .statusCode(401)
                .contentType(ContentType.JSON)
                .body("error", containsString("Signature validation failed"));
    }

    @Test
    @DisplayName("Empty signature header should return 401")
    void testEmptySignature() {
        String payload = """
                {
                    "id": "test-event-006",
                    "time": 1699000000000,
                    "realmId": "test-realm",
                    "resourceType": "USER",
                    "operationType": "CREATE",
                    "resourcePath": "users/test-user-id"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .header(SIGNATURE_HEADER, "")
                .body(payload)
                .when()
                .post("/api/kc/events")
                .then()
                .statusCode(401)
                .contentType(ContentType.JSON)
                .body("error", containsString("Signature validation failed"));
    }

    /**
     * Helper method to compute HMAC-SHA256 signature for testing.
     */
    private String computeHmacSha256(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hmacBytes);
    }

    /**
     * Test profile that enables HMAC signature validation.
     */
    public static class SignatureTestProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "keycloak.webhook-hmac-secret", TEST_SECRET
            );
        }
    }
}

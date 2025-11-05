package com.miimetiq.keycloak.sync.webhook;

import com.miimetiq.keycloak.sync.keycloak.KeycloakConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WebhookSignatureValidator.
 * <p>
 * Tests HMAC-SHA256 signature validation logic including success cases,
 * failure cases, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookSignatureValidator Unit Tests")
class WebhookSignatureValidatorTest {

    private WebhookSignatureValidator validator;

    @Mock
    private KeycloakConfig keycloakConfig;

    @BeforeEach
    void setUp() {
        validator = new WebhookSignatureValidator();
        validator.keycloakConfig = keycloakConfig;
    }

    private static final String TEST_SECRET = "test-webhook-secret-key";
    private static final String TEST_PAYLOAD = "{\"resourceType\":\"USER\",\"operationType\":\"CREATE\"}";

    @Test
    @DisplayName("Valid signature should pass validation")
    void testValidSignature() throws Exception {
        // Given: configured secret and valid signature
        when(keycloakConfig.webhookHmacSecret()).thenReturn(Optional.of(TEST_SECRET));
        String validSignature = computeHmacSha256(TEST_PAYLOAD, TEST_SECRET);

        // When: validating with correct signature
        WebhookSignatureValidator.ValidationResult result = validator.validate(TEST_PAYLOAD, validSignature);

        // Then: validation succeeds
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("Invalid signature should fail validation")
    void testInvalidSignature() {
        // Given: configured secret and invalid signature
        when(keycloakConfig.webhookHmacSecret()).thenReturn(Optional.of(TEST_SECRET));
        String invalidSignature = "0000000000000000000000000000000000000000000000000000000000000000";

        // When: validating with incorrect signature
        WebhookSignatureValidator.ValidationResult result = validator.validate(TEST_PAYLOAD, invalidSignature);

        // Then: validation fails
        assertFalse(result.isValid());
        assertEquals("Invalid signature", result.getErrorMessage());
    }

    @Test
    @DisplayName("Missing signature header should fail validation")
    void testMissingSignature() {
        // Given: configured secret but no signature
        when(keycloakConfig.webhookHmacSecret()).thenReturn(Optional.of(TEST_SECRET));

        // When: validating with null signature
        WebhookSignatureValidator.ValidationResult result = validator.validate(TEST_PAYLOAD, null);

        // Then: validation fails
        assertFalse(result.isValid());
        assertEquals("Missing signature header", result.getErrorMessage());
    }

    @Test
    @DisplayName("Blank signature should fail validation")
    void testBlankSignature() {
        // Given: configured secret but blank signature
        when(keycloakConfig.webhookHmacSecret()).thenReturn(Optional.of(TEST_SECRET));

        // When: validating with blank signature
        WebhookSignatureValidator.ValidationResult result = validator.validate(TEST_PAYLOAD, "   ");

        // Then: validation fails
        assertFalse(result.isValid());
        assertEquals("Missing signature header", result.getErrorMessage());
    }

    @Test
    @DisplayName("Null payload should fail validation")
    void testNullPayload() {
        // Given: configured secret and valid signature
        when(keycloakConfig.webhookHmacSecret()).thenReturn(Optional.of(TEST_SECRET));
        String signature = "some-signature";

        // When: validating null payload
        WebhookSignatureValidator.ValidationResult result = validator.validate(null, signature);

        // Then: validation fails
        assertFalse(result.isValid());
        assertEquals("Payload is required", result.getErrorMessage());
    }

    @Test
    @DisplayName("Unconfigured secret should allow request through")
    void testUnconfiguredSecret() {
        // Given: no configured secret
        when(keycloakConfig.webhookHmacSecret()).thenReturn(Optional.empty());

        // When: validating without configured secret
        WebhookSignatureValidator.ValidationResult result = validator.validate(TEST_PAYLOAD, null);

        // Then: validation succeeds (signature checking disabled)
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("Different payloads should produce different signatures")
    void testDifferentPayloads() throws Exception {
        // Given: configured secret
        when(keycloakConfig.webhookHmacSecret()).thenReturn(Optional.of(TEST_SECRET));

        String payload1 = "{\"resourceType\":\"USER\",\"operationType\":\"CREATE\"}";
        String payload2 = "{\"resourceType\":\"USER\",\"operationType\":\"UPDATE\"}";

        String signature1 = computeHmacSha256(payload1, TEST_SECRET);
        String signature2 = computeHmacSha256(payload2, TEST_SECRET);

        // When: validating each payload with its own signature
        WebhookSignatureValidator.ValidationResult result1 = validator.validate(payload1, signature1);
        WebhookSignatureValidator.ValidationResult result2 = validator.validate(payload2, signature2);

        // Then: both should succeed
        assertTrue(result1.isValid());
        assertTrue(result2.isValid());

        // And: signatures should be different
        assertNotEquals(signature1, signature2);
    }

    @Test
    @DisplayName("Cross-payload signature should fail")
    void testCrossPayloadSignature() throws Exception {
        // Given: configured secret and two payloads
        when(keycloakConfig.webhookHmacSecret()).thenReturn(Optional.of(TEST_SECRET));

        String payload1 = "{\"resourceType\":\"USER\",\"operationType\":\"CREATE\"}";
        String payload2 = "{\"resourceType\":\"USER\",\"operationType\":\"UPDATE\"}";

        String signature1 = computeHmacSha256(payload1, TEST_SECRET);

        // When: validating payload2 with signature1
        WebhookSignatureValidator.ValidationResult result = validator.validate(payload2, signature1);

        // Then: validation should fail
        assertFalse(result.isValid());
        assertEquals("Invalid signature", result.getErrorMessage());
    }

    @Test
    @DisplayName("Different secrets should produce different signatures")
    void testDifferentSecrets() throws Exception {
        // Given: two different secrets
        String secret1 = "secret-one";
        String secret2 = "secret-two";

        String signature1 = computeHmacSha256(TEST_PAYLOAD, secret1);
        String signature2 = computeHmacSha256(TEST_PAYLOAD, secret2);

        // Then: signatures should be different
        assertNotEquals(signature1, signature2);

        // And: each should only validate with its own secret
        when(keycloakConfig.webhookHmacSecret()).thenReturn(Optional.of(secret1));
        WebhookSignatureValidator.ValidationResult result1 = validator.validate(TEST_PAYLOAD, signature1);
        assertTrue(result1.isValid());

        WebhookSignatureValidator.ValidationResult result2 = validator.validate(TEST_PAYLOAD, signature2);
        assertFalse(result2.isValid());
    }

    @Test
    @DisplayName("Case sensitivity in signature should matter")
    void testCaseSensitivity() throws Exception {
        // Given: configured secret and valid signature
        when(keycloakConfig.webhookHmacSecret()).thenReturn(Optional.of(TEST_SECRET));
        String validSignature = computeHmacSha256(TEST_PAYLOAD, TEST_SECRET);

        // When: validating with uppercased signature
        String uppercasedSignature = validSignature.toUpperCase();
        WebhookSignatureValidator.ValidationResult result = validator.validate(TEST_PAYLOAD, uppercasedSignature);

        // Then: validation should fail (hex is lowercase)
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("Empty string payload should produce valid signature")
    void testEmptyPayload() throws Exception {
        // Given: configured secret and empty payload
        when(keycloakConfig.webhookHmacSecret()).thenReturn(Optional.of(TEST_SECRET));
        String emptyPayload = "";
        String signature = computeHmacSha256(emptyPayload, TEST_SECRET);

        // When: validating empty payload
        WebhookSignatureValidator.ValidationResult result = validator.validate(emptyPayload, signature);

        // Then: validation should succeed
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("ValidationResult should have proper factory methods")
    void testValidationResult() {
        // Test success result
        WebhookSignatureValidator.ValidationResult success =
                WebhookSignatureValidator.ValidationResult.success();
        assertTrue(success.isValid());
        assertNull(success.getErrorMessage());

        // Test failure result
        WebhookSignatureValidator.ValidationResult failure =
                WebhookSignatureValidator.ValidationResult.failure("Test error");
        assertFalse(failure.isValid());
        assertEquals("Test error", failure.getErrorMessage());
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
}

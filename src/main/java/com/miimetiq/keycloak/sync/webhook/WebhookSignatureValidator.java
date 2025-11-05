package com.miimetiq.keycloak.sync.webhook;

import com.miimetiq.keycloak.sync.keycloak.KeycloakConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Service for validating HMAC signatures on webhook payloads.
 * <p>
 * Validates that incoming webhook events are authentic by verifying the
 * HMAC-SHA256 signature using the configured secret key. This prevents
 * unauthorized event injection attacks.
 */
@ApplicationScoped
public class WebhookSignatureValidator {

    private static final Logger LOG = Logger.getLogger(WebhookSignatureValidator.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Inject
    KeycloakConfig keycloakConfig;

    /**
     * Validates the HMAC signature for a webhook payload.
     * <p>
     * Computes the expected HMAC-SHA256 signature for the payload using the
     * configured secret, then performs a timing-safe comparison with the
     * provided signature.
     *
     * @param payload   the raw JSON payload as string
     * @param signature the signature to validate (typically from X-Keycloak-Signature header)
     * @return validation result with success status and error message if failed
     */
    public ValidationResult validate(String payload, String signature) {
        // Check if webhook HMAC validation is configured
        if (keycloakConfig.webhookHmacSecret().isEmpty()) {
            LOG.warn("Webhook HMAC secret is not configured - signature validation disabled");
            return ValidationResult.success();
        }

        // Check if signature is provided
        if (signature == null || signature.isBlank()) {
            LOG.warn("Missing webhook signature header");
            return ValidationResult.failure("Missing signature header");
        }

        // Check if payload is provided
        if (payload == null) {
            LOG.warn("Cannot validate signature for null payload");
            return ValidationResult.failure("Payload is required");
        }

        try {
            // Get the configured secret
            String secret = keycloakConfig.webhookHmacSecret().get();

            // Compute expected signature
            String expectedSignature = computeHmacSha256(payload, secret);

            // Timing-safe comparison to prevent timing attacks
            if (timingSafeEquals(expectedSignature, signature)) {
                LOG.debug("Webhook signature validation successful");
                return ValidationResult.success();
            } else {
                LOG.warnf("Invalid webhook signature: expected=%s, received=%s",
                        maskSignature(expectedSignature), maskSignature(signature));
                return ValidationResult.failure("Invalid signature");
            }

        } catch (Exception e) {
            LOG.errorf(e, "Failed to validate webhook signature: %s", e.getMessage());
            return ValidationResult.failure("Signature validation error: " + e.getMessage());
        }
    }

    /**
     * Computes HMAC-SHA256 signature for the given payload using the secret key.
     *
     * @param payload the payload to sign
     * @param secret  the secret key
     * @return hex-encoded HMAC-SHA256 signature
     */
    private String computeHmacSha256(String payload, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {

        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );
        mac.init(keySpec);

        byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hmacBytes);
    }

    /**
     * Performs timing-safe string comparison to prevent timing attacks.
     * <p>
     * Uses MessageDigest.isEqual() which performs constant-time comparison.
     *
     * @param expected the expected signature
     * @param actual   the actual signature
     * @return true if signatures match, false otherwise
     */
    private boolean timingSafeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }

        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    /**
     * Masks a signature for logging purposes.
     * Shows only first and last 4 characters.
     *
     * @param signature the signature to mask
     * @return masked signature
     */
    private String maskSignature(String signature) {
        if (signature == null || signature.length() < 8) {
            return "***";
        }
        return signature.substring(0, 4) + "..." + signature.substring(signature.length() - 4);
    }

    /**
     * Result of signature validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}

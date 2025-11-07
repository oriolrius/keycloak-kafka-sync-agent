package com.miimetiq.keycloak.sync.webhook;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST endpoint for receiving password reset events from Keycloak Password Sync SPI.
 * <p>
 * This endpoint receives plain-text passwords BEFORE Keycloak hashes them,
 * allowing the sync-agent to create matching SCRAM credentials in Kafka.
 * <p>
 * Security Note: This endpoint is for TESTING ONLY in development environments.
 * In production, this pattern should be replaced with secure secret management.
 * <p>
 * Endpoints:
 * - POST /api/webhook/password: receive password reset events from Keycloak SPI
 */
@Path("/api/webhook/password")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PasswordWebhookResource {

    private static final Logger LOG = Logger.getLogger(PasswordWebhookResource.class);

    // Store passwords temporarily (in-memory cache)
    // Key: username, Value: plain-text password
    // TODO: In production, use secure secret management (HashiCorp Vault, AWS Secrets Manager, etc.)
    private static final Map<String, String> PASSWORD_CACHE = new ConcurrentHashMap<>();

    /**
     * Receive a password reset event from Keycloak Password Sync SPI.
     * <p>
     * The Keycloak SPI intercepts password reset operations and sends the
     * plain-text password to this endpoint BEFORE Keycloak hashes it.
     * <p>
     * The password is stored temporarily in memory until the next reconciliation
     * cycle uses it to create matching Kafka SCRAM credentials.
     * <p>
     * Returns 200 OK if the password is successfully stored.
     * Returns 400 Bad Request if the payload is malformed.
     *
     * @param event the password event from Keycloak SPI
     * @return response indicating success or failure
     */
    @POST
    public Response receivePassword(PasswordEvent event) {
        try {
            // Validate payload
            if (event == null) {
                LOG.warn("Received null password event");
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Password event is required"))
                        .build();
            }

            // Validate required fields
            if (event.username == null || event.username.isBlank()) {
                LOG.warnf("Received password event with missing username: %s", event);
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Username is required"))
                        .build();
            }

            if (event.password == null || event.password.isBlank()) {
                LOG.warnf("Received password event with missing password for user: %s", event.username);
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Password is required"))
                        .build();
            }

            // Log received event (don't log the actual password!)
            LOG.infof("Received password event for user: %s (realmId: %s, userId: %s)",
                    event.username, event.realmId, event.userId);

            // Store password in cache for reconciliation
            PASSWORD_CACHE.put(event.username, event.password);
            LOG.debugf("Stored password in cache for user: %s (cache size: %d)",
                    event.username, PASSWORD_CACHE.size());

            // Return success
            return Response
                    .ok(new SuccessResponse("Password received successfully", event.username))
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "Failed to process password webhook: %s", e.getMessage());
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to process password event: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Retrieve and remove a password from the cache.
     * <p>
     * This method is called by ReconciliationService during sync operations.
     * The password is returned once and then removed from the cache.
     * <p>
     * Thread-safe: Uses ConcurrentHashMap.remove() for atomic get-and-remove.
     *
     * @param username the username to retrieve password for
     * @return the plain-text password, or null if not found
     */
    public static String getPasswordForUser(String username) {
        String password = PASSWORD_CACHE.remove(username);
        if (password != null) {
            LOG.debugf("Retrieved and removed password from cache for user: %s (cache size: %d)",
                    username, PASSWORD_CACHE.size());
        } else {
            LOG.debugf("No password found in cache for user: %s", username);
        }
        return password;
    }

    /**
     * Clear all passwords from the cache.
     * <p>
     * Useful for testing or periodic cleanup.
     */
    public static void clearPasswordCache() {
        int size = PASSWORD_CACHE.size();
        PASSWORD_CACHE.clear();
        LOG.infof("Cleared password cache (%d entries removed)", size);
    }

    /**
     * Get current cache size (for monitoring/testing).
     */
    public static int getCacheSize() {
        return PASSWORD_CACHE.size();
    }

    /**
     * Request DTO for password events from Keycloak SPI.
     */
    public static class PasswordEvent {
        public String realmId;
        public String username;
        public String userId;
        public String password;

        public PasswordEvent() {
        }

        public PasswordEvent(String realmId, String username, String userId, String password) {
            this.realmId = realmId;
            this.username = username;
            this.userId = userId;
            this.password = password;
        }

        @Override
        public String toString() {
            // Don't log password!
            return String.format("PasswordEvent{realmId='%s', username='%s', userId='%s', password=***}",
                    realmId, username, userId);
        }
    }

    /**
     * Response DTO for successful password receipt.
     */
    public static class SuccessResponse {
        public String message;
        public String username;

        public SuccessResponse() {
        }

        public SuccessResponse(String message, String username) {
            this.message = message;
            this.username = username;
        }
    }

    /**
     * Response DTO for errors.
     */
    public static class ErrorResponse {
        public String error;

        public ErrorResponse() {
        }

        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}

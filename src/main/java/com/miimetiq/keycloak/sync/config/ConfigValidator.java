package com.miimetiq.keycloak.sync.config;

import com.miimetiq.keycloak.sync.kafka.KafkaConfig;
import com.miimetiq.keycloak.sync.keycloak.KeycloakConfig;
import com.miimetiq.keycloak.sync.reconcile.ReconcileConfig;
import com.miimetiq.keycloak.sync.retention.RetentionConfig;
import com.miimetiq.keycloak.sync.server.ServerConfig;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates all configuration at application startup.
 * Fails fast with clear error messages if any required configuration is missing or invalid.
 */
@ApplicationScoped
public class ConfigValidator {

    private static final Logger LOG = Logger.getLogger(ConfigValidator.class);

    // Pattern for basic auth format: username:password
    private static final Pattern BASIC_AUTH_PATTERN = Pattern.compile("^[^:]+:.+$");

    // Valid Kafka security protocols
    private static final List<String> VALID_SECURITY_PROTOCOLS = List.of(
        "PLAINTEXT", "SSL", "SASL_SSL", "SASL_PLAINTEXT"
    );

    // Valid SASL mechanisms
    private static final List<String> VALID_SASL_MECHANISMS = List.of(
        "PLAIN", "SCRAM-SHA-256", "SCRAM-SHA-512", "GSSAPI"
    );

    @Inject
    KafkaConfig kafkaConfig;

    @Inject
    KeycloakConfig keycloakConfig;

    @Inject
    ReconcileConfig reconcileConfig;

    @Inject
    RetentionConfig retentionConfig;

    @Inject
    ServerConfig serverConfig;

    void onStart(@Observes StartupEvent event) {
        LOG.info("Validating application configuration...");

        List<String> errors = new ArrayList<>();

        // Validate Kafka configuration
        validateKafkaConfig(errors);

        // Validate Keycloak configuration
        validateKeycloakConfig(errors);

        // Validate Reconcile configuration
        validateReconcileConfig(errors);

        // Validate Retention configuration
        validateRetentionConfig(errors);

        // Validate Server configuration
        validateServerConfig(errors);

        if (!errors.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("Configuration validation failed with ")
                       .append(errors.size())
                       .append(" error(s):\n");
            for (int i = 0; i < errors.size(); i++) {
                errorMessage.append("  ").append(i + 1).append(". ").append(errors.get(i)).append("\n");
            }
            LOG.error(errorMessage.toString());
            throw new IllegalStateException(errorMessage.toString());
        }

        LOG.info("Configuration validation completed successfully");
    }

    private void validateKafkaConfig(List<String> errors) {
        // Validate bootstrap servers
        if (kafkaConfig.bootstrapServers() == null || kafkaConfig.bootstrapServers().trim().isEmpty()) {
            errors.add("KAFKA_BOOTSTRAP_SERVERS is required");
        }

        // Validate security protocol
        if (!VALID_SECURITY_PROTOCOLS.contains(kafkaConfig.securityProtocol())) {
            errors.add("KAFKA_SECURITY_PROTOCOL must be one of: " + String.join(", ", VALID_SECURITY_PROTOCOLS));
        }

        // Validate SASL configuration if SASL is used
        if (kafkaConfig.securityProtocol().startsWith("SASL")) {
            if (kafkaConfig.saslMechanism().isEmpty()) {
                errors.add("KAFKA_SASL_MECHANISM is required when using SASL security protocol");
            } else if (!VALID_SASL_MECHANISMS.contains(kafkaConfig.saslMechanism().get())) {
                errors.add("KAFKA_SASL_MECHANISM must be one of: " + String.join(", ", VALID_SASL_MECHANISMS));
            }

            if (kafkaConfig.saslJaas().isEmpty()) {
                errors.add("KAFKA_SASL_JAAS is required when using SASL security protocol");
            }
        }

        // Validate timeouts
        if (kafkaConfig.requestTimeoutMs() <= 0) {
            errors.add("kafka.request-timeout-ms must be positive");
        }

        if (kafkaConfig.connectionTimeoutMs() <= 0) {
            errors.add("kafka.connection-timeout-ms must be positive");
        }
    }

    private void validateKeycloakConfig(List<String> errors) {
        // Validate URL
        try {
            new URL(keycloakConfig.url());
        } catch (MalformedURLException e) {
            errors.add("KC_BASE_URL must be a valid URL: " + e.getMessage());
        }

        // Validate realm
        if (keycloakConfig.realm() == null || keycloakConfig.realm().trim().isEmpty()) {
            errors.add("KC_REALM is required");
        }

        // Validate client ID
        if (keycloakConfig.clientId() == null || keycloakConfig.clientId().trim().isEmpty()) {
            errors.add("KC_CLIENT_ID is required");
        }

        // Validate authentication: either client credentials or admin username/password
        boolean hasClientSecret = keycloakConfig.clientSecret().isPresent() &&
                                 !keycloakConfig.clientSecret().get().trim().isEmpty();
        boolean hasAdminCreds = keycloakConfig.adminUsername().isPresent() &&
                               keycloakConfig.adminPassword().isPresent() &&
                               !keycloakConfig.adminUsername().get().trim().isEmpty() &&
                               !keycloakConfig.adminPassword().get().trim().isEmpty();

        if (!hasClientSecret && !hasAdminCreds) {
            errors.add("Either KC_CLIENT_SECRET or both KC_ADMIN_USERNAME and KC_ADMIN_PASSWORD must be provided");
        }

        // Validate timeouts
        if (keycloakConfig.connectionTimeoutMs() <= 0) {
            errors.add("keycloak.connection-timeout-ms must be positive");
        }

        if (keycloakConfig.readTimeoutMs() <= 0) {
            errors.add("keycloak.read-timeout-ms must be positive");
        }
    }

    private void validateReconcileConfig(List<String> errors) {
        // Validate interval
        if (reconcileConfig.intervalSeconds() <= 0) {
            errors.add("RECONCILE_INTERVAL_SECONDS must be positive");
        }

        // Validate page size
        if (reconcileConfig.pageSize() <= 0) {
            errors.add("RECONCILE_PAGE_SIZE must be positive");
        }

        if (reconcileConfig.pageSize() > 10000) {
            errors.add("RECONCILE_PAGE_SIZE should not exceed 10000 for performance reasons");
        }
    }

    private void validateRetentionConfig(List<String> errors) {
        // Validate max bytes
        if (retentionConfig.maxBytes() <= 0) {
            errors.add("RETENTION_MAX_BYTES must be positive");
        }

        // Warn if max bytes is very small (less than 1 MB)
        if (retentionConfig.maxBytes() < 1048576) {
            LOG.warn("RETENTION_MAX_BYTES is set to less than 1 MB, which may cause frequent purges");
        }

        // Validate max age
        if (retentionConfig.maxAgeDays() <= 0) {
            errors.add("RETENTION_MAX_AGE_DAYS must be positive");
        }

        // Validate purge interval
        if (retentionConfig.purgeIntervalSeconds() <= 0) {
            errors.add("RETENTION_PURGE_INTERVAL_SECONDS must be positive");
        }
    }

    private void validateServerConfig(List<String> errors) {
        // Validate port
        if (serverConfig.port() <= 0 || serverConfig.port() > 65535) {
            errors.add("SERVER_PORT must be between 1 and 65535");
        }

        // Validate basic auth format if provided
        if (serverConfig.dashboardBasicAuth().isPresent()) {
            String basicAuth = serverConfig.dashboardBasicAuth().get();
            if (!BASIC_AUTH_PATTERN.matcher(basicAuth).matches()) {
                errors.add("DASHBOARD_BASIC_AUTH must be in format 'username:password'");
            }
        } else {
            LOG.warn("DASHBOARD_BASIC_AUTH is not set - dashboard will be accessible without authentication");
        }
    }
}

package com.miimetiq.keycloak.spi;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating and managing Kafka AdminClient instances.
 * Reads configuration from environment variables to support various deployment scenarios.
 *
 * <p>Supported environment variables:
 * <ul>
 *   <li>KAFKA_BOOTSTRAP_SERVERS - Comma-separated list of Kafka brokers (default: localhost:9092)</li>
 *   <li>KAFKA_SECURITY_PROTOCOL - Security protocol: PLAINTEXT, SSL, SASL_SSL, SASL_PLAINTEXT (default: PLAINTEXT)</li>
 *   <li>KAFKA_SASL_MECHANISM - SASL mechanism: PLAIN, SCRAM-SHA-256, SCRAM-SHA-512, GSSAPI</li>
 *   <li>KAFKA_SASL_JAAS_CONFIG - SASL JAAS configuration string</li>
 *   <li>KAFKA_SSL_TRUSTSTORE_LOCATION - Path to SSL truststore file</li>
 *   <li>KAFKA_SSL_TRUSTSTORE_PASSWORD - Password for SSL truststore</li>
 *   <li>KAFKA_SSL_KEYSTORE_LOCATION - Path to SSL keystore file</li>
 *   <li>KAFKA_SSL_KEYSTORE_PASSWORD - Password for SSL keystore</li>
 *   <li>KAFKA_SSL_KEY_PASSWORD - Password for SSL key</li>
 *   <li>KAFKA_REQUEST_TIMEOUT_MS - Request timeout in milliseconds (default: 30000)</li>
 *   <li>KAFKA_DEFAULT_API_TIMEOUT_MS - Default API timeout in milliseconds (default: 60000)</li>
 * </ul>
 */
public class KafkaAdminClientFactory {

    private static final Logger LOG = Logger.getLogger(KafkaAdminClientFactory.class);

    private static volatile AdminClient adminClient;
    private static volatile boolean initialized = false;

    /**
     * Private constructor to prevent instantiation.
     */
    private KafkaAdminClientFactory() {
    }

    /**
     * Gets or creates a singleton AdminClient instance configured from environment variables.
     *
     * @return AdminClient instance
     * @throws RuntimeException if client creation fails
     */
    public static synchronized AdminClient getAdminClient() {
        if (!initialized || adminClient == null) {
            adminClient = createAdminClient();
            initialized = true;
        }
        return adminClient;
    }

    /**
     * Creates a new AdminClient configured from environment variables.
     *
     * @return AdminClient instance
     * @throws RuntimeException if client creation fails
     */
    private static AdminClient createAdminClient() {
        LOG.info("Creating Kafka AdminClient from environment variables");

        Map<String, Object> configs = new HashMap<>();

        // Basic configuration
        String bootstrapServers = getEnvOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        LOG.info("Kafka bootstrap servers: " + bootstrapServers);

        String requestTimeoutMs = getEnvOrDefault("KAFKA_REQUEST_TIMEOUT_MS", "30000");
        configs.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, Integer.parseInt(requestTimeoutMs));

        String defaultApiTimeoutMs = getEnvOrDefault("KAFKA_DEFAULT_API_TIMEOUT_MS", "60000");
        configs.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, Integer.parseInt(defaultApiTimeoutMs));

        // Security protocol
        String securityProtocol = getEnvOrDefault("KAFKA_SECURITY_PROTOCOL", "PLAINTEXT");
        configs.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, securityProtocol);
        LOG.info("Kafka security protocol: " + securityProtocol);

        // SASL configuration
        if (securityProtocol.contains("SASL")) {
            String saslMechanism = System.getenv("KAFKA_SASL_MECHANISM");
            if (saslMechanism != null && !saslMechanism.isEmpty()) {
                configs.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
                LOG.info("Kafka SASL mechanism: " + saslMechanism);
            }

            String saslJaas = System.getenv("KAFKA_SASL_JAAS_CONFIG");
            if (saslJaas != null && !saslJaas.isEmpty()) {
                configs.put(SaslConfigs.SASL_JAAS_CONFIG, saslJaas);
                LOG.info("Kafka SASL JAAS configuration provided");
            }
        }

        // SSL configuration
        if (securityProtocol.contains("SSL")) {
            String truststoreLocation = System.getenv("KAFKA_SSL_TRUSTSTORE_LOCATION");
            if (truststoreLocation != null && !truststoreLocation.isEmpty()) {
                configs.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststoreLocation);
                LOG.info("Kafka SSL truststore location: " + truststoreLocation);
            }

            String truststorePassword = System.getenv("KAFKA_SSL_TRUSTSTORE_PASSWORD");
            if (truststorePassword != null && !truststorePassword.isEmpty()) {
                configs.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword);
                LOG.debug("Kafka SSL truststore password provided");
            }

            String keystoreLocation = System.getenv("KAFKA_SSL_KEYSTORE_LOCATION");
            if (keystoreLocation != null && !keystoreLocation.isEmpty()) {
                configs.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keystoreLocation);
                LOG.info("Kafka SSL keystore location: " + keystoreLocation);
            }

            String keystorePassword = System.getenv("KAFKA_SSL_KEYSTORE_PASSWORD");
            if (keystorePassword != null && !keystorePassword.isEmpty()) {
                configs.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, keystorePassword);
                LOG.debug("Kafka SSL keystore password provided");
            }

            String keyPassword = System.getenv("KAFKA_SSL_KEY_PASSWORD");
            if (keyPassword != null && !keyPassword.isEmpty()) {
                configs.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, keyPassword);
                LOG.debug("Kafka SSL key password provided");
            }

            // Disable endpoint identification for dev/testing with self-signed certs
            configs.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "");
            LOG.debug("Kafka SSL endpoint identification disabled for dev/testing");
        }

        try {
            AdminClient client = AdminClient.create(configs);
            LOG.info("Kafka AdminClient created successfully");
            return client;
        } catch (Exception e) {
            LOG.error("Failed to create Kafka AdminClient", e);
            throw new RuntimeException("Failed to create Kafka AdminClient: " + e.getMessage(), e);
        }
    }

    /**
     * Closes the AdminClient if it has been initialized.
     * This should be called during SPI shutdown.
     */
    public static synchronized void close() {
        if (adminClient != null) {
            LOG.info("Closing Kafka AdminClient");
            try {
                adminClient.close();
                LOG.info("Kafka AdminClient closed successfully");
            } catch (Exception e) {
                LOG.error("Error closing Kafka AdminClient", e);
            } finally {
                adminClient = null;
                initialized = false;
            }
        }
    }

    /**
     * Gets an environment variable value or returns a default value if not set.
     *
     * @param envVar environment variable name
     * @param defaultValue default value if environment variable is not set
     * @return environment variable value or default value
     */
    private static String getEnvOrDefault(String envVar, String defaultValue) {
        String value = System.getenv(envVar);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}

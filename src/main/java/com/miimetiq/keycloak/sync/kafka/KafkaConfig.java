package com.miimetiq.keycloak.sync.kafka;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Kafka configuration properties.
 * Reads from application.properties and environment variables.
 */
@ConfigMapping(prefix = "kafka")
public interface KafkaConfig {

    /**
     * Kafka bootstrap servers (comma-separated list).
     * Can be overridden with KAFKA_BOOTSTRAP_SERVERS environment variable.
     */
    @WithDefault("localhost:9092")
    String bootstrapServers();

    /**
     * Security protocol: PLAINTEXT, SSL, SASL_SSL, SASL_PLAINTEXT.
     * Can be overridden with KAFKA_SECURITY_PROTOCOL environment variable.
     */
    @WithDefault("PLAINTEXT")
    String securityProtocol();

    /**
     * SASL mechanism: PLAIN, SCRAM-SHA-256, SCRAM-SHA-512, GSSAPI.
     * Can be overridden with KAFKA_SASL_MECHANISM environment variable.
     */
    Optional<String> saslMechanism();

    /**
     * SASL JAAS configuration.
     * Can be overridden with KAFKA_SASL_JAAS environment variable.
     */
    Optional<String> saslJaas();

    /**
     * SSL truststore location.
     * Can be overridden with KAFKA_SSL_TRUSTSTORE_LOCATION environment variable.
     */
    Optional<String> sslTruststoreLocation();

    /**
     * SSL truststore password.
     * Can be overridden with KAFKA_SSL_TRUSTSTORE_PASSWORD environment variable.
     */
    Optional<String> sslTruststorePassword();

    /**
     * SSL keystore location.
     * Can be overridden with KAFKA_SSL_KEYSTORE_LOCATION environment variable.
     */
    Optional<String> sslKeystoreLocation();

    /**
     * SSL keystore password.
     * Can be overridden with KAFKA_SSL_KEYSTORE_PASSWORD environment variable.
     */
    Optional<String> sslKeystorePassword();

    /**
     * SSL key password.
     * Can be overridden with KAFKA_SSL_KEY_PASSWORD environment variable.
     */
    Optional<String> sslKeyPassword();

    /**
     * Request timeout in milliseconds.
     */
    @WithDefault("30000")
    int requestTimeoutMs();

    /**
     * Connection timeout in milliseconds.
     */
    @WithDefault("10000")
    int connectionTimeoutMs();
}

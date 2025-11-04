package com.miimetiq.keycloak.sync.kafka;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Produces Kafka AdminClient bean configured from application properties and environment variables.
 */
@ApplicationScoped
public class KafkaAdminClientProducer {

    private static final Logger LOG = Logger.getLogger(KafkaAdminClientProducer.class);

    @Inject
    KafkaConfig kafkaConfig;

    @Produces
    @ApplicationScoped
    public AdminClient produceAdminClient() {
        LOG.info("Creating Kafka AdminClient with configuration");

        Map<String, Object> configs = new HashMap<>();

        // Basic configuration
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.bootstrapServers());
        configs.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, kafkaConfig.requestTimeoutMs());
        configs.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, kafkaConfig.connectionTimeoutMs());

        // Security protocol
        String securityProtocol = kafkaConfig.securityProtocol();
        configs.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, securityProtocol);
        LOG.info("Kafka security protocol: " + securityProtocol);

        // SASL configuration
        if (securityProtocol.contains("SASL")) {
            kafkaConfig.saslMechanism().ifPresent(mechanism -> {
                configs.put(SaslConfigs.SASL_MECHANISM, mechanism);
                LOG.info("Kafka SASL mechanism: " + mechanism);
            });

            kafkaConfig.saslJaas().ifPresent(jaas -> {
                configs.put(SaslConfigs.SASL_JAAS_CONFIG, jaas);
                LOG.info("Kafka SASL JAAS configuration provided");
            });
        }

        // SSL configuration
        if (securityProtocol.contains("SSL")) {
            kafkaConfig.sslTruststoreLocation().ifPresent(location -> {
                configs.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, location);
                LOG.info("Kafka SSL truststore location: " + location);
            });

            kafkaConfig.sslTruststorePassword().ifPresent(password -> {
                configs.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, password);
                LOG.debug("Kafka SSL truststore password provided");
            });

            kafkaConfig.sslKeystoreLocation().ifPresent(location -> {
                configs.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, location);
                LOG.info("Kafka SSL keystore location: " + location);
            });

            kafkaConfig.sslKeystorePassword().ifPresent(password -> {
                configs.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, password);
                LOG.debug("Kafka SSL keystore password provided");
            });

            kafkaConfig.sslKeyPassword().ifPresent(password -> {
                configs.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, password);
                LOG.debug("Kafka SSL key password provided");
            });

            // Disable endpoint identification for dev/testing with self-signed certs
            configs.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "");
            LOG.debug("Kafka SSL endpoint identification disabled for dev/testing");
        }

        try {
            AdminClient adminClient = AdminClient.create(configs);
            LOG.info("Kafka AdminClient created successfully");
            LOG.info("Bootstrap servers: " + kafkaConfig.bootstrapServers());
            return adminClient;
        } catch (Exception e) {
            LOG.error("Failed to create Kafka AdminClient", e);
            throw new RuntimeException("Failed to create Kafka AdminClient: " + e.getMessage(), e);
        }
    }

    public void closeAdminClient(@Disposes AdminClient adminClient) {
        if (adminClient != null) {
            LOG.info("Closing Kafka AdminClient");
            try {
                adminClient.close();
                LOG.info("Kafka AdminClient closed successfully");
            } catch (Exception e) {
                LOG.error("Error closing Kafka AdminClient", e);
            }
        }
    }
}

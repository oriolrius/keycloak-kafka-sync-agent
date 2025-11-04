package com.miimetiq.keycloak.sync.kafka;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.jboss.logging.Logger;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Validates Kafka connectivity on application startup.
 */
@ApplicationScoped
public class KafkaConnectivityValidator {

    private static final Logger LOG = Logger.getLogger(KafkaConnectivityValidator.class);

    @Inject
    AdminClient adminClient;

    @Inject
    KafkaConfig kafkaConfig;

    void validateOnStartup(@Observes StartupEvent event) {
        LOG.info("Validating Kafka connectivity on startup...");

        try {
            // Try to list topics as a connectivity test
            ListTopicsOptions options = new ListTopicsOptions();
            options.timeoutMs(kafkaConfig.connectionTimeoutMs());

            Set<String> topics = adminClient.listTopics(options)
                    .names()
                    .get(kafkaConfig.connectionTimeoutMs(), TimeUnit.MILLISECONDS);

            LOG.info("✓ Kafka connectivity validated successfully");
            LOG.info("Connected to Kafka at: " + kafkaConfig.bootstrapServers());
            LOG.info("Security protocol: " + kafkaConfig.securityProtocol());
            LOG.info("Available topics: " + topics.size());

            if (!topics.isEmpty()) {
                LOG.debug("Topics: " + topics);
            } else {
                LOG.info("No topics found (cluster is empty)");
            }

        } catch (Exception e) {
            LOG.error("✗ Failed to validate Kafka connectivity", e);
            LOG.error("Bootstrap servers: " + kafkaConfig.bootstrapServers());
            LOG.error("Security protocol: " + kafkaConfig.securityProtocol());
            LOG.error("Error type: " + e.getClass().getSimpleName());
            LOG.error("Error message: " + e.getMessage());

            if (e.getCause() != null) {
                LOG.error("Caused by: " + e.getCause().getClass().getSimpleName() + " - " + e.getCause().getMessage());
            }

            LOG.warn("Application will continue but Kafka operations may fail");
            LOG.warn("Check Kafka configuration and ensure Kafka broker is accessible");
        }
    }
}

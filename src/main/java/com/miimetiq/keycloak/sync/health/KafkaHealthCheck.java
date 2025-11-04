package com.miimetiq.keycloak.sync.health;

import com.miimetiq.keycloak.sync.kafka.KafkaConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.AdminClient;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import java.util.concurrent.TimeUnit;

@Readiness
@ApplicationScoped
public class KafkaHealthCheck implements HealthCheck {

    private static final Logger LOG = Logger.getLogger(KafkaHealthCheck.class);

    @Inject
    AdminClient adminClient;

    @Inject
    KafkaConfig kafkaConfig;

    @Override
    public HealthCheckResponse call() {
        try {
            // Try to list topics with a timeout to verify connectivity
            adminClient.listTopics().names().get(5, TimeUnit.SECONDS);

            LOG.debug("Kafka health check passed");
            return HealthCheckResponse
                    .named("kafka")
                    .up()
                    .withData("bootstrap.servers", kafkaConfig.bootstrapServers())
                    .withData("security.protocol", kafkaConfig.securityProtocol())
                    .build();
        } catch (Exception e) {
            LOG.error("Kafka health check failed", e);
            return HealthCheckResponse
                    .named("kafka")
                    .down()
                    .withData("bootstrap.servers", kafkaConfig.bootstrapServers())
                    .withData("security.protocol", kafkaConfig.securityProtocol())
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}

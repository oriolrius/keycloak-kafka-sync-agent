package com.miimetiq.keycloak.sync.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Readiness
@ApplicationScoped
public class KafkaHealthCheck implements HealthCheck {

    private static final Logger LOG = Logger.getLogger(KafkaHealthCheck.class);

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Override
    public HealthCheckResponse call() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");
        configs.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000");

        try (AdminClient adminClient = AdminClient.create(configs)) {
            // Try to list topics with a timeout to verify connectivity
            adminClient.listTopics().names().get(5, TimeUnit.SECONDS);

            LOG.debug("Kafka health check passed");
            return HealthCheckResponse
                    .named("kafka")
                    .up()
                    .withData("bootstrap.servers", bootstrapServers)
                    .build();
        } catch (Exception e) {
            LOG.error("Kafka health check failed", e);
            return HealthCheckResponse
                    .named("kafka")
                    .down()
                    .withData("bootstrap.servers", bootstrapServers)
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}

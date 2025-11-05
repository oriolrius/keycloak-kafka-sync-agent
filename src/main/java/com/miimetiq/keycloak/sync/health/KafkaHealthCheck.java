package com.miimetiq.keycloak.sync.health;

import com.miimetiq.keycloak.sync.kafka.KafkaConfig;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

/**
 * Health check for Kafka connectivity.
 * Uses circuit breaker pattern to prevent repeated connection attempts to failing service.
 */
@Readiness
@ApplicationScoped
public class KafkaHealthCheck implements HealthCheck {

    private static final Logger LOG = Logger.getLogger(KafkaHealthCheck.class);

    @Inject
    CircuitBreakerService circuitBreakerService;

    @Inject
    KafkaConfig kafkaConfig;

    @Inject
    CircuitBreakerMaintenance circuitBreakerMaintenance;

    @Override
    public HealthCheckResponse call() {
        CircuitBreakerState circuitState = getCircuitBreakerState();

        try {
            // Check connectivity through circuit breaker
            circuitBreakerService.checkKafkaConnectivity();

            LOG.debug("Kafka health check passed");
            return HealthCheckResponse
                    .named("kafka")
                    .up()
                    .withData("bootstrap.servers", kafkaConfig.bootstrapServers())
                    .withData("security.protocol", kafkaConfig.securityProtocol())
                    .withData("circuit_breaker_state", circuitState.toString())
                    .build();
        } catch (CircuitBreakerOpenException e) {
            LOG.warn("Kafka health check skipped - circuit breaker is open");
            return HealthCheckResponse
                    .named("kafka")
                    .down()
                    .withData("bootstrap.servers", kafkaConfig.bootstrapServers())
                    .withData("security.protocol", kafkaConfig.securityProtocol())
                    .withData("circuit_breaker_state", circuitState.toString())
                    .withData("error", "Circuit breaker is open - too many consecutive failures")
                    .build();
        } catch (Exception e) {
            LOG.error("Kafka health check failed", e);
            return HealthCheckResponse
                    .named("kafka")
                    .down()
                    .withData("bootstrap.servers", kafkaConfig.bootstrapServers())
                    .withData("security.protocol", kafkaConfig.securityProtocol())
                    .withData("circuit_breaker_state", circuitState.toString())
                    .withData("error", e.getMessage())
                    .build();
        }
    }

    private CircuitBreakerState getCircuitBreakerState() {
        try {
            return circuitBreakerMaintenance.currentState("kafka-connectivity");
        } catch (Exception e) {
            LOG.debug("Unable to get circuit breaker state: " + e.getMessage());
            return CircuitBreakerState.CLOSED;
        }
    }
}

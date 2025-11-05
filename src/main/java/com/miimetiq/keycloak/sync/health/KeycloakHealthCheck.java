package com.miimetiq.keycloak.sync.health;

import com.miimetiq.keycloak.sync.keycloak.KeycloakConfig;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;
import org.keycloak.representations.idm.RealmRepresentation;

/**
 * Health check for Keycloak Admin client connectivity.
 * Validates that the client can authenticate and interact with the Keycloak API.
 * Uses circuit breaker pattern to prevent repeated connection attempts to failing service.
 */
@Readiness
@ApplicationScoped
public class KeycloakHealthCheck implements HealthCheck {

    private static final Logger LOG = Logger.getLogger(KeycloakHealthCheck.class);

    @Inject
    CircuitBreakerService circuitBreakerService;

    @Inject
    KeycloakConfig config;

    @Inject
    CircuitBreakerMaintenance circuitBreakerMaintenance;

    @Override
    public HealthCheckResponse call() {
        CircuitBreakerState circuitState = getCircuitBreakerState();

        try {
            // Check connectivity through circuit breaker
            RealmRepresentation realm = circuitBreakerService.checkKeycloakConnectivity();

            LOG.debug("Keycloak health check passed - realm info retrieved successfully");
            return HealthCheckResponse
                    .named("keycloak-admin-client")
                    .up()
                    .withData("url", config.url())
                    .withData("realm", realm.getRealm())
                    .withData("realm_enabled", realm.isEnabled())
                    .withData("client_id", config.clientId())
                    .withData("circuit_breaker_state", circuitState.toString())
                    .build();
        } catch (CircuitBreakerOpenException e) {
            LOG.warn("Keycloak health check skipped - circuit breaker is open");
            return HealthCheckResponse
                    .named("keycloak-admin-client")
                    .down()
                    .withData("url", config.url())
                    .withData("realm", config.realm())
                    .withData("client_id", config.clientId())
                    .withData("circuit_breaker_state", circuitState.toString())
                    .withData("error", "Circuit breaker is open - too many consecutive failures")
                    .build();
        } catch (Exception e) {
            LOG.error("Keycloak health check failed - unable to connect or authenticate", e);
            return HealthCheckResponse
                    .named("keycloak-admin-client")
                    .down()
                    .withData("url", config.url())
                    .withData("realm", config.realm())
                    .withData("client_id", config.clientId())
                    .withData("circuit_breaker_state", circuitState.toString())
                    .withData("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }

    private CircuitBreakerState getCircuitBreakerState() {
        try {
            return circuitBreakerMaintenance.currentState("keycloak-connectivity");
        } catch (Exception e) {
            LOG.debug("Unable to get circuit breaker state: " + e.getMessage());
            return CircuitBreakerState.CLOSED;
        }
    }
}

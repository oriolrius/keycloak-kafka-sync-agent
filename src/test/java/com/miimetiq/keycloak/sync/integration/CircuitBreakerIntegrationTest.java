package com.miimetiq.keycloak.sync.integration;

import com.miimetiq.keycloak.sync.health.CircuitBreakerService;
import com.miimetiq.keycloak.sync.health.KafkaHealthCheck;
import com.miimetiq.keycloak.sync.health.KeycloakHealthCheck;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for circuit breaker behavior in health checks.
 * Tests the full circuit breaker lifecycle with health check beans.
 */
@QuarkusTest
class CircuitBreakerIntegrationTest {

    @Inject
    CircuitBreakerMaintenance circuitBreakerMaintenance;

    @Inject
    CircuitBreakerService circuitBreakerService;

    @Inject
    KeycloakHealthCheck keycloakHealthCheck;

    @Inject
    KafkaHealthCheck kafkaHealthCheck;

    @BeforeEach
    void setUp() {
        // Reset circuit breakers before each test
        try {
            circuitBreakerMaintenance.resetAll();
        } catch (Exception e) {
            // Ignore if not initialized
        }
    }

    @Test
    void testHealthCheckIncludesCircuitBreakerState() {
        // When: Call health check beans
        HealthCheckResponse keycloakResponse = keycloakHealthCheck.call();
        HealthCheckResponse kafkaResponse = kafkaHealthCheck.call();

        // Then: Response should include circuit breaker state
        assertTrue(keycloakResponse.getData().isPresent(), "Keycloak health check should have data");
        assertTrue(keycloakResponse.getData().get().containsKey("circuit_breaker_state"),
                "Keycloak health check should include circuit breaker state");

        assertTrue(kafkaResponse.getData().isPresent(), "Kafka health check should have data");
        assertTrue(kafkaResponse.getData().get().containsKey("circuit_breaker_state"),
                "Kafka health check should include circuit breaker state");
    }

    @Test
    void testHealthCheckWithClosedCircuit() {
        // Given: Circuit breakers are reset (closed)
        circuitBreakerMaintenance.resetAll();

        // When: Call health check beans
        HealthCheckResponse keycloakResponse = keycloakHealthCheck.call();
        HealthCheckResponse kafkaResponse = kafkaHealthCheck.call();

        // Then: Circuit breaker states should be CLOSED
        String keycloakCircuitState = (String) keycloakResponse.getData().get().get("circuit_breaker_state");
        String kafkaCircuitState = (String) kafkaResponse.getData().get().get("circuit_breaker_state");

        assertEquals("CLOSED", keycloakCircuitState, "Keycloak circuit should be CLOSED after reset");
        assertEquals("CLOSED", kafkaCircuitState, "Kafka circuit should be CLOSED after reset");
    }

    @Test
    void testCircuitBreakerStatesCanBeQueried() {
        // When: Query circuit breaker states via maintenance API
        CircuitBreakerState keycloakState = circuitBreakerMaintenance.currentState("keycloak-connectivity");
        CircuitBreakerState kafkaState = circuitBreakerMaintenance.currentState("kafka-connectivity");

        // Then: States should be queryable
        assertNotNull(keycloakState, "Keycloak circuit breaker state should be queryable");
        assertNotNull(kafkaState, "Kafka circuit breaker state should be queryable");
    }

    @Test
    void testMultipleHealthCheckCallsMaintainCircuitState() {
        // Given: Circuit breakers are reset
        circuitBreakerMaintenance.resetAll();

        // When: Make multiple health check calls
        for (int i = 0; i < 3; i++) {
            keycloakHealthCheck.call();
            kafkaHealthCheck.call();
        }

        // Then: Circuit breakers should still be functional
        CircuitBreakerState keycloakState = circuitBreakerMaintenance.currentState("keycloak-connectivity");
        CircuitBreakerState kafkaState = circuitBreakerMaintenance.currentState("kafka-connectivity");

        assertNotNull(keycloakState, "Keycloak circuit breaker should still be functional");
        assertNotNull(kafkaState, "Kafka circuit breaker should still be functional");
    }
}

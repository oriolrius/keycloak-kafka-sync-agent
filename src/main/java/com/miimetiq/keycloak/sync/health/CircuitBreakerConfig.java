package com.miimetiq.keycloak.sync.health;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Circuit breaker configuration properties.
 * Controls fault tolerance behavior for Keycloak and Kafka connectivity.
 */
@ConfigMapping(prefix = "circuit-breaker")
public interface CircuitBreakerConfig {

    /**
     * Number of consecutive failures before opening the circuit.
     * Can be overridden with CIRCUIT_BREAKER_FAILURE_THRESHOLD environment variable.
     */
    @WithDefault("5")
    int failureThreshold();

    /**
     * Time in milliseconds to wait before attempting to close an open circuit (half-open state).
     * Can be overridden with CIRCUIT_BREAKER_DELAY environment variable.
     */
    @WithDefault("60000")
    int delayMs();

    /**
     * Number of successful requests in half-open state required to close the circuit.
     * Can be overridden with CIRCUIT_BREAKER_SUCCESS_THRESHOLD environment variable.
     */
    @WithDefault("2")
    int successThreshold();

    /**
     * Request timeout in milliseconds for circuit breaker operations.
     * Can be overridden with CIRCUIT_BREAKER_REQUEST_TIMEOUT environment variable.
     */
    @WithDefault("5000")
    int requestTimeoutMs();
}

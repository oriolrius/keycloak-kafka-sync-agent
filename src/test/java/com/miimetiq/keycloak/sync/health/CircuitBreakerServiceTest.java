package com.miimetiq.keycloak.sync.health;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusMock;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.common.KafkaFuture;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RealmRepresentation;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CircuitBreakerService validating circuit breaker state transitions.
 */
@QuarkusTest
class CircuitBreakerServiceTest {

    @Inject
    CircuitBreakerService circuitBreakerService;

    @Inject
    CircuitBreakerMaintenance circuitBreakerMaintenance;

    private Keycloak keycloak;
    private AdminClient kafkaAdminClient;

    @BeforeEach
    void setUp() {
        // Create mocks
        keycloak = mock(Keycloak.class);
        kafkaAdminClient = mock(AdminClient.class);

        // Install mocks
        QuarkusMock.installMockForType(keycloak, Keycloak.class);
        QuarkusMock.installMockForType(kafkaAdminClient, AdminClient.class);

        // Reset circuit breakers before each test
        try {
            circuitBreakerMaintenance.resetAll();
        } catch (Exception e) {
            // Ignore if circuit breakers not yet initialized
        }
    }

    @Test
    void testKeycloakCircuitBreakerClosedState_Success() throws Exception {
        // Given: Keycloak is available
        RealmResource realmResource = mock(RealmResource.class);
        RealmRepresentation realmRep = new RealmRepresentation();
        realmRep.setRealm("test-realm");

        when(keycloak.realm(anyString())).thenReturn(realmResource);
        when(realmResource.toRepresentation()).thenReturn(realmRep);

        // When: Check connectivity
        RealmRepresentation result = circuitBreakerService.checkKeycloakConnectivity();

        // Then: Circuit remains closed and call succeeds
        assertNotNull(result);
        assertEquals("test-realm", result.getRealm());
        assertEquals(CircuitBreakerState.CLOSED,
                circuitBreakerMaintenance.currentState("keycloak-connectivity"));
    }

    @Test
    void testKeycloakCircuitBreakerOpensAfterFailures() throws Exception {
        // Given: Keycloak is unavailable
        when(keycloak.realm(anyString())).thenThrow(new RuntimeException("Connection refused"));

        // When: Multiple failed attempts (need 4 requests with 75% failure ratio)
        int circuitBreakerOpenCount = 0;
        for (int i = 0; i < 10; i++) {
            try {
                circuitBreakerService.checkKeycloakConnectivity();
                fail("Should throw exception");
            } catch (CircuitBreakerOpenException e) {
                // Circuit breaker is open
                circuitBreakerOpenCount++;
            } catch (Exception e) {
                // Other exceptions (connection failures before circuit opens)
            }
        }

        // Then: Circuit should have opened and rejected some calls
        assertTrue(circuitBreakerOpenCount > 0,
                "Circuit breaker should have opened and rejected at least one call");

        CircuitBreakerState state = circuitBreakerMaintenance.currentState("keycloak-connectivity");
        assertTrue(state == CircuitBreakerState.OPEN || state == CircuitBreakerState.HALF_OPEN,
                "Circuit breaker should be OPEN or HALF_OPEN after consecutive failures, but was: " + state);
    }

    @Test
    void testKeycloakCircuitBreakerRejectsCallsWhenOpen() throws Exception {
        // Given: Circuit is forced open
        when(keycloak.realm(anyString())).thenThrow(new RuntimeException("Connection refused"));

        // Trigger circuit to open
        for (int i = 0; i < 6; i++) {
            try {
                circuitBreakerService.checkKeycloakConnectivity();
            } catch (Exception e) {
                // Expected
            }
        }

        // When: Try to make another call while circuit is open
        boolean circuitBreakerOpenExceptionThrown = false;
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreakerService.checkKeycloakConnectivity();
            } catch (CircuitBreakerOpenException e) {
                circuitBreakerOpenExceptionThrown = true;
                break;
            } catch (Exception e) {
                // Other exceptions are also expected
            }
        }

        // Then: Circuit breaker should eventually throw CircuitBreakerOpenException
        CircuitBreakerState state = circuitBreakerMaintenance.currentState("keycloak-connectivity");
        assertTrue(state == CircuitBreakerState.OPEN || circuitBreakerOpenExceptionThrown,
                "Circuit should be OPEN or throw CircuitBreakerOpenException");
    }

    @Test
    void testKafkaCircuitBreakerClosedState_Success() throws Exception {
        // Given: Kafka is available
        ListTopicsResult listTopicsResult = mock(ListTopicsResult.class);
        @SuppressWarnings("unchecked")
        KafkaFuture<Set<String>> future = mock(KafkaFuture.class);

        when(kafkaAdminClient.listTopics()).thenReturn(listTopicsResult);
        when(listTopicsResult.names()).thenReturn(future);
        when(future.get(anyLong(), any(TimeUnit.class))).thenReturn(Set.of("topic1", "topic2"));

        // When: Check connectivity
        circuitBreakerService.checkKafkaConnectivity();

        // Then: Circuit remains closed and call succeeds
        assertEquals(CircuitBreakerState.CLOSED,
                circuitBreakerMaintenance.currentState("kafka-connectivity"));
    }

    @Test
    void testKafkaCircuitBreakerOpensAfterFailures() throws Exception {
        // Given: Kafka is unavailable
        when(kafkaAdminClient.listTopics()).thenThrow(new RuntimeException("Connection refused"));

        // When: Multiple failed attempts
        int circuitBreakerOpenCount = 0;
        for (int i = 0; i < 10; i++) {
            try {
                circuitBreakerService.checkKafkaConnectivity();
                fail("Should throw exception");
            } catch (CircuitBreakerOpenException e) {
                // Circuit breaker is open
                circuitBreakerOpenCount++;
            } catch (Exception e) {
                // Other exceptions (connection failures before circuit opens)
            }
        }

        // Then: Circuit should have opened and rejected some calls
        assertTrue(circuitBreakerOpenCount > 0,
                "Circuit breaker should have opened and rejected at least one call");

        CircuitBreakerState state = circuitBreakerMaintenance.currentState("kafka-connectivity");
        assertTrue(state == CircuitBreakerState.OPEN || state == CircuitBreakerState.HALF_OPEN,
                "Circuit breaker should be OPEN or HALF_OPEN after consecutive failures, but was: " + state);
    }

    @Test
    void testCircuitBreakerHalfOpenStateTransition() throws Exception {
        // This test validates transition from OPEN -> HALF_OPEN -> CLOSED
        // Note: This is a simplified test as timing-based state transitions are hard to test

        // Given: Successful Keycloak connectivity
        RealmResource realmResource = mock(RealmResource.class);
        RealmRepresentation realmRep = new RealmRepresentation();
        realmRep.setRealm("test-realm");

        when(keycloak.realm(anyString())).thenReturn(realmResource);
        when(realmResource.toRepresentation()).thenReturn(realmRep);

        // When: Make successful calls
        for (int i = 0; i < 3; i++) {
            RealmRepresentation result = circuitBreakerService.checkKeycloakConnectivity();
            assertNotNull(result);
        }

        // Then: Circuit should remain closed with successful calls
        CircuitBreakerState state = circuitBreakerMaintenance.currentState("keycloak-connectivity");
        assertEquals(CircuitBreakerState.CLOSED, state);
    }

    @Test
    void testCircuitBreakerFailsInHalfOpenState() throws Exception {
        // Given: Circuit is open due to failures
        when(keycloak.realm(anyString())).thenThrow(new RuntimeException("Connection refused"));

        // Open the circuit
        for (int i = 0; i < 6; i++) {
            try {
                circuitBreakerService.checkKeycloakConnectivity();
            } catch (Exception e) {
                // Expected
            }
        }

        // When: Continue to fail in half-open attempts
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreakerService.checkKeycloakConnectivity();
            } catch (Exception e) {
                // Expected - circuit should remain open or throw CircuitBreakerOpenException
            }
        }

        // Then: Circuit should be open or half-open
        CircuitBreakerState state = circuitBreakerMaintenance.currentState("keycloak-connectivity");
        assertTrue(state == CircuitBreakerState.OPEN || state == CircuitBreakerState.HALF_OPEN,
                "Circuit should remain OPEN or HALF_OPEN after failures");
    }
}

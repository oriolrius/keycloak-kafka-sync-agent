package com.miimetiq.keycloak.sync.health;

import com.miimetiq.keycloak.sync.kafka.KafkaConfig;
import com.miimetiq.keycloak.sync.keycloak.KeycloakConfig;
import io.smallrye.faulttolerance.api.CircuitBreakerName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.AdminClient;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RealmRepresentation;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * Service that wraps Keycloak and Kafka connectivity checks with circuit breaker pattern.
 * Prevents repeated connection attempts to failing services.
 */
@ApplicationScoped
public class CircuitBreakerService {

    private static final Logger LOG = Logger.getLogger(CircuitBreakerService.class);

    @Inject
    Keycloak keycloak;

    @Inject
    AdminClient kafkaAdminClient;

    @Inject
    KeycloakConfig keycloakConfig;

    @Inject
    KafkaConfig kafkaConfig;

    @Inject
    CircuitBreakerConfig circuitBreakerConfig;

    /**
     * Check Keycloak connectivity with circuit breaker protection.
     * Circuit opens after configured consecutive failures and stays open for configured delay.
     *
     * @return Realm representation if successful
     * @throws Exception if connectivity check fails
     */
    @CircuitBreaker(
            requestVolumeThreshold = 4,
            failureRatio = 0.75,
            delay = 60000,
            delayUnit = ChronoUnit.MILLIS,
            successThreshold = 2
    )
    @CircuitBreakerName("keycloak-connectivity")
    @Timeout(value = 5000, unit = ChronoUnit.MILLIS)
    public RealmRepresentation checkKeycloakConnectivity() throws Exception {
        LOG.debug("Checking Keycloak connectivity");
        try {
            RealmRepresentation realm = keycloak.realm(keycloakConfig.realm()).toRepresentation();
            if (realm == null || realm.getRealm() == null) {
                throw new IllegalStateException("Realm information is null");
            }
            LOG.debug("Keycloak connectivity check successful");
            return realm;
        } catch (Exception e) {
            LOG.warn("Keycloak connectivity check failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Check Kafka connectivity with circuit breaker protection.
     * Circuit opens after configured consecutive failures and stays open for configured delay.
     *
     * @throws Exception if connectivity check fails
     */
    @CircuitBreaker(
            requestVolumeThreshold = 4,
            failureRatio = 0.75,
            delay = 60000,
            delayUnit = ChronoUnit.MILLIS,
            successThreshold = 2
    )
    @CircuitBreakerName("kafka-connectivity")
    @Timeout(value = 5000, unit = ChronoUnit.MILLIS)
    public void checkKafkaConnectivity() throws Exception {
        LOG.debug("Checking Kafka connectivity");
        try {
            kafkaAdminClient.listTopics().names().get(5, TimeUnit.SECONDS);
            LOG.debug("Kafka connectivity check successful");
        } catch (Exception e) {
            LOG.warn("Kafka connectivity check failed: " + e.getMessage());
            throw e;
        }
    }
}

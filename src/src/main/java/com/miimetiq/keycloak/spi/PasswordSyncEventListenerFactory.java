package com.miimetiq.keycloak.spi;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Factory for PasswordSyncEventListener.
 *
 * This factory creates event listener instances that intercept password-related
 * admin events and synchronize passwords directly to Kafka SCRAM credentials.
 */
public class PasswordSyncEventListenerFactory implements EventListenerProviderFactory {

    private static final Logger LOG = Logger.getLogger(PasswordSyncEventListenerFactory.class);
    private static final String PROVIDER_ID = "password-sync-listener";

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        // Pass session to enable querying Keycloak for usernames
        return new PasswordSyncEventListener(session);
    }

    @Override
    public void init(Config.Scope config) {
        LOG.info("Initializing PasswordSyncEventListener SPI");
        // Initialize Kafka AdminClient on SPI startup
        try {
            KafkaAdminClientFactory.getAdminClient();
            LOG.info("Kafka AdminClient initialized successfully");
        } catch (Exception e) {
            LOG.error("Failed to initialize Kafka AdminClient - password sync may not work", e);
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization needed
    }

    @Override
    public void close() {
        LOG.info("Closing PasswordSyncEventListener SPI");
        // Close Kafka AdminClient on SPI shutdown
        KafkaAdminClientFactory.close();
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}

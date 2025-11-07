package com.miimetiq.keycloak.spi;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Factory for PasswordSyncEventListener.
 *
 * This factory creates event listener instances that intercept password-related
 * admin events and send them to the sync-agent webhook.
 */
public class PasswordSyncEventListenerFactory implements EventListenerProviderFactory {

    private static final String PROVIDER_ID = "password-sync-listener";

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        // Pass session to enable querying Keycloak for usernames
        return new PasswordSyncEventListener(session);
    }

    @Override
    public void init(Config.Scope config) {
        // No initialization needed
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization needed
    }

    @Override
    public void close() {
        // No resources to close
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}

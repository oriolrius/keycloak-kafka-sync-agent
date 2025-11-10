package com.miimetiq.keycloak.spi;

import org.keycloak.Config;
import org.keycloak.credential.hash.PasswordHashProvider;
import org.keycloak.credential.hash.PasswordHashProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Factory for PasswordSyncHashProviderSimple.
 *
 * This factory registers a custom password hash provider with the same ID
 * as Keycloak's built-in pbkdf2-sha256 provider, effectively overriding it
 * with a higher priority order.
 */
public class PasswordSyncHashProviderFactory implements PasswordHashProviderFactory {

    public static final String ID = "pbkdf2-sha256";  // Override default provider
    public static final int DEFAULT_ITERATIONS = 27500;

    @Override
    public PasswordHashProvider create(KeycloakSession session) {
        return new PasswordSyncHashProviderSimple(ID, DEFAULT_ITERATIONS);
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
        return ID;
    }

    @Override
    public int order() {
        // CRITICAL: Very high priority to override Keycloak's built-in provider
        // Default providers use priority 0-10, we use 1000 to ensure we take precedence
        return 1000;
    }
}

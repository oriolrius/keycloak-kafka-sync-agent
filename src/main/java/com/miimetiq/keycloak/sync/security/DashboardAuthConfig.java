package com.miimetiq.keycloak.sync.security;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

/**
 * Configuration for dashboard authentication.
 * Reads from DASHBOARD_BASIC_AUTH environment variable or dashboard.basic-auth property.
 */
@ConfigMapping(prefix = "dashboard")
public interface DashboardAuthConfig {

    /**
     * Basic authentication credentials in format "username:password".
     * If not set, dashboard authentication is disabled.
     *
     * @return optional basic auth credentials
     */
    @WithName("basic-auth")
    Optional<String> basicAuth();

    /**
     * Enable OIDC authentication via Keycloak.
     * If true, uses Keycloak for authentication instead of Basic Auth.
     *
     * @return true if OIDC auth is enabled
     */
    @WithName("oidc-enabled")
    @WithDefault("false")
    boolean oidcEnabled();

    /**
     * Required role for dashboard access when using OIDC.
     * Defaults to "dashboard-admin".
     *
     * @return required role name
     */
    @WithName("oidc-required-role")
    @WithDefault("dashboard-admin")
    String oidcRequiredRole();
}

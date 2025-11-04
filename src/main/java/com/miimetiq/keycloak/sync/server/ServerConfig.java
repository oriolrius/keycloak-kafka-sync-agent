package com.miimetiq.keycloak.sync.server;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Server configuration properties.
 * Reads from application.properties and environment variables.
 */
@ConfigMapping(prefix = "server")
public interface ServerConfig {

    /**
     * HTTP server port.
     * Can be overridden with SERVER_PORT environment variable.
     * Default: 8088
     */
    @WithDefault("8088")
    int port();

    /**
     * Basic authentication credentials for dashboard access.
     * Format: "username:password"
     * Can be overridden with DASHBOARD_BASIC_AUTH environment variable.
     * If not set, dashboard is accessible without authentication (not recommended for production).
     */
    Optional<String> dashboardBasicAuth();
}

package com.miimetiq.keycloak.sync.retention;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Retention configuration properties.
 * Reads from application.properties and environment variables.
 */
@ConfigMapping(prefix = "retention")
public interface RetentionConfig {

    /**
     * Maximum database size in bytes.
     * When exceeded, oldest entries are purged.
     * Can be overridden with RETENTION_MAX_BYTES environment variable.
     * Default: 256 MB (268435456 bytes)
     */
    @WithDefault("268435456")
    long maxBytes();

    /**
     * Maximum age of records in days.
     * Records older than this are purged.
     * Can be overridden with RETENTION_MAX_AGE_DAYS environment variable.
     * Default: 30 days
     */
    @WithDefault("30")
    int maxAgeDays();

    /**
     * Interval in seconds between retention purge runs.
     * Can be overridden with RETENTION_PURGE_INTERVAL_SECONDS environment variable.
     * Default: 300 seconds (5 minutes)
     */
    @WithDefault("300")
    int purgeIntervalSeconds();
}

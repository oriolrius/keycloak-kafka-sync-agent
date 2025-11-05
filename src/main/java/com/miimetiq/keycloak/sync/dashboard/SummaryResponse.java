package com.miimetiq.keycloak.sync.dashboard;

import java.time.LocalDateTime;

/**
 * Response DTO for summary statistics endpoint.
 * Provides high-level metrics about sync operations over the last hour.
 */
public class SummaryResponse {
    /**
     * Number of operations per hour
     */
    public Long opsPerHour;

    /**
     * Error rate as a percentage (0-100)
     */
    public Double errorRate;

    /**
     * 95th percentile latency in milliseconds
     */
    public Integer latencyP95;

    /**
     * 99th percentile latency in milliseconds
     */
    public Integer latencyP99;

    /**
     * Current approximate database size in bytes
     */
    public Long dbUsageBytes;

    /**
     * Timestamp when these statistics were computed
     */
    public LocalDateTime timestamp;

    public SummaryResponse() {
    }

    public SummaryResponse(Long opsPerHour, Double errorRate, Integer latencyP95,
                          Integer latencyP99, Long dbUsageBytes, LocalDateTime timestamp) {
        this.opsPerHour = opsPerHour;
        this.errorRate = errorRate;
        this.latencyP95 = latencyP95;
        this.latencyP99 = latencyP99;
        this.dbUsageBytes = dbUsageBytes;
        this.timestamp = timestamp;
    }
}

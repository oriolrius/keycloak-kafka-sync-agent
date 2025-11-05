package com.miimetiq.keycloak.sync.dashboard;

import java.time.LocalDateTime;

/**
 * Response DTO for sync batch summary.
 * Represents a single batch (reconciliation cycle) in the dashboard.
 */
public class BatchResponse {
    /**
     * Unique batch ID
     */
    public Long id;

    /**
     * Correlation ID for tracking
     */
    public String correlationId;

    /**
     * When the batch started
     */
    public LocalDateTime startedAt;

    /**
     * When the batch finished (null if still running)
     */
    public LocalDateTime finishedAt;

    /**
     * Source of the batch (e.g., SCHEDULED, MANUAL, WEBHOOK)
     */
    public String source;

    /**
     * Total items in batch
     */
    public Integer itemsTotal;

    /**
     * Successfully processed items
     */
    public Integer itemsSuccess;

    /**
     * Failed items
     */
    public Integer itemsError;

    /**
     * Duration in milliseconds (null if still running)
     */
    public Long durationMs;

    /**
     * Whether the batch is complete
     */
    public boolean complete;

    public BatchResponse() {
    }

    public BatchResponse(Long id, String correlationId, LocalDateTime startedAt,
                        LocalDateTime finishedAt, String source, Integer itemsTotal,
                        Integer itemsSuccess, Integer itemsError) {
        this.id = id;
        this.correlationId = correlationId;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.source = source;
        this.itemsTotal = itemsTotal;
        this.itemsSuccess = itemsSuccess;
        this.itemsError = itemsError;
        this.complete = finishedAt != null;

        // Calculate duration if batch is complete
        if (finishedAt != null && startedAt != null) {
            this.durationMs = java.time.Duration.between(startedAt, finishedAt).toMillis();
        }
    }
}

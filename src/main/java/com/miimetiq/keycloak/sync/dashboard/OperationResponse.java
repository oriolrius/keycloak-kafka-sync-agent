package com.miimetiq.keycloak.sync.dashboard;

import java.time.LocalDateTime;

/**
 * Response DTO for individual sync operation.
 * Represents a single operation in the timeline view.
 */
public class OperationResponse {
    /**
     * Unique operation ID
     */
    public Long id;

    /**
     * Correlation ID linking related operations
     */
    public String correlationId;

    /**
     * When the operation occurred
     */
    public LocalDateTime occurredAt;

    /**
     * Keycloak realm
     */
    public String realm;

    /**
     * Kafka cluster ID
     */
    public String clusterId;

    /**
     * Principal (username)
     */
    public String principal;

    /**
     * Operation type (e.g., CREATE_CREDENTIAL, DELETE_CREDENTIAL, CREATE_ACL)
     */
    public String opType;

    /**
     * SCRAM mechanism (if applicable)
     */
    public String mechanism;

    /**
     * Operation result (SUCCESS, ERROR, SKIPPED)
     */
    public String result;

    /**
     * Error code if operation failed
     */
    public String errorCode;

    /**
     * Error message if operation failed
     */
    public String errorMessage;

    /**
     * Duration in milliseconds
     */
    public Integer durationMs;

    public OperationResponse() {
    }

    public OperationResponse(Long id, String correlationId, LocalDateTime occurredAt,
                            String realm, String clusterId, String principal,
                            String opType, String mechanism, String result,
                            String errorCode, String errorMessage, Integer durationMs) {
        this.id = id;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
        this.realm = realm;
        this.clusterId = clusterId;
        this.principal = principal;
        this.opType = opType;
        this.mechanism = mechanism;
        this.result = result;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
    }
}

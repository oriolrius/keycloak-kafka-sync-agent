package com.miimetiq.keycloak.sync.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing a Keycloak Admin Event received via webhook.
 * <p>
 * This models the structure of admin events sent by Keycloak's event listener.
 * Events contain information about administrative operations performed on resources
 * like users, clients, realms, etc.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KeycloakAdminEvent {

    @JsonProperty("id")
    private String id;

    @JsonProperty("time")
    private Long time;

    @JsonProperty("realmId")
    private String realmId;

    @JsonProperty("authDetails")
    private AuthDetails authDetails;

    @JsonProperty("resourceType")
    private String resourceType;

    @JsonProperty("operationType")
    private String operationType;

    @JsonProperty("resourcePath")
    private String resourcePath;

    @JsonProperty("representation")
    private String representation;

    @JsonProperty("error")
    private String error;

    // Constructors

    public KeycloakAdminEvent() {
    }

    public KeycloakAdminEvent(String id, Long time, String realmId, String resourceType,
                              String operationType, String resourcePath) {
        this.id = id;
        this.time = time;
        this.realmId = realmId;
        this.resourceType = resourceType;
        this.operationType = operationType;
        this.resourcePath = resourcePath;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public String getRealmId() {
        return realmId;
    }

    public void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    public AuthDetails getAuthDetails() {
        return authDetails;
    }

    public void setAuthDetails(AuthDetails authDetails) {
        this.authDetails = authDetails;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String getRepresentation() {
        return representation;
    }

    public void setRepresentation(String representation) {
        this.representation = representation;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    /**
     * Nested DTO for authentication details within an admin event.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AuthDetails {
        @JsonProperty("realmId")
        private String realmId;

        @JsonProperty("clientId")
        private String clientId;

        @JsonProperty("userId")
        private String userId;

        @JsonProperty("ipAddress")
        private String ipAddress;

        // Constructors

        public AuthDetails() {
        }

        public AuthDetails(String realmId, String clientId, String userId, String ipAddress) {
            this.realmId = realmId;
            this.clientId = clientId;
            this.userId = userId;
            this.ipAddress = ipAddress;
        }

        // Getters and Setters

        public String getRealmId() {
            return realmId;
        }

        public void setRealmId(String realmId) {
            this.realmId = realmId;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }
    }

    @Override
    public String toString() {
        return "KeycloakAdminEvent{" +
                "id='" + id + '\'' +
                ", time=" + time +
                ", realmId='" + realmId + '\'' +
                ", resourceType='" + resourceType + '\'' +
                ", operationType='" + operationType + '\'' +
                ", resourcePath='" + resourcePath + '\'' +
                '}';
    }
}

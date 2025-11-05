package com.miimetiq.keycloak.sync.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration test for KeycloakWebhookResource REST endpoints.
 * <p>
 * Tests webhook event reception with realistic mock Keycloak admin event payloads
 * including user create, update, delete, and password change operations.
 */
@QuarkusTest
@QuarkusTestResource(IntegrationTestResource.class)
@DisplayName("Keycloak Webhook REST API Integration Tests")
class KeycloakWebhookResourceIntegrationTest {

    @Test
    @DisplayName("POST /api/kc/events should accept and process CREATE_USER event")
    void testReceiveEvent_CreateUser() {
        String createUserEvent = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440001",
                    "time": 1699000000000,
                    "realmId": "test-realm",
                    "authDetails": {
                        "realmId": "test-realm",
                        "clientId": "admin-cli",
                        "userId": "admin-user-id",
                        "ipAddress": "192.168.1.100"
                    },
                    "resourceType": "USER",
                    "operationType": "CREATE",
                    "resourcePath": "users/123e4567-e89b-12d3-a456-426614174000",
                    "representation": "{\\"username\\":\\"john.doe\\",\\"email\\":\\"john@example.com\\"}"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(createUserEvent)
                .when()
                .post("/api/kc/events")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("correlationId", notNullValue())
                .body("message", equalTo("Event received successfully"))
                .body("resourceType", equalTo("USER"))
                .body("operationType", equalTo("CREATE"));
    }

    @Test
    @DisplayName("POST /api/kc/events should accept and process UPDATE_USER event")
    void testReceiveEvent_UpdateUser() {
        String updateUserEvent = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440002",
                    "time": 1699000100000,
                    "realmId": "test-realm",
                    "authDetails": {
                        "realmId": "test-realm",
                        "clientId": "admin-cli",
                        "userId": "admin-user-id",
                        "ipAddress": "192.168.1.100"
                    },
                    "resourceType": "USER",
                    "operationType": "UPDATE",
                    "resourcePath": "users/123e4567-e89b-12d3-a456-426614174000",
                    "representation": "{\\"username\\":\\"john.doe\\",\\"email\\":\\"john.updated@example.com\\",\\"firstName\\":\\"John\\",\\"lastName\\":\\"Doe\\"}"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(updateUserEvent)
                .when()
                .post("/api/kc/events")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("correlationId", notNullValue())
                .body("message", equalTo("Event received successfully"))
                .body("resourceType", equalTo("USER"))
                .body("operationType", equalTo("UPDATE"));
    }

    @Test
    @DisplayName("POST /api/kc/events should accept and process DELETE_USER event")
    void testReceiveEvent_DeleteUser() {
        String deleteUserEvent = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440003",
                    "time": 1699000200000,
                    "realmId": "test-realm",
                    "authDetails": {
                        "realmId": "test-realm",
                        "clientId": "admin-cli",
                        "userId": "admin-user-id",
                        "ipAddress": "192.168.1.100"
                    },
                    "resourceType": "USER",
                    "operationType": "DELETE",
                    "resourcePath": "users/123e4567-e89b-12d3-a456-426614174000"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(deleteUserEvent)
                .when()
                .post("/api/kc/events")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("correlationId", notNullValue())
                .body("message", equalTo("Event received successfully"))
                .body("resourceType", equalTo("USER"))
                .body("operationType", equalTo("DELETE"));
    }

    @Test
    @DisplayName("POST /api/kc/events should accept and process password change event")
    void testReceiveEvent_PasswordChange() {
        String passwordChangeEvent = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440004",
                    "time": 1699000300000,
                    "realmId": "test-realm",
                    "authDetails": {
                        "realmId": "test-realm",
                        "clientId": "admin-cli",
                        "userId": "admin-user-id",
                        "ipAddress": "192.168.1.100"
                    },
                    "resourceType": "USER",
                    "operationType": "UPDATE",
                    "resourcePath": "users/123e4567-e89b-12d3-a456-426614174000/reset-password"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(passwordChangeEvent)
                .when()
                .post("/api/kc/events")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("correlationId", notNullValue())
                .body("message", equalTo("Event received successfully"))
                .body("resourceType", equalTo("USER"))
                .body("operationType", equalTo("UPDATE"));
    }

    @Test
    @DisplayName("POST /api/kc/events should return 400 for malformed JSON")
    void testReceiveEvent_MalformedJson() {
        String malformedJson = "{ this is not valid json }";

        given()
                .contentType(ContentType.JSON)
                .body(malformedJson)
                .when()
                .post("/api/kc/events")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("POST /api/kc/events should return 400 for null payload")
    void testReceiveEvent_NullPayload() {
        given()
                .contentType(ContentType.JSON)
                .body("null")
                .when()
                .post("/api/kc/events")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", equalTo("Event payload is required"));
    }

    @Test
    @DisplayName("POST /api/kc/events should return 400 for missing required fields")
    void testReceiveEvent_MissingRequiredFields() {
        String incompleteEvent = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440005",
                    "time": 1699000400000,
                    "realmId": "test-realm"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(incompleteEvent)
                .when()
                .post("/api/kc/events")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", equalTo("Event must contain resourceType and operationType"));
    }

    @Test
    @DisplayName("POST /api/kc/events should correctly parse and enqueue events")
    void testReceiveEvent_ParsesAndEnqueues() {
        // This test validates that the endpoint correctly parses the event
        // and prepares it for enqueueing (actual queue processing in task-040)

        String validEvent = """
                {
                    "id": "test-event-id",
                    "time": 1699000500000,
                    "realmId": "test-realm",
                    "resourceType": "USER",
                    "operationType": "CREATE",
                    "resourcePath": "users/test-user-id",
                    "representation": "{\\"username\\":\\"testuser\\"}"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(validEvent)
                .when()
                .post("/api/kc/events")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("correlationId", matchesPattern("[a-f0-9\\-]{36}")) // UUID pattern
                .body("message", not(emptyOrNullString()))
                .body("resourceType", equalTo("USER"))
                .body("operationType", equalTo("CREATE"));
    }

    @Test
    @DisplayName("Webhook endpoint should be accessible")
    void testEndpointAccessible() {
        // Send a minimal valid event to verify endpoint is accessible
        String minimalEvent = """
                {
                    "resourceType": "USER",
                    "operationType": "CREATE"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(minimalEvent)
                .when()
                .post("/api/kc/events")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("Invalid HTTP methods should return 405")
    void testInvalidMethods() {
        // Webhook endpoint only accepts POST
        given()
                .when()
                .get("/api/kc/events")
                .then()
                .statusCode(405); // Method Not Allowed

        given()
                .when()
                .put("/api/kc/events")
                .then()
                .statusCode(405); // Method Not Allowed

        given()
                .when()
                .delete("/api/kc/events")
                .then()
                .statusCode(405); // Method Not Allowed
    }
}

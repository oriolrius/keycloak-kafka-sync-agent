---
name: keycloak-admin-integration
description: Expert guidance for Keycloak Admin API integration including user/client management, webhook event handling, HMAC signature verification, and pagination
---

# Keycloak Admin Integration

Comprehensive guide for integrating with Keycloak Admin API, handling admin events, and extracting user credentials for synchronization.

## Client Setup

### Using Keycloak Admin Client (Recommended)

```java
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.*;

@ApplicationScoped
public class KeycloakClientProducer {

    @ConfigProperty(name = "keycloak.server.url")
    String serverUrl;

    @ConfigProperty(name = "keycloak.realm")
    String realm;

    @ConfigProperty(name = "keycloak.client.id")
    String clientId;

    @ConfigProperty(name = "keycloak.client.secret")
    String clientSecret;

    private Keycloak keycloak;

    @PostConstruct
    void init() {
        this.keycloak = KeycloakBuilder.builder()
            .serverUrl(serverUrl)
            .realm(realm)
            .grantType("client_credentials")
            .clientId(clientId)
            .clientSecret(clientSecret)
            .build();
    }

    @Produces
    @ApplicationScoped
    public Keycloak keycloak() {
        return keycloak;
    }

    @Produces
    @ApplicationScoped
    public RealmResource realmResource() {
        return keycloak.realm(realm);
    }

    @PreDestroy
    void cleanup() {
        if (keycloak != null) {
            keycloak.close();
        }
    }
}
```

**Dependencies (Maven):**
```xml
<dependency>
    <groupId>org.keycloak</groupId>
    <artifactId>keycloak-admin-client</artifactId>
    <version>23.0.0</version>
</dependency>
```

### Alternative: REST Client with Quarkus

```java
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@RegisterRestClient(configKey = "keycloak-api")
@Path("/admin/realms/{realm}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface KeycloakRestClient {

    @GET
    @Path("/users")
    List<UserRepresentation> getUsers(
        @PathParam("realm") String realm,
        @QueryParam("first") Integer first,
        @QueryParam("max") Integer max
    );

    @GET
    @Path("/users/{id}")
    UserRepresentation getUser(
        @PathParam("realm") String realm,
        @PathParam("id") String userId
    );

    @POST
    @Path("/users")
    Response createUser(
        @PathParam("realm") String realm,
        UserRepresentation user
    );
}
```

**Configuration:**
```properties
quarkus.rest-client.keycloak-api.url=${KEYCLOAK_SERVER_URL}
quarkus.rest-client.keycloak-api.scope=javax.inject.Singleton
```

## User Management

### Fetch Users with Pagination

```java
@ApplicationScoped
public class KeycloakUserService {

    @Inject
    RealmResource realmResource;

    @ConfigProperty(name = "reconcile.page.size", defaultValue = "500")
    int pageSize;

    /**
     * Fetch all users with pagination
     */
    public Uni<List<UserRepresentation>> fetchAllUsers() {
        return Uni.createFrom().item(() -> {
            List<UserRepresentation> allUsers = new ArrayList<>();
            int first = 0;
            List<UserRepresentation> page;

            do {
                page = realmResource.users()
                    .list(first, pageSize);
                allUsers.addAll(page);
                first += pageSize;
            } while (page.size() == pageSize);

            return allUsers;
        }).runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    /**
     * Fetch users incrementally (reactive stream)
     */
    public Multi<UserRepresentation> streamUsers() {
        return Multi.createFrom().emitter(emitter -> {
            int first = 0;
            List<UserRepresentation> page;

            do {
                page = realmResource.users().list(first, pageSize);
                page.forEach(emitter::emit);
                first += pageSize;
            } while (page.size() == pageSize);

            emitter.complete();
        });
    }

    /**
     * Get user by username
     */
    public Uni<Optional<UserRepresentation>> findUserByUsername(String username) {
        return Uni.createFrom().item(() -> {
            List<UserRepresentation> users = realmResource.users()
                .search(username, true);  // exact match
            return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
        });
    }

    /**
     * Get user with roles and groups
     */
    public Uni<UserWithRoles> getUserWithRoles(String userId) {
        return Uni.createFrom().item(() -> {
            UserResource userResource = realmResource.users().get(userId);
            UserRepresentation user = userResource.toRepresentation();

            List<RoleRepresentation> realmRoles = userResource.roles().realmLevel().listEffective();
            List<GroupRepresentation> groups = userResource.groups();

            return new UserWithRoles(user, realmRoles, groups);
        });
    }
}
```

### Extract User Credentials

**Important**: Keycloak does NOT expose user passwords via API. For synchronization:
1. Use Keycloak events to capture password changes
2. Require users to reset passwords on first sync
3. Use a custom Keycloak SPI to intercept credential updates

```java
/**
 * Extract credential metadata (not actual passwords)
 */
public List<CredentialRepresentation> getUserCredentials(String userId) {
    return realmResource.users()
        .get(userId)
        .credentials();
}

/**
 * Check if user has password set
 */
public boolean hasPassword(String userId) {
    return getUserCredentials(userId).stream()
        .anyMatch(cred -> "password".equals(cred.getType()));
}
```

## Client (Service Account) Management

```java
@ApplicationScoped
public class KeycloakClientService {

    @Inject
    RealmResource realmResource;

    /**
     * Fetch all clients (service accounts)
     */
    public Uni<List<ClientRepresentation>> fetchAllClients() {
        return Uni.createFrom().item(() ->
            realmResource.clients().findAll()
        );
    }

    /**
     * Get service account user for client
     */
    public Uni<Optional<UserRepresentation>> getServiceAccountUser(String clientId) {
        return Uni.createFrom().item(() -> {
            List<ClientRepresentation> clients = realmResource.clients()
                .findByClientId(clientId);

            if (clients.isEmpty()) {
                return Optional.empty();
            }

            ClientRepresentation client = clients.get(0);
            if (!client.isServiceAccountsEnabled()) {
                return Optional.empty();
            }

            UserRepresentation serviceAccount = realmResource.clients()
                .get(client.getId())
                .getServiceAccountUser();

            return Optional.of(serviceAccount);
        });
    }

    /**
     * List all service accounts
     */
    public Uni<List<UserRepresentation>> fetchAllServiceAccounts() {
        return fetchAllClients()
            .onItem().transformToMulti(clients -> Multi.createFrom().iterable(clients))
            .onItem().transformToUniAndConcatenate(client -> {
                if (client.isServiceAccountsEnabled()) {
                    return Uni.createFrom().item(() ->
                        realmResource.clients().get(client.getId()).getServiceAccountUser()
                    );
                }
                return Uni.createFrom().nullItem();
            })
            .select().where(Objects::nonNull)
            .collect().asList();
    }
}
```

## Role and Group Management

```java
@ApplicationScoped
public class KeycloakRoleService {

    @Inject
    RealmResource realmResource;

    /**
     * Get all realm roles
     */
    public List<RoleRepresentation> getAllRealmRoles() {
        return realmResource.roles().list();
    }

    /**
     * Get user's effective roles (including composite and group roles)
     */
    public List<RoleRepresentation> getUserEffectiveRoles(String userId) {
        return realmResource.users()
            .get(userId)
            .roles()
            .realmLevel()
            .listEffective();
    }

    /**
     * Map roles to Kafka topics (example policy)
     */
    public List<String> mapRolesToTopics(List<RoleRepresentation> roles) {
        return roles.stream()
            .filter(role -> role.getName().startsWith("kafka-topic-"))
            .map(role -> role.getName().substring("kafka-topic-".length()))
            .collect(Collectors.toList());
    }

    /**
     * Get all groups
     */
    public List<GroupRepresentation> getAllGroups() {
        return realmResource.groups().groups();
    }

    /**
     * Get user's groups
     */
    public List<GroupRepresentation> getUserGroups(String userId) {
        return realmResource.users()
            .get(userId)
            .groups();
    }
}
```

## Webhook Event Handling

### Event Structure

Keycloak Admin Events contain:
```json
{
  "id": "event-uuid",
  "time": 1699999999000,
  "realmId": "master",
  "authDetails": {
    "realmId": "master",
    "clientId": "admin-cli",
    "userId": "admin-user-id"
  },
  "resourceType": "USER",
  "operationType": "CREATE",
  "resourcePath": "users/user-id",
  "representation": "{\"username\":\"alice\",...}",
  "error": null
}
```

### Webhook Endpoint

```java
@Path("/api/kc/events")
@ApplicationScoped
public class KeycloakWebhookResource {

    @Inject
    EventProcessor eventProcessor;

    @Inject
    SignatureVerifier signatureVerifier;

    @ConfigProperty(name = "keycloak.webhook.hmac.secret")
    String hmacSecret;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> handleEvent(
            @HeaderParam("X-Keycloak-Signature") String signature,
            String payload) {

        // Verify signature
        if (!signatureVerifier.verify(payload, signature, hmacSecret)) {
            return Uni.createFrom().item(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("INVALID_SIGNATURE", "Signature verification failed"))
                    .build()
            );
        }

        // Parse event
        AdminEvent event;
        try {
            event = new ObjectMapper().readValue(payload, AdminEvent.class);
        } catch (JsonProcessingException e) {
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("INVALID_PAYLOAD", "Failed to parse event"))
                    .build()
            );
        }

        // Process event asynchronously
        return eventProcessor.process(event)
            .onItem().transform(result ->
                Response.accepted()
                    .entity(new WebhookResponse("ACCEPTED", result.correlationId()))
                    .build()
            )
            .onFailure().recoverWithItem(error ->
                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("PROCESSING_FAILED", error.getMessage()))
                    .build()
            );
    }
}
```

### HMAC Signature Verification

```java
@ApplicationScoped
public class SignatureVerifier {

    /**
     * Verify HMAC-SHA256 signature
     */
    public boolean verify(String payload, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            mac.init(secretKey);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getEncoder().encodeToString(hash);

            return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            Log.error("Failed to verify signature", e);
            return false;
        }
    }
}
```

### Event Processing

```java
@ApplicationScoped
public class EventProcessor {

    @Inject
    SyncService syncService;

    @Inject
    MeterRegistry registry;

    public Uni<ProcessResult> process(AdminEvent event) {
        String correlationId = UUID.randomUUID().toString();

        // Filter relevant events
        if (!isRelevantEvent(event)) {
            return Uni.createFrom().item(
                new ProcessResult(correlationId, "IGNORED", "Event not relevant")
            );
        }

        // Route based on operation type
        return switch (event.getOperationType()) {
            case "CREATE" -> handleCreate(event, correlationId);
            case "UPDATE" -> handleUpdate(event, correlationId);
            case "DELETE" -> handleDelete(event, correlationId);
            default -> Uni.createFrom().item(
                new ProcessResult(correlationId, "UNSUPPORTED", "Operation not supported")
            );
        };
    }

    private boolean isRelevantEvent(AdminEvent event) {
        return "USER".equals(event.getResourceType()) ||
               "CLIENT".equals(event.getResourceType()) ||
               (event.getResourcePath() != null &&
                event.getResourcePath().contains("credentials"));
    }

    private Uni<ProcessResult> handleCreate(AdminEvent event, String correlationId) {
        Counter.builder("keycloak.events.processed")
            .tag("operation", "CREATE")
            .register(registry)
            .increment();

        String userId = extractUserId(event.getResourcePath());
        return syncService.syncUser(userId)
            .onItem().transform(result ->
                new ProcessResult(correlationId, "SUCCESS", "User synchronized")
            );
    }

    private Uni<ProcessResult> handleUpdate(AdminEvent event, String correlationId) {
        // Check if it's a password update
        if (event.getResourcePath().contains("reset-password") ||
            event.getResourcePath().contains("credentials")) {

            Counter.builder("keycloak.events.processed")
                .tag("operation", "PASSWORD_CHANGE")
                .register(registry)
                .increment();

            String userId = extractUserId(event.getResourcePath());
            return syncService.updateUserCredentials(userId)
                .onItem().transform(result ->
                    new ProcessResult(correlationId, "SUCCESS", "Credentials updated")
                );
        }

        return Uni.createFrom().item(
            new ProcessResult(correlationId, "IGNORED", "Update not relevant")
        );
    }

    private Uni<ProcessResult> handleDelete(AdminEvent event, String correlationId) {
        Counter.builder("keycloak.events.processed")
            .tag("operation", "DELETE")
            .register(registry)
            .increment();

        String userId = extractUserId(event.getResourcePath());
        return syncService.removeUser(userId)
            .onItem().transform(result ->
                new ProcessResult(correlationId, "SUCCESS", "User removed")
            );
    }

    private String extractUserId(String resourcePath) {
        // resourcePath format: "users/user-id" or "users/user-id/credentials"
        String[] parts = resourcePath.split("/");
        return parts.length >= 2 ? parts[1] : null;
    }
}
```

## Reconciliation Pattern

```java
@ApplicationScoped
public class ReconciliationService {

    @Inject
    KeycloakUserService keycloakUserService;

    @Inject
    KeycloakClientService keycloakClientService;

    @Inject
    KafkaCredentialService kafkaCredentialService;

    @Inject
    MetricsService metricsService;

    /**
     * Full reconciliation cycle
     */
    public Uni<ReconcileResult> reconcile() {
        Timer.Sample sample = metricsService.startTimer();

        return Uni.combine().all().unis(
            // Fetch from Keycloak
            keycloakUserService.fetchAllUsers(),
            keycloakClientService.fetchAllServiceAccounts(),
            // Fetch from Kafka
            kafkaCredentialService.describeAllCredentials()
        ).asTuple()
        .onItem().transformToUni(tuple -> {
            List<UserRepresentation> kcUsers = tuple.getItem1();
            List<UserRepresentation> kcClients = tuple.getItem2();
            Map<String, ?> kafkaCreds = tuple.getItem3();

            // Combine all Keycloak principals
            Set<String> kcPrincipals = Stream.concat(
                kcUsers.stream(),
                kcClients.stream()
            ).map(UserRepresentation::getUsername)
             .collect(Collectors.toSet());

            // Compute diff
            Set<String> toAdd = new HashSet<>(kcPrincipals);
            toAdd.removeAll(kafkaCreds.keySet());

            Set<String> toRemove = new HashSet<>(kafkaCreds.keySet());
            toRemove.removeAll(kcPrincipals);

            // Apply changes
            return applyDiff(toAdd, toRemove)
                .onItem().invoke(() ->
                    sample.stop(metricsService.reconcileDurationTimer())
                );
        });
    }

    private Uni<ReconcileResult> applyDiff(Set<String> toAdd, Set<String> toRemove) {
        return Uni.combine().all().unis(
            kafkaCredentialService.batchAddUsers(toAdd),
            kafkaCredentialService.batchRemoveUsers(toRemove)
        ).asTuple()
        .onItem().transform(tuple ->
            new ReconcileResult(tuple.getItem1(), tuple.getItem2())
        );
    }
}
```

## Error Handling

```java
public Uni<UserRepresentation> fetchUserWithRetry(String userId) {
    return Uni.createFrom().item(() ->
        realmResource.users().get(userId).toRepresentation()
    )
    .onFailure(NotFoundException.class).recoverWithNull()
    .onFailure(ProcessingException.class).retry()
        .withBackOff(Duration.ofSeconds(1), Duration.ofSeconds(10))
        .atMost(3)
    .onFailure().transform(e ->
        new SyncException("KEYCLOAK_ERROR", "Failed to fetch user: " + e.getMessage())
    );
}
```

## Best Practices

1. **Pagination**: Always paginate when fetching users/clients
2. **Service Accounts**: Grant minimal permissions (view-users, view-clients)
3. **Webhook Security**: Always verify HMAC signatures
4. **Error Handling**: Implement retry logic for transient failures
5. **Rate Limiting**: Respect Keycloak's rate limits
6. **Connection Pooling**: Reuse Keycloak client instances
7. **Monitoring**: Track API call latencies and errors
8. **Caching**: Consider caching role/group mappings
9. **Idempotency**: Ensure webhook handlers are idempotent
10. **Testing**: Use Testcontainers with Keycloak image

## Keycloak Configuration

### Enable Admin Events
```bash
# In Keycloak admin console:
Realm Settings → Events → Admin Events Settings
- Save Events: ON
- Include Representation: ON (to get full object in events)
```

### Create Service Account
```bash
# Create client with service account
Clients → Create
- Client ID: sync-agent
- Client Protocol: openid-connect
- Access Type: confidential
- Service Accounts Enabled: ON
- Authorization Enabled: OFF

# Grant permissions
Service Account Roles → Realm Roles
- Add: view-users, view-clients
```

## Resources

- Keycloak Admin REST API: https://www.keycloak.org/docs-api/latest/rest-api/
- Keycloak Admin Client: https://github.com/keycloak/keycloak/tree/main/integration/admin-client
- Event Listeners: https://www.keycloak.org/docs/latest/server_development/#_events

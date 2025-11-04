---
name: kafka-admin-expert
description: Expert guidance for Apache Kafka AdminClient operations including SCRAM credential management, ACL operations, error handling, and batch processing
---

# Kafka Admin Expert

Comprehensive guide for managing Kafka using the AdminClient API, with focus on SCRAM authentication and ACL management.

## AdminClient Setup

### Basic Configuration

```java
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.config.SaslConfigs;
import java.util.Properties;

@ApplicationScoped
public class KafkaAdminClientProducer {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "kafka.security.protocol")
    String securityProtocol;

    @ConfigProperty(name = "kafka.sasl.mechanism")
    String saslMechanism;

    @ConfigProperty(name = "kafka.sasl.jaas.config")
    String saslJaasConfig;

    private AdminClient adminClient;

    @PostConstruct
    void init() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, securityProtocol);
        props.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
        props.put(SaslConfigs.SASL_JAAS_CONFIG, saslJaasConfig);

        // Additional recommended settings
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "30000");
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "60000");
        props.put(AdminClientConfig.RETRIES_CONFIG, "3");

        this.adminClient = AdminClient.create(props);
    }

    @Produces
    @ApplicationScoped
    public AdminClient adminClient() {
        return adminClient;
    }

    @PreDestroy
    void cleanup() {
        if (adminClient != null) {
            adminClient.close(Duration.ofSeconds(10));
        }
    }
}
```

### Configuration Properties Example

```properties
kafka.bootstrap.servers=kafka:9093
kafka.security.protocol=SASL_SSL
kafka.sasl.mechanism=SCRAM-SHA-512
kafka.sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username="admin" password="admin-secret";

# For SSL
kafka.ssl.truststore.location=/certs/truststore.jks
kafka.ssl.truststore.password=changeit
```

## SCRAM Credential Management

### Understanding SCRAM Credentials

SCRAM (Salted Challenge Response Authentication Mechanism) stores credentials as:
- **Salt**: Random bytes for password hashing
- **Iterations**: Number of hash iterations (default: 4096)
- **StoredKey**: Derived from salted password hash
- **ServerKey**: Server authentication key

### Upsert SCRAM Credentials

```java
import org.apache.kafka.clients.admin.UserScramCredentialUpsertion;
import org.apache.kafka.clients.admin.AlterUserScramCredentialsOptions;
import org.apache.kafka.clients.admin.ScramCredentialInfo;
import org.apache.kafka.common.security.scram.ScramMechanism;

@ApplicationScoped
public class ScramCredentialService {

    @Inject
    AdminClient adminClient;

    /**
     * Upsert SCRAM credential for a user
     */
    public Uni<Void> upsertScramCredential(String username,
                                           String password,
                                           ScramMechanism mechanism) {
        return Uni.createFrom().future(() -> {
            // Create upsertion
            UserScramCredentialUpsertion upsertion = new UserScramCredentialUpsertion(
                username,
                new ScramCredentialInfo(mechanism, 4096),
                password.getBytes(StandardCharsets.UTF_8)
            );

            AlterUserScramCredentialsOptions options = new AlterUserScramCredentialsOptions()
                .timeoutMs(30000);

            // Execute operation
            return adminClient
                .alterUserScramCredentials(Collections.singletonList(upsertion), options)
                .all()
                .toCompletionStage()
                .toCompletableFuture();
        });
    }

    /**
     * Batch upsert multiple credentials
     */
    public Uni<Map<String, Throwable>> batchUpsertCredentials(
            Map<String, String> userPasswords,
            ScramMechanism mechanism) {

        List<UserScramCredentialAlteration> alterations = userPasswords.entrySet()
            .stream()
            .map(entry -> new UserScramCredentialUpsertion(
                entry.getKey(),
                new ScramCredentialInfo(mechanism, 4096),
                entry.getValue().getBytes(StandardCharsets.UTF_8)
            ))
            .collect(Collectors.toList());

        return Uni.createFrom().future(() -> {
            AlterUserScramCredentialsResult result = adminClient
                .alterUserScramCredentials(alterations);

            // Get results per user
            Map<String, KafkaFuture<Void>> futures = result.values();
            Map<String, Throwable> errors = new HashMap<>();

            for (Map.Entry<String, KafkaFuture<Void>> entry : futures.entrySet()) {
                try {
                    entry.getValue().get();
                } catch (Exception e) {
                    errors.put(entry.getKey(), e.getCause());
                }
            }

            return CompletableFuture.completedFuture(errors);
        });
    }

    /**
     * Support multiple mechanisms per user
     */
    public Uni<Void> upsertMultipleMechanisms(String username,
                                              String password,
                                              List<ScramMechanism> mechanisms) {
        List<UserScramCredentialUpsertion> upsertions = mechanisms.stream()
            .map(mechanism -> new UserScramCredentialUpsertion(
                username,
                new ScramCredentialInfo(mechanism, 4096),
                password.getBytes(StandardCharsets.UTF_8)
            ))
            .collect(Collectors.toList());

        return Uni.createFrom().future(() ->
            adminClient.alterUserScramCredentials(upsertions)
                .all()
                .toCompletionStage()
                .toCompletableFuture()
        );
    }
}
```

### Delete SCRAM Credentials

```java
import org.apache.kafka.clients.admin.UserScramCredentialDeletion;

public Uni<Void> deleteScramCredential(String username, ScramMechanism mechanism) {
    UserScramCredentialDeletion deletion = new UserScramCredentialDeletion(
        username,
        mechanism
    );

    return Uni.createFrom().future(() ->
        adminClient.alterUserScramCredentials(Collections.singletonList(deletion))
            .all()
            .toCompletionStage()
            .toCompletableFuture()
    );
}

public Uni<Void> deleteAllUserCredentials(String username) {
    return describeUserCredentials(username)
        .onItem().transformToUni(credentials -> {
            List<UserScramCredentialDeletion> deletions = credentials.stream()
                .map(cred -> new UserScramCredentialDeletion(username, cred.mechanism()))
                .collect(Collectors.toList());

            return Uni.createFrom().future(() ->
                adminClient.alterUserScramCredentials(deletions)
                    .all()
                    .toCompletionStage()
                    .toCompletableFuture()
            );
        });
}
```

### Describe SCRAM Credentials

```java
import org.apache.kafka.clients.admin.DescribeUserScramCredentialsOptions;
import org.apache.kafka.clients.admin.UserScramCredentialsDescription;

public Uni<List<ScramCredentialInfo>> describeUserCredentials(String username) {
    return Uni.createFrom().future(() -> {
        DescribeUserScramCredentialsOptions options =
            new DescribeUserScramCredentialsOptions()
                .users(Collections.singletonList(username));

        return adminClient.describeUserScramCredentials(Collections.singletonList(username))
            .description(username)
            .toCompletionStage()
            .toCompletableFuture();
    }).onItem().transform(description ->
        description.credentialInfos()
            .stream()
            .collect(Collectors.toList())
    );
}

public Uni<Map<String, List<ScramCredentialInfo>>> describeAllCredentials() {
    return Uni.createFrom().future(() ->
        adminClient.describeUserScramCredentials()
            .all()
            .toCompletionStage()
            .toCompletableFuture()
    ).onItem().transform(descriptions ->
        descriptions.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> new ArrayList<>(entry.getValue().credentialInfos())
            ))
    );
}
```

## ACL Management

### Understanding Kafka ACLs

ACLs in Kafka define permissions using:
- **Principal**: User identity (e.g., `User:alice`)
- **Resource**: Topic, Group, Cluster, etc.
- **Operation**: Read, Write, Create, Delete, Describe, etc.
- **Permission Type**: Allow or Deny
- **Host**: IP address (usually `*` for any)

### Create ACLs

```java
import org.apache.kafka.common.acl.*;
import org.apache.kafka.common.resource.*;

@ApplicationScoped
public class AclService {

    @Inject
    AdminClient adminClient;

    /**
     * Grant topic read permission to user
     */
    public Uni<Void> grantTopicRead(String username, String topicPattern) {
        ResourcePattern resource = new ResourcePattern(
            ResourceType.TOPIC,
            topicPattern,
            PatternType.LITERAL  // or PREFIXED for pattern matching
        );

        AccessControlEntry ace = new AccessControlEntry(
            "User:" + username,
            "*",  // any host
            AclOperation.READ,
            AclPermissionType.ALLOW
        );

        AclBinding aclBinding = new AclBinding(resource, ace);

        return Uni.createFrom().future(() ->
            adminClient.createAcls(Collections.singletonList(aclBinding))
                .all()
                .toCompletionStage()
                .toCompletableFuture()
        );
    }

    /**
     * Grant comprehensive access to topic
     */
    public Uni<Void> grantTopicFullAccess(String username, String topicName) {
        ResourcePattern resource = new ResourcePattern(
            ResourceType.TOPIC,
            topicName,
            PatternType.LITERAL
        );

        List<AclBinding> bindings = Arrays.asList(
            AclOperation.READ,
            AclOperation.WRITE,
            AclOperation.DESCRIBE,
            AclOperation.CREATE,
            AclOperation.DELETE
        ).stream()
        .map(op -> new AclBinding(
            resource,
            new AccessControlEntry("User:" + username, "*", op, AclPermissionType.ALLOW)
        ))
        .collect(Collectors.toList());

        return Uni.createFrom().future(() ->
            adminClient.createAcls(bindings)
                .all()
                .toCompletionStage()
                .toCompletableFuture()
        );
    }

    /**
     * Grant consumer group access
     */
    public Uni<Void> grantConsumerGroupAccess(String username, String groupId) {
        ResourcePattern resource = new ResourcePattern(
            ResourceType.GROUP,
            groupId,
            PatternType.LITERAL
        );

        AccessControlEntry ace = new AccessControlEntry(
            "User:" + username,
            "*",
            AclOperation.READ,
            AclPermissionType.ALLOW
        );

        return Uni.createFrom().future(() ->
            adminClient.createAcls(Collections.singletonList(new AclBinding(resource, ace)))
                .all()
                .toCompletionStage()
                .toCompletableFuture()
        );
    }

    /**
     * Batch create ACLs
     */
    public Uni<Map<AclBinding, Throwable>> batchCreateAcls(List<AclBinding> bindings) {
        return Uni.createFrom().future(() -> {
            CreateAclsResult result = adminClient.createAcls(bindings);

            Map<AclBinding, Throwable> errors = new HashMap<>();
            for (int i = 0; i < bindings.size(); i++) {
                try {
                    result.values().get(i).get();
                } catch (Exception e) {
                    errors.put(bindings.get(i), e.getCause());
                }
            }

            return CompletableFuture.completedFuture(errors);
        });
    }
}
```

### Delete ACLs

```java
public Uni<Integer> deleteAclsForUser(String username) {
    AclBindingFilter filter = new AclBindingFilter(
        ResourcePatternFilter.ANY,
        new AccessControlEntryFilter(
            "User:" + username,
            null,
            AclOperation.ANY,
            AclPermissionType.ANY
        )
    );

    return Uni.createFrom().future(() ->
        adminClient.deleteAcls(Collections.singletonList(filter))
            .all()
            .toCompletionStage()
            .toCompletableFuture()
    ).onItem().transform(results ->
        results.stream()
            .mapToInt(result -> result.values().size())
            .sum()
    );
}

public Uni<Void> deleteTopicAcls(String username, String topicName) {
    AclBindingFilter filter = new AclBindingFilter(
        new ResourcePatternFilter(ResourceType.TOPIC, topicName, PatternType.LITERAL),
        new AccessControlEntryFilter("User:" + username, null, AclOperation.ANY, AclPermissionType.ANY)
    );

    return Uni.createFrom().future(() ->
        adminClient.deleteAcls(Collections.singletonList(filter))
            .all()
            .toCompletionStage()
            .toCompletableFuture()
    ).replaceWithVoid();
}
```

### Describe ACLs

```java
public Uni<List<AclBinding>> describeAclsForUser(String username) {
    AclBindingFilter filter = new AclBindingFilter(
        ResourcePatternFilter.ANY,
        new AccessControlEntryFilter(
            "User:" + username,
            null,
            AclOperation.ANY,
            AclPermissionType.ANY
        )
    );

    return Uni.createFrom().future(() ->
        adminClient.describeAcls(filter)
            .values()
            .toCompletionStage()
            .toCompletableFuture()
    ).onItem().transform(Collection::stream)
      .onItem().transform(stream -> stream.collect(Collectors.toList()));
}
```

## Error Handling

### Common Exceptions

```java
public Uni<Void> handleKafkaErrors(Uni<Void> operation) {
    return operation
        .onFailure(TimeoutException.class).retry()
            .withBackOff(Duration.ofSeconds(1), Duration.ofSeconds(10))
            .atMost(3)
        .onFailure(InvalidRequestException.class).transform(e ->
            new SyncException("INVALID_REQUEST", "Invalid Kafka request: " + e.getMessage())
        )
        .onFailure(TopicAuthorizationException.class).transform(e ->
            new SyncException("AUTHORIZATION_FAILED", "Insufficient permissions: " + e.getMessage())
        )
        .onFailure(UnsupportedSaslMechanismException.class).transform(e ->
            new SyncException("UNSUPPORTED_MECHANISM", "SASL mechanism not supported: " + e.getMessage())
        );
}
```

### Validation

```java
public void validateScramMechanism(ScramMechanism mechanism) {
    if (!Arrays.asList(ScramMechanism.SCRAM_SHA_256, ScramMechanism.SCRAM_SHA_512)
            .contains(mechanism)) {
        throw new IllegalArgumentException("Unsupported SCRAM mechanism: " + mechanism);
    }
}

public void validateUsername(String username) {
    if (username == null || username.isBlank()) {
        throw new IllegalArgumentException("Username cannot be empty");
    }
    if (username.length() > 255) {
        throw new IllegalArgumentException("Username too long (max 255 characters)");
    }
}
```

## Advanced Patterns

### Reconciliation Pattern

```java
public Uni<ReconcileResult> reconcileCredentials(List<String> keycloakUsers) {
    return describeAllCredentials()
        .onItem().transformToUni(kafkaUsers -> {
            // Compute diff
            Set<String> toAdd = new HashSet<>(keycloakUsers);
            toAdd.removeAll(kafkaUsers.keySet());

            Set<String> toRemove = new HashSet<>(kafkaUsers.keySet());
            toRemove.removeAll(keycloakUsers);

            // Apply changes
            return Uni.combine().all().unis(
                addUsers(toAdd),
                removeUsers(toRemove)
            ).asTuple()
            .onItem().transform(tuple ->
                new ReconcileResult(tuple.getItem1(), tuple.getItem2())
            );
        });
}
```

### Monitoring Operations

```java
@Inject
MeterRegistry registry;

private Timer.Sample startTimer() {
    return Timer.start(registry);
}

public Uni<Void> monitoredUpsert(String username, String password) {
    Timer.Sample sample = startTimer();

    return upsertScramCredential(username, password, ScramMechanism.SCRAM_SHA_512)
        .onItem().invoke(() -> {
            sample.stop(Timer.builder("kafka.admin.operation.duration")
                .tag("operation", "upsert")
                .tag("result", "success")
                .register(registry));

            Counter.builder("kafka.admin.operation.total")
                .tag("operation", "upsert")
                .tag("result", "success")
                .register(registry)
                .increment();
        })
        .onFailure().invoke(error -> {
            sample.stop(Timer.builder("kafka.admin.operation.duration")
                .tag("operation", "upsert")
                .tag("result", "error")
                .register(registry));

            Counter.builder("kafka.admin.operation.total")
                .tag("operation", "upsert")
                .tag("result", "error")
                .register(registry)
                .increment();
        });
}
```

## Best Practices

1. **Connection Management**: Create AdminClient once at startup, reuse throughout application
2. **Batch Operations**: Group multiple operations to reduce network roundtrips
3. **Error Handling**: Implement retry logic with exponential backoff
4. **Timeouts**: Set appropriate timeouts for operations (default 60s may be too long)
5. **Monitoring**: Track all operations with metrics
6. **Security**: Store credentials securely, use environment variables
7. **Validation**: Validate inputs before calling Kafka APIs
8. **Idempotency**: Ensure operations are idempotent (safe to retry)
9. **Logging**: Log correlation IDs for traceability
10. **Testing**: Use Testcontainers for integration tests

## Resources

- Apache Kafka AdminClient API: https://kafka.apache.org/documentation/#adminapi
- SCRAM Authentication: https://kafka.apache.org/documentation/#security_sasl_scram
- ACL Documentation: https://kafka.apache.org/documentation/#security_authz

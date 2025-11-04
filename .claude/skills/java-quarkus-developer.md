---
name: java-quarkus-developer
description: Expert guidance for Quarkus framework development including REST APIs, dependency injection, reactive programming, observability, and native compilation
---

# Java Quarkus Developer

Expert knowledge for building production-ready Quarkus applications with focus on microservices, REST APIs, and cloud-native patterns.

## Core Quarkus Concepts

### Dependency Injection (CDI)

**Application Scopes:**
```java
@ApplicationScoped  // Single instance per application
@RequestScoped      // New instance per HTTP request
@Dependent          // New instance per injection point
@Singleton          // Eager initialization, thread-safe

// Example service
@ApplicationScoped
public class SyncService {
    @Inject
    KafkaAdminClient kafkaClient;

    @Inject
    KeycloakClient keycloakClient;
}
```

**Constructor Injection (Preferred):**
```java
@ApplicationScoped
public class ReconciliationEngine {
    private final SyncService syncService;
    private final MetricsService metricsService;

    @Inject
    public ReconciliationEngine(SyncService syncService,
                                MetricsService metricsService) {
        this.syncService = syncService;
        this.metricsService = metricsService;
    }
}
```

### Configuration Management

**MicroProfile Config:**
```java
@ConfigProperty(name = "kafka.bootstrap.servers")
String bootstrapServers;

@ConfigProperty(name = "reconcile.interval.seconds", defaultValue = "120")
int reconcileIntervalSeconds;

// Optional values
@ConfigProperty(name = "kafka.sasl.username")
Optional<String> saslUsername;

// Inject entire config group
@Inject
@ConfigProperty(name = "kafka")
Map<String, String> kafkaConfig;
```

**application.properties patterns:**
```properties
# Kafka configuration
kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
kafka.security.protocol=${KAFKA_SECURITY_PROTOCOL:PLAINTEXT}
kafka.sasl.mechanism=${KAFKA_SASL_MECHANISM:SCRAM-SHA-512}

# Quarkus datasource (SQLite)
quarkus.datasource.db-kind=other
quarkus.datasource.jdbc.driver=org.sqlite.JDBC
quarkus.datasource.jdbc.url=jdbc:sqlite:/data/sync.db

# HTTP server
quarkus.http.port=8088
quarkus.http.cors=true

# Logging
quarkus.log.level=INFO
quarkus.log.category."com.yourcompany".level=DEBUG
```

### REST Endpoints (RESTEasy Reactive)

**Basic REST Controller:**
```java
@Path("/api")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SyncApiResource {

    @Inject
    SyncService syncService;

    @GET
    @Path("/summary")
    public Uni<SummaryResponse> getSummary() {
        return Uni.createFrom().item(syncService.computeSummary());
    }

    @GET
    @Path("/operations")
    public Uni<Page<Operation>> getOperations(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("principal") String principal) {
        return syncService.getOperations(page, size, principal);
    }

    @POST
    @Path("/reconcile")
    public Uni<ReconcileResponse> triggerReconcile() {
        return syncService.reconcile()
            .onItem().transform(result -> new ReconcileResponse(result));
    }
}
```

**Exception Handling:**
```java
// Custom exception mapper
@Provider
public class SyncExceptionMapper implements ExceptionMapper<SyncException> {

    @Override
    public Response toResponse(SyncException exception) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(new ErrorResponse(exception.getCode(), exception.getMessage()))
            .build();
    }
}

// Business exception
public class SyncException extends RuntimeException {
    private final String code;

    public SyncException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
```

### Reactive Programming

**Mutiny Patterns:**
```java
// Uni - single async result
Uni<User> fetchUser(String userId) {
    return Uni.createFrom().item(() -> repository.findById(userId))
        .onItem().ifNull().failWith(new NotFoundException())
        .onFailure().retry().atMost(3);
}

// Multi - stream of items
Multi<Operation> streamOperations() {
    return Multi.createFrom().items(repository.findAll().stream())
        .onItem().transform(this::mapToDto);
}

// Combining Unis
Uni<ReconcileResult> reconcile() {
    return Uni.combine().all().unis(
        fetchKeycloakUsers(),
        fetchKafkaPrincipals()
    ).asTuple()
    .onItem().transformToUni(tuple -> {
        List<User> kcUsers = tuple.getItem1();
        List<String> kafkaPrincipals = tuple.getItem2();
        return applyDiff(kcUsers, kafkaPrincipals);
    });
}
```

**Async Execution:**
```java
@Inject
ManagedExecutor executor;

Uni<Result> asyncOperation() {
    return Uni.createFrom().item(() -> heavyComputation())
        .runSubscriptionOn(executor);
}
```

### Scheduling

**Periodic Tasks:**
```java
@ApplicationScoped
public class ReconciliationScheduler {

    @Inject
    ReconciliationEngine engine;

    // Every 2 minutes
    @Scheduled(every = "120s")
    void periodicReconcile() {
        engine.reconcile()
            .subscribe().with(
                result -> Log.info("Reconcile completed: " + result),
                failure -> Log.error("Reconcile failed", failure)
            );
    }

    // Cron expression
    @Scheduled(cron = "0 */5 * * * ?")
    void purgeOldRecords() {
        engine.purgeOldRecords();
    }
}
```

### Health Checks

**SmallRye Health:**
```java
@Liveness
public class KafkaHealthCheck implements HealthCheck {

    @Inject
    KafkaAdminClient kafkaClient;

    @Override
    public HealthCheckResponse call() {
        try {
            kafkaClient.describeCluster().clusterId().get(5, TimeUnit.SECONDS);
            return HealthCheckResponse.up("kafka");
        } catch (Exception e) {
            return HealthCheckResponse.down("kafka");
        }
    }
}

@Readiness
public class KeycloakHealthCheck implements HealthCheck {

    @Inject
    KeycloakClient keycloakClient;

    @Override
    public HealthCheckResponse call() {
        try {
            keycloakClient.ping();
            return HealthCheckResponse.up("keycloak");
        } catch (Exception e) {
            return HealthCheckResponse.down("keycloak");
        }
    }
}
```

### Observability (Micrometer)

**Metrics:**
```java
@ApplicationScoped
public class MetricsService {

    @Inject
    MeterRegistry registry;

    private Counter syncSuccessCounter;
    private Counter syncErrorCounter;
    private Timer reconcileDuration;
    private Gauge dbSizeGauge;

    @PostConstruct
    void init() {
        syncSuccessCounter = Counter.builder("sync.operations.total")
            .tag("result", "success")
            .description("Total successful sync operations")
            .register(registry);

        syncErrorCounter = Counter.builder("sync.operations.total")
            .tag("result", "error")
            .register(registry);

        reconcileDuration = Timer.builder("sync.reconcile.duration")
            .description("Reconciliation cycle duration")
            .register(registry);

        // Gauge with lambda
        dbSizeGauge = Gauge.builder("sync.db.size.bytes", this::getDbSize)
            .register(registry);
    }

    public void recordSuccess(String opType) {
        syncSuccessCounter.increment();
    }

    public void recordReconcile(Runnable operation) {
        reconcileDuration.record(operation);
    }

    private long getDbSize() {
        // Compute database size
        return 0L;
    }
}

// Or use annotations
@Timed(value = "sync.reconcile.duration", description = "Reconciliation time")
@Counted(value = "sync.reconcile.calls")
public ReconcileResult reconcile() {
    // implementation
}
```

### Database Access (JDBC)

**Simple JDBC with Connection Pool:**
```java
@ApplicationScoped
public class OperationRepository {

    @Inject
    AgroalDataSource dataSource;

    public List<Operation> findAll(int limit, int offset) {
        String sql = "SELECT * FROM sync_operation ORDER BY occurred_at DESC LIMIT ? OFFSET ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Operation> operations = new ArrayList<>();
                while (rs.next()) {
                    operations.add(mapRow(rs));
                }
                return operations;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch operations", e);
        }
    }

    @Transactional
    public void insert(Operation operation) {
        String sql = "INSERT INTO sync_operation (correlation_id, occurred_at, realm, " +
                    "cluster_id, principal, op_type, result, duration_ms) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, operation.correlationId());
            stmt.setTimestamp(2, Timestamp.from(operation.occurredAt()));
            stmt.setString(3, operation.realm());
            stmt.setString(4, operation.clusterId());
            stmt.setString(5, operation.principal());
            stmt.setString(6, operation.opType());
            stmt.setString(7, operation.result());
            stmt.setInt(8, operation.durationMs());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert operation", e);
        }
    }

    private Operation mapRow(ResultSet rs) throws SQLException {
        return new Operation(
            rs.getLong("id"),
            rs.getString("correlation_id"),
            rs.getTimestamp("occurred_at").toInstant(),
            rs.getString("realm"),
            rs.getString("cluster_id"),
            rs.getString("principal"),
            rs.getString("op_type"),
            rs.getString("result"),
            rs.getInt("duration_ms")
        );
    }
}
```

### OpenAPI Documentation

**Auto-generated with annotations:**
```java
@Path("/api/operations")
@Tag(name = "Operations", description = "Sync operation history")
public class OperationsResource {

    @GET
    @Operation(summary = "List operations",
               description = "Returns paginated list of sync operations")
    @APIResponse(responseCode = "200",
                 description = "Success",
                 content = @Content(schema = @Schema(implementation = Page.class)))
    public Page<Operation> list(
            @Parameter(description = "Page number") @QueryParam("page") int page,
            @Parameter(description = "Page size") @QueryParam("size") int size) {
        // implementation
    }
}
```

### Testing Patterns

**JUnit + RestAssured:**
```java
@QuarkusTest
class SyncApiResourceTest {

    @Test
    void testGetSummary() {
        given()
            .when().get("/api/summary")
            .then()
            .statusCode(200)
            .body("opsPerHour", greaterThan(0));
    }

    @Test
    void testTriggerReconcile() {
        given()
            .when().post("/api/reconcile")
            .then()
            .statusCode(200)
            .body("status", equalTo("COMPLETED"));
    }
}

@QuarkusTest
@TestProfile(MockProfile.class)
class ReconciliationEngineTest {

    @Inject
    ReconciliationEngine engine;

    @InjectMock
    KafkaAdminClient kafkaClient;

    @Test
    void testReconcile() {
        Mockito.when(kafkaClient.describeUserScramCredentials())
            .thenReturn(mockResult());

        ReconcileResult result = engine.reconcile().await().indefinitely();

        assertThat(result.itemsProcessed()).isEqualTo(10);
    }
}
```

## Best Practices

### 1. Lifecycle Management
```java
@ApplicationScoped
public class KafkaAdminClientProducer {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    private AdminClient adminClient;

    @PostConstruct
    void init() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        this.adminClient = AdminClient.create(props);
    }

    @Produces
    @ApplicationScoped
    AdminClient adminClient() {
        return adminClient;
    }

    @PreDestroy
    void cleanup() {
        if (adminClient != null) {
            adminClient.close();
        }
    }
}
```

### 2. Error Handling
- Use custom exceptions with error codes
- Implement exception mappers for consistent API responses
- Log errors with correlation IDs
- Implement retry logic with exponential backoff

### 3. Performance
- Use reactive programming for I/O operations
- Implement connection pooling
- Use batch operations when possible
- Monitor with metrics and set up alerts

### 4. Native Compilation
```bash
# Build native image
./mvnw package -Pnative

# Native image considerations:
# - Register reflection for runtime-accessed classes
# - Configure resource includes in application.properties
# - Test native image thoroughly before deployment
```

## Common Patterns

### Retry Logic
```java
public Uni<Result> operationWithRetry() {
    return Uni.createFrom().item(this::doOperation)
        .onFailure().retry()
            .withBackOff(Duration.ofSeconds(1), Duration.ofSeconds(10))
            .atMost(3);
}
```

### Bulk Operations
```java
public Uni<BulkResult> bulkSync(List<User> users) {
    return Multi.createFrom().iterable(users)
        .onItem().transformToUniAndConcatenate(this::syncUser)
        .collect().asList()
        .onItem().transform(BulkResult::from);
}
```

### Configuration Validation
```java
@Startup
@ApplicationScoped
public class ConfigValidator {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @PostConstruct
    void validate() {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalStateException("kafka.bootstrap.servers is required");
        }
    }
}
```

## Resources

- Quarkus Guides: https://quarkus.io/guides/
- Mutiny Reference: https://smallrye.io/smallrye-mutiny/
- MicroProfile Config: https://quarkus.io/guides/config-reference
- SmallRye Health: https://quarkus.io/guides/smallrye-health

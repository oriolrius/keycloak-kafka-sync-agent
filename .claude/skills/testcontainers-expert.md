---
name: testcontainers-expert
description: Expert guidance for integration testing with Testcontainers including Kafka, Keycloak, PostgreSQL setup, lifecycle management, and performance testing patterns
---

# Testcontainers Expert

Comprehensive guide for integration testing using Testcontainers with focus on Kafka, Keycloak, and database testing.

## Setup

### Dependencies (Maven)

```xml
<dependencies>
    <!-- Testcontainers core -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers</artifactId>
        <version>1.19.3</version>
        <scope>test</scope>
    </dependency>

    <!-- Testcontainers JUnit 5 -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>1.19.3</version>
        <scope>test</scope>
    </dependency>

    <!-- Kafka container -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>kafka</artifactId>
        <version>1.19.3</version>
        <scope>test</scope>
    </dependency>

    <!-- PostgreSQL container (for Keycloak) -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <version>1.19.3</version>
        <scope>test</scope>
    </dependency>

    <!-- Quarkus test -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-junit5</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- REST Assured -->
    <dependency>
        <groupId>io.rest-assured</groupId>
        <artifactId>rest-assured</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Kafka Container

### Basic Kafka Setup

```java
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaIntegrationTest {

    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("apache/kafka:3.7.0")
    );

    @BeforeAll
    static void startContainers() {
        kafka.start();
    }

    @AfterAll
    static void stopContainers() {
        kafka.stop();
    }

    @Test
    void testKafkaConnection() {
        String bootstrapServers = kafka.getBootstrapServers();
        assertThat(bootstrapServers).isNotEmpty();
    }
}
```

### Kafka with SASL/SCRAM

```java
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaScramIntegrationTest {

    static KafkaContainer kafka;

    @BeforeAll
    static void startKafka() throws Exception {
        kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"))
            .withEnv("KAFKA_SASL_ENABLED_MECHANISMS", "SCRAM-SHA-512")
            .withEnv("KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL", "SCRAM-SHA-512")
            .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
                "PLAINTEXT:PLAINTEXT,BROKER:PLAINTEXT,SASL_PLAINTEXT:SASL_PLAINTEXT")
            .withEnv("KAFKA_LISTENERS",
                "PLAINTEXT://0.0.0.0:9092,BROKER://0.0.0.0:9093,SASL_PLAINTEXT://0.0.0.0:9094")
            .withEnv("KAFKA_ADVERTISED_LISTENERS",
                "PLAINTEXT://localhost:9092,BROKER://kafka:9093,SASL_PLAINTEXT://localhost:9094");

        kafka.start();

        // Create SCRAM credentials
        kafka.execInContainer(
            "kafka-configs", "--bootstrap-server", "localhost:9092",
            "--alter", "--add-config", "SCRAM-SHA-512=[password=admin-secret]",
            "--entity-type", "users", "--entity-name", "admin"
        );
    }

    @Test
    void testScramAuthentication() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
        props.put(SaslConfigs.SASL_MECHANISM, "SCRAM-SHA-512");
        props.put(SaslConfigs.SASL_JAAS_CONFIG,
            "org.apache.kafka.common.security.scram.ScramLoginModule required " +
            "username=\"admin\" password=\"admin-secret\";");

        try (AdminClient admin = AdminClient.create(props)) {
            String clusterId = admin.describeCluster()
                .clusterId()
                .get(10, TimeUnit.SECONDS);
            assertThat(clusterId).isNotNull();
        }
    }

    @AfterAll
    static void stopKafka() {
        kafka.stop();
    }
}
```

## Keycloak Container

### Basic Keycloak Setup

```java
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KeycloakIntegrationTest {

    static GenericContainer<?> keycloak;

    @BeforeAll
    static void startKeycloak() {
        keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:23.0")
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withEnv("KC_HTTP_ENABLED", "true")
            .withEnv("KC_HOSTNAME_STRICT", "false")
            .withCommand("start-dev")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/health/ready")
                .forPort(8080)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(2)));

        keycloak.start();
    }

    @Test
    void testKeycloakConnection() {
        String baseUrl = "http://" + keycloak.getHost() + ":" + keycloak.getFirstMappedPort();

        Keycloak client = KeycloakBuilder.builder()
            .serverUrl(baseUrl)
            .realm("master")
            .grantType("password")
            .clientId("admin-cli")
            .username("admin")
            .password("admin")
            .build();

        RealmResource realm = client.realm("master");
        assertThat(realm).isNotNull();
    }

    @AfterAll
    static void stopKeycloak() {
        keycloak.stop();
    }
}
```

### Keycloak with PostgreSQL

```java
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.Network;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KeycloakWithPostgresTest {

    static Network network = Network.newNetwork();
    static PostgreSQLContainer<?> postgres;
    static GenericContainer<?> keycloak;

    @BeforeAll
    static void startContainers() {
        // Start PostgreSQL
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("keycloak")
            .withUsername("keycloak")
            .withPassword("keycloak")
            .withNetwork(network)
            .withNetworkAliases("postgres");
        postgres.start();

        // Start Keycloak with PostgreSQL
        keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:23.0")
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withEnv("KC_DB", "postgres")
            .withEnv("KC_DB_URL", "jdbc:postgresql://postgres:5432/keycloak")
            .withEnv("KC_DB_USERNAME", "keycloak")
            .withEnv("KC_DB_PASSWORD", "keycloak")
            .withEnv("KC_HTTP_ENABLED", "true")
            .withEnv("KC_HOSTNAME_STRICT", "false")
            .withCommand("start")
            .withExposedPorts(8080)
            .withNetwork(network)
            .dependsOn(postgres)
            .waitingFor(Wait.forHttp("/health/ready")
                .forPort(8080)
                .withStartupTimeout(Duration.ofMinutes(3)));

        keycloak.start();
    }

    @AfterAll
    static void stopContainers() {
        keycloak.stop();
        postgres.stop();
        network.close();
    }
}
```

## Complete Test Environment

### Quarkus Test Resource

```java
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.Network;
import java.util.Map;

public class SyncAgentTestResource implements QuarkusTestResourceLifecycleManager {

    private static final Network network = Network.newNetwork();
    private static KafkaContainer kafka;
    private static PostgreSQLContainer<?> postgres;
    private static GenericContainer<?> keycloak;

    @Override
    public Map<String, String> start() {
        // Start Kafka
        kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"))
            .withNetwork(network)
            .withNetworkAliases("kafka");
        kafka.start();

        // Start PostgreSQL
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("keycloak")
            .withUsername("keycloak")
            .withPassword("keycloak")
            .withNetwork(network)
            .withNetworkAliases("postgres");
        postgres.start();

        // Start Keycloak
        keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:23.0")
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withEnv("KC_DB", "postgres")
            .withEnv("KC_DB_URL", "jdbc:postgresql://postgres:5432/keycloak")
            .withEnv("KC_DB_USERNAME", "keycloak")
            .withEnv("KC_DB_PASSWORD", "keycloak")
            .withEnv("KC_HTTP_ENABLED", "true")
            .withEnv("KC_HOSTNAME_STRICT", "false")
            .withCommand("start")
            .withExposedPorts(8080)
            .withNetwork(network)
            .dependsOn(postgres)
            .waitingFor(Wait.forHttp("/health/ready").forPort(8080));
        keycloak.start();

        // Setup test data
        setupTestData();

        // Return configuration for Quarkus
        return Map.of(
            "kafka.bootstrap.servers", kafka.getBootstrapServers(),
            "keycloak.server.url",
                "http://" + keycloak.getHost() + ":" + keycloak.getFirstMappedPort(),
            "keycloak.realm", "master",
            "keycloak.client.id", "sync-agent",
            "keycloak.client.secret", "test-secret"
        );
    }

    @Override
    public void stop() {
        if (keycloak != null) keycloak.stop();
        if (postgres != null) postgres.stop();
        if (kafka != null) kafka.stop();
        network.close();
    }

    private void setupTestData() {
        // Create test users in Keycloak
        String baseUrl = "http://" + keycloak.getHost() + ":" + keycloak.getFirstMappedPort();

        Keycloak client = KeycloakBuilder.builder()
            .serverUrl(baseUrl)
            .realm("master")
            .grantType("password")
            .clientId("admin-cli")
            .username("admin")
            .password("admin")
            .build();

        // Create test user
        UserRepresentation user = new UserRepresentation();
        user.setUsername("alice");
        user.setEnabled(true);
        user.setCredentials(Collections.singletonList(
            createPasswordCredential("alice-password")
        ));

        client.realm("master").users().create(user);
    }

    private CredentialRepresentation createPasswordCredential(String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        return credential;
    }
}
```

### Using Test Resource

```java
@QuarkusTest
@QuarkusTestResource(SyncAgentTestResource.class)
class SyncAgentE2ETest {

    @Inject
    SyncService syncService;

    @Test
    void testFullSync() {
        ReconcileResult result = syncService.reconcile()
            .await().atMost(Duration.ofMinutes(1));

        assertThat(result.itemsProcessed()).isGreaterThan(0);
        assertThat(result.itemsSuccess()).isEqualTo(result.itemsProcessed());
    }

    @Test
    void testUserSync() {
        // Verify user exists in Keycloak
        // Trigger sync
        // Verify SCRAM credentials in Kafka
    }
}
```

## Advanced Patterns

### Shared Containers (Singleton)

```java
import org.testcontainers.containers.GenericContainer;

public abstract class AbstractIntegrationTest {

    protected static final KafkaContainer KAFKA;
    protected static final GenericContainer<?> KEYCLOAK;

    static {
        KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));
        KAFKA.start();

        KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:23.0")
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withCommand("start-dev")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/health/ready").forPort(8080));
        KEYCLOAK.start();
    }

    @AfterAll
    static void stopSharedContainers() {
        // Containers stopped automatically by Ryuk
    }
}

// Use in tests
@QuarkusTest
class TestA extends AbstractIntegrationTest {
    // Uses shared containers
}

@QuarkusTest
class TestB extends AbstractIntegrationTest {
    // Uses same shared containers
}
```

### Custom Container Configuration

```java
public class CustomKafkaContainer extends KafkaContainer {

    public CustomKafkaContainer() {
        super(DockerImageName.parse("apache/kafka:3.7.0"));
        withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
        withEnv("KAFKA_NUM_PARTITIONS", "3");
    }

    public CustomKafkaContainer withScram() {
        withEnv("KAFKA_SASL_ENABLED_MECHANISMS", "SCRAM-SHA-512");
        return this;
    }

    public void createScramUser(String username, String password) {
        try {
            execInContainer(
                "kafka-configs", "--bootstrap-server", "localhost:9092",
                "--alter", "--add-config", "SCRAM-SHA-512=[password=" + password + "]",
                "--entity-type", "users", "--entity-name", username
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SCRAM user", e);
        }
    }
}
```

### Docker Compose Integration

```java
import org.testcontainers.containers.DockerComposeContainer;
import java.io.File;

@QuarkusTest
class DockerComposeTest {

    static DockerComposeContainer<?> environment =
        new DockerComposeContainer<>(new File("src/test/resources/docker-compose-test.yml"))
            .withExposedService("kafka", 9092)
            .withExposedService("keycloak", 8080)
            .withExposedService("sync-agent", 8088);

    @BeforeAll
    static void startEnvironment() {
        environment.start();
    }

    @Test
    void testFullStack() {
        String syncAgentUrl = "http://" +
            environment.getServiceHost("sync-agent", 8088) + ":" +
            environment.getServicePort("sync-agent", 8088);

        given()
            .when().get(syncAgentUrl + "/api/summary")
            .then().statusCode(200);
    }

    @AfterAll
    static void stopEnvironment() {
        environment.stop();
    }
}
```

## Performance Testing

### Load Test Setup

```java
@QuarkusTest
@QuarkusTestResource(SyncAgentTestResource.class)
class PerformanceTest {

    @Inject
    KeycloakUserService keycloakService;

    @Inject
    SyncService syncService;

    @Test
    void testSyncPerformanceWith1000Users() {
        // Create 1000 test users
        IntStream.range(0, 1000).parallel().forEach(i -> {
            UserRepresentation user = new UserRepresentation();
            user.setUsername("user" + i);
            user.setEnabled(true);
            keycloakService.createUser(user);
        });

        // Measure sync time
        long startTime = System.currentTimeMillis();

        ReconcileResult result = syncService.reconcile()
            .await().atMost(Duration.ofMinutes(5));

        long duration = System.currentTimeMillis() - startTime;

        // Assertions
        assertThat(result.itemsProcessed()).isEqualTo(1000);
        assertThat(duration).isLessThan(60000); // < 1 minute
        assertThat(result.avgDurationMs()).isLessThan(100); // < 100ms per user

        System.out.printf("Synced %d users in %dms (%.2f users/sec)%n",
            result.itemsProcessed(),
            duration,
            (result.itemsProcessed() * 1000.0) / duration
        );
    }

    @Test
    void testConcurrentReconciliations() {
        // Test multiple reconciliations running concurrently
        List<Uni<ReconcileResult>> reconciles = IntStream.range(0, 10)
            .mapToObj(i -> syncService.reconcile())
            .toList();

        List<ReconcileResult> results = Uni.join().all(reconciles).andFailFast()
            .await().atMost(Duration.ofMinutes(2));

        assertThat(results).hasSize(10);
        assertThat(results).allMatch(r -> r.itemsProcessed() > 0);
    }
}
```

## Troubleshooting

### Enable Container Logging

```java
@BeforeAll
static void startContainers() {
    kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"))
        .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("kafka")));
    kafka.start();
}
```

### Access Container Logs

```java
@Test
void debugTest() {
    String logs = kafka.getLogs();
    System.out.println(logs);
}
```

### Execute Commands in Container

```java
@Test
void debugKafka() throws Exception {
    Container.ExecResult result = kafka.execInContainer(
        "kafka-topics", "--list", "--bootstrap-server", "localhost:9092"
    );
    System.out.println("Topics: " + result.getStdout());
}
```

### Custom Wait Strategy

```java
keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:23.0")
    .waitingFor(new AbstractWaitStrategy() {
        @Override
        protected void waitUntilReady() {
            // Custom logic
            int retries = 0;
            while (retries < 60) {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(
                        "http://" + getHost() + ":" + getPort() + "/health/ready"
                    ).openConnection();
                    if (conn.getResponseCode() == 200) {
                        return;
                    }
                } catch (IOException e) {
                    // Retry
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                retries++;
            }
            throw new RuntimeException("Container not ready");
        }
    });
```

## Best Practices

1. **Reuse containers**: Use singleton pattern for expensive containers
2. **Parallel execution**: Run independent tests in parallel
3. **Clean state**: Reset data between tests or use transactions
4. **Timeout configuration**: Set appropriate wait timeouts
5. **Resource cleanup**: Always stop containers in @AfterAll
6. **Logging**: Enable container logs for debugging
7. **Network isolation**: Use custom networks for multi-container tests
8. **Image caching**: Use fixed image tags, not :latest
9. **Startup optimization**: Use wait strategies effectively
10. **CI/CD integration**: Configure Docker socket for CI environments

## CI/CD Configuration

### GitHub Actions

```yaml
name: Integration Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run integration tests
        run: ./mvnw verify -Pintegration-tests

      - name: Publish test results
        if: always()
        uses: EnricoMi/publish-unit-test-result-action@v2
        with:
          files: target/surefire-reports/*.xml
```

### Maven Configuration

```xml
<profile>
    <id>integration-tests</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <version>3.0.0</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</profile>
```

## Resources

- Testcontainers Documentation: https://www.testcontainers.org/
- Quarkus Testing Guide: https://quarkus.io/guides/getting-started-testing
- Testcontainers Modules: https://www.testcontainers.org/modules/

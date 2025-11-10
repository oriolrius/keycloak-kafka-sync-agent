# Keycloak Password Sync SPI for Kafka SCRAM

A lightweight Keycloak Event Listener SPI that synchronizes user passwords directly to Kafka SCRAM credentials in real-time.

**Architecture**: Ultra-simple! Password synchronization happens **immediately** via direct Kafka AdminClient connection from within Keycloak. No external services, no webhooks, no caching. See [decision-003](backlog/decisions/decision-003%20-%20Direct%20Kafka%20SPI%20Architecture.md) for the architecture decision record.

```
┌──────────┐
│ Keycloak │  (with SPI JAR)
│          │
│  Password├─────► Kafka AdminClient ────► Kafka SCRAM Credentials
│  Change  │         (direct sync)             (immediate)
└──────────┘
```

## Features

- ⚡ **Immediate Password Sync**: Intercepts password changes BEFORE Keycloak hashing
- 🎯 **Zero Dependencies**: Single JAR deployed to Keycloak (no separate services)
- 🔐 **SCRAM-SHA-256/512**: Full support for Kafka's SCRAM authentication mechanisms
- 🛡️ **ThreadLocal Correlation**: Secure password correlation using Keycloak's custom password hashing SPI
- 🔧 **Environment Configuration**: Simple environment variables for Kafka connection

## Project Structure

```
keycloak-kafka-sync-agent/
├── src/                                             # The entire implementation (12 Java files)
│   ├── src/main/java/
│   │   └── com/miimetiq/keycloak/spi/
│   │       ├── PasswordSyncEventListener.java       # Intercepts password events
│   │       ├── PasswordSyncHashProvider*.java       # Custom hash provider for correlation
│   │       ├── KafkaScramSync.java                  # Direct Kafka sync
│   │       ├── KafkaAdminClientFactory.java         # Kafka AdminClient management
│   │       ├── PasswordCorrelationContext.java      # ThreadLocal password storage
│   │       ├── crypto/ScramCredentialGenerator.java # SCRAM credential generation
│   │       └── domain/                              # Domain models
│   ├── pom.xml                                      # Maven build configuration
│   └── target/keycloak-password-sync-spi.jar        # Built SPI JAR (after mvn package)
├── testing/                                         # Docker Compose infrastructure
├── tests/                                           # E2E tests (Playwright)
└── backlog/                                         # Project documentation
```

## Quick Start

### 1. Build the SPI

```bash
cd src
mvn clean package
```

The JAR will be at: `src/target/keycloak-password-sync-spi.jar`

### 2. Deploy to Keycloak

Copy the JAR to Keycloak's providers directory:

```bash
cp src/target/keycloak-password-sync-spi.jar /opt/keycloak/providers/
```

### 3. Configure Environment Variables

Set these in your Keycloak deployment:

```bash
# Required: Kafka connection
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Optional: SASL authentication
export KAFKA_SASL_MECHANISM=SCRAM-SHA-512
export KAFKA_SASL_USERNAME=admin
export KAFKA_SASL_PASSWORD=admin-secret

# Optional: Kafka timeouts
export KAFKA_DEFAULT_API_TIMEOUT_MS=60000
export KAFKA_REQUEST_TIMEOUT_MS=30000
```

### 4. Rebuild Keycloak (if needed)

```bash
/opt/keycloak/bin/kc.sh build
```

### 5. Start Keycloak

```bash
/opt/keycloak/bin/kc.sh start
```

## Docker Compose (Testing)

The fastest way to test the complete stack:

```bash
cd testing/
make start
```

This starts:
- **KMS** (Certificate Authority) on port `57001`
- **Keycloak** (with SPI) on ports `57002` (HTTP) and `57003` (HTTPS)
- **Kafka** on ports `57004` (PLAINTEXT) and `57005` (SSL)

See [testing/README.md](testing/README.md) for detailed infrastructure documentation.

## Configuration

### SPI Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses | `localhost:9092` |
| `KAFKA_SASL_MECHANISM` | SASL mechanism (`PLAIN`, `SCRAM-SHA-256`, `SCRAM-SHA-512`) | none |
| `KAFKA_SASL_USERNAME` | Kafka username for SASL authentication | none |
| `KAFKA_SASL_PASSWORD` | Kafka password for SASL authentication | none |
| `KAFKA_DEFAULT_API_TIMEOUT_MS` | Kafka API operation timeout | `60000` |
| `KAFKA_REQUEST_TIMEOUT_MS` | Kafka request timeout | `30000` |
| `password.sync.kafka.enabled` | Enable/disable Kafka sync (Java system property) | `true` |

### Password Hashing Configuration

The SPI uses a custom password hashing provider to intercept passwords. Configure this in Keycloak's realm settings or use the provided `PasswordSyncHashProviderSimple`.

## How It Works

### 1. Password Change Event

When a user changes their password in Keycloak:

```java
// Keycloak Admin API call
PUT /admin/realms/master/users/{userId}/reset-password
{ "type": "password", "value": "MyPassword123!", "temporary": false }
```

### 2. Custom Hash Provider Intercepts

```java
// PasswordSyncHashProviderSimple stores password in ThreadLocal
PasswordCorrelationContext.setPassword(rawPassword);
```

### 3. Event Listener Triggers

```java
// PasswordSyncEventListener receives admin event
@Override
public void onEvent(AdminEvent event, boolean includeRepresentation) {
    if (event.getOperationType() == OperationType.UPDATE &&
        event.getResourceType() == ResourceType.USER) {

        // Get password from ThreadLocal
        String password = PasswordCorrelationContext.getPassword();
        String username = lookupUsername(event);

        // Sync to Kafka immediately
        kafkaScramSync.syncPassword(username, password);
    }
}
```

### 4. Direct Kafka Sync

```java
// KafkaScramSync uses Kafka AdminClient API
ScramCredential cred = ScramCredentialGenerator.generate(
    password, mechanism, iterations
);

adminClient.alterUserScramCredentials(List.of(
    new UserScramCredentialUpsertion(username, credentialInfo, cred.getSalt(), ...)
)).all().get();
```

### 5. User Can Authenticate

```java
// User connects to Kafka with same password
Properties props = new Properties();
props.put("sasl.mechanism", "SCRAM-SHA-256");
props.put("sasl.jaas.config",
    "org.apache.kafka.common.security.scram.ScramLoginModule required " +
    "username=\"" + username + "\" password=\"" + password + "\";");

KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
// ✅ Authentication succeeds!
```

## Testing

### Unit Tests

```bash
cd src
mvn test
```

### E2E Tests

```bash
cd testing/
make start  # Start infrastructure

cd ..
npm install
npm run test:scram-e2e  # Run Playwright E2E tests
```

## Architecture Benefits

### Before (Complex)
- Separate Quarkus service (21+ Java files)
- REST API endpoints (dashboard, reconciliation)
- SQLite database for event persistence
- Prometheus metrics
- Health checks
- Retention management
- **Result**: 340MB, multiple components, complexity

### After (Ultra-Simple)
- Single Keycloak SPI JAR (12 Java files)
- Direct Kafka synchronization
- No external dependencies
- **Result**: ~2MB JAR, zero external services

## Security Considerations

### Development/Testing
- ✅ Passwords transmitted over Docker network (isolated)
- ✅ ThreadLocal storage (thread-safe, cleared after use)
- ✅ No persistent storage of plain passwords

### Production
For production deployments:
1. **Use TLS** for Kafka connections (`KAFKA_SECURITY_PROTOCOL=SASL_SSL`)
2. **Secure credentials** using environment secrets management
3. **Audit logging** for password sync events
4. **Network isolation** between Keycloak and Kafka
5. **Regular security updates** for Keycloak and Kafka

## References

- [Keycloak Event Listener SPI](https://www.keycloak.org/docs/latest/server_development/#_events)
- [SCRAM-SHA-256 RFC 7677](https://tools.ietf.org/html/rfc7677)
- [Kafka SCRAM Authentication](https://kafka.apache.org/documentation/#security_sasl_scram)
- [Architecture Decision: Direct Kafka SPI](backlog/decisions/decision-003%20-%20Direct%20Kafka%20SPI%20Architecture.md)

## License

See LICENSE file for details.

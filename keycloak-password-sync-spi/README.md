# Keycloak Password Sync SPI - Implementation Guide

## 📋 Overview

This Keycloak Event Listener SPI intercepts password reset events **before** Keycloak hashes the passwords and sends them to the sync-agent. This enables SCRAM credential synchronization using the actual user passwords.

## ✅ What Has Been Created

### 1. Maven Project Structure
```
keycloak-password-sync-spi/
├── pom.xml                                     ✅ Created
├── src/main/java/com/miimetiq/keycloak/spi/
│   ├── PasswordSyncEventListener.java         ✅ Created
│   └── PasswordSyncEventListenerFactory.java  ✅ Created
└── src/main/resources/META-INF/services/
    └── org.keycloak.events.EventListenerProviderFactory  ✅ Created
```

### 2. Key Components

- **PasswordSyncEventListener**: Intercepts password reset admin events
- **PasswordSyncEventListenerFactory**: SPI factory for Keycloak to discover the listener
- **META-INF/services**: Java SPI configuration for auto-discovery

## 🔨 Build Instructions

### Step 1: Build the JAR

```bash
cd keycloak-password-sync-spi
mvn clean package
```

Expected output:
```
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

The JAR will be at: `target/keycloak-password-sync-spi.jar`

### Step 2: Verify the JAR

```bash
jar tf target/keycloak-password-sync-spi.jar | grep -E "Password|META-INF"
```

Should show:
```
com/miimetiq/keycloak/spi/PasswordSyncEventListener.class
com/miimetiq/keycloak/spi/PasswordSyncEventListenerFactory.class
META-INF/services/org.keycloak.events.EventListenerProviderFactory
```

## 📦 Remaining Implementation Steps

### Step 3: Update Keycloak Dockerfile

Add the SPI JAR to the Keycloak container in `testing/dockerfiles/Dockerfile.keycloak`:

```dockerfile
FROM quay.io/keycloak/keycloak:26.4

USER root

# Copy Password Sync SPI
COPY ../keycloak-password-sync-spi/target/keycloak-password-sync-spi.jar /opt/keycloak/providers/

# Set permissions
RUN chmod 644 /opt/keycloak/providers/keycloak-password-sync-spi.jar

USER 1000

# Build Keycloak with the SPI
RUN /opt/keycloak/bin/kc.sh build

# ... rest of Dockerfile
```

**OR** use docker-compose volume mount (easier for testing):

Update `testing/docker-compose.yml`:

```yaml
services:
  keycloak:
    # ... existing config ...
    volumes:
      - ${CERTS_DIR:-./certs}:/opt/keycloak/conf/certs:ro
      - ${KEYCLOAK_DATA_DIR:-./data/keycloak}:/opt/keycloak/data
      - ../keycloak-password-sync-spi/target/keycloak-password-sync-spi.jar:/opt/keycloak/providers/keycloak-password-sync-spi.jar:ro  # ADD THIS
    command:
      - start-dev
      - --spi-events-listener-password-sync-listener-enabled=true  # ADD THIS
```

### Step 4: Add Sync-Agent Webhook Endpoint

Create `/api/webhook/password` endpoint in sync-agent to receive password events.

**File**: `src/main/java/com/miimetiq/keycloak/sync/webhook/PasswordWebhookResource.java`

```java
package com.miimetiq.keycloak.sync.webhook;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@Path("/api/webhook/password")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PasswordWebhookResource {

    private static final Logger LOG = Logger.getLogger(PasswordWebhookResource.class);

    // Store passwords temporarily (in-memory cache)
    private static final Map<String, String> PASSWORD_CACHE = new ConcurrentHashMap<>();

    @POST
    public Response receivePassword(PasswordEvent event) {
        LOG.infof("Received password for user: %s", event.username);

        // Store password temporarily for next reconciliation
        PASSWORD_CACHE.put(event.username, event.password);

        return Response.ok().build();
    }

    public static String getPasswordForUser(String username) {
        return PASSWORD_CACHE.remove(username); // Get and remove
    }

    public static class PasswordEvent {
        public String realmId;
        public String username;
        public String userId;
        public String password;
    }
}
```

### Step 5: Update ReconciliationService

Modify `ReconciliationService.java` to use real passwords when available:

```java
// In performReconciliation(), around line 164-168:

// Generate credentials for upserts
Map<String, CredentialSpec> credentialSpecs = new HashMap<>();
for (KeycloakUserInfo user : syncPlan.getUpserts()) {
    // Try to get real password from webhook cache
    String password = PasswordWebhookResource.getPasswordForUser(user.getUsername());

    // Fallback to random password if not available
    if (password == null || password.isEmpty()) {
        password = generateRandomPassword();
        LOG.warnf("No password from webhook for user %s, using random password", user.getUsername());
    } else {
        LOG.infof("Using real password from webhook for user %s", user.getUsername());
    }

    credentialSpecs.put(user.getUsername(),
                       new CredentialSpec(DEFAULT_MECHANISM, password, DEFAULT_ITERATIONS));
}
```

### Step 6: Enable Event Listener in Keycloak

After Keycloak starts, enable the event listener via Admin Console:

1. Login to Keycloak: `https://localhost:57003`
2. Navigate to: `Realm Settings` → `Events` → `Event listeners`
3. Add `password-sync-listener` to the list
4. Save

**OR** configure via CLI:

```bash
docker exec keycloak /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 --realm master --user admin --password The2password.

docker exec keycloak /opt/keycloak/bin/kcadm.sh update events/config \
  -s 'eventsListeners=["jboss-logging","password-sync-listener"]'
```

## 🧪 Testing

### Test 1: Build and Deploy

```bash
# Build SPI
cd keycloak-password-sync-spi
mvn clean package

# Rebuild Keycloak with SPI
cd ../testing
docker compose build keycloak
docker compose up -d keycloak

# Check logs
docker logs keycloak | grep -i "password-sync"
```

Expected: `password-sync-listener provider loaded successfully`

### Test 2: Trigger Password Event

```bash
# Run e2e test
cd ..
npm run test:scram-e2e
```

Watch for:
1. Keycloak logs: `Detected password reset for user: scram-test-user-...`
2. Sync-agent logs: `Received password for user: scram-test-user-...`
3. Test output: `✅ STEP 4 COMPLETE: Successfully authenticated to Kafka`

## 🐛 Troubleshooting

### SPI Not Loading

```bash
# Check if JAR exists in container
docker exec keycloak ls -l /opt/keycloak/providers/

# Check Keycloak logs
docker logs keycloak 2>&1 | grep -i "provider\|spi"
```

### Password Events Not Firing

```bash
# Enable Keycloak debug logging
docker exec keycloak /opt/keycloak/bin/kc.sh show-config | grep log

# Test password reset manually
curl -X PUT https://localhost:57003/admin/realms/master/users/{userId}/reset-password \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"type":"password","value":"test123","temporary":false}'
```

### Webhook Not Received

```bash
# Check sync-agent is reachable from Keycloak container
docker exec keycloak ping -c 3 agent.example

# Check sync-agent logs
./mvnw quarkus:dev  # Watch console
```

## 📝 Architecture Flow

```
┌─────────────────────────────────────────────────────────────┐
│  1. User creates account with password in Keycloak          │
│     POST /admin/realms/master/users/{id}/reset-password     │
│     Body: {"type":"password","value":"MyPassword123!"}       │
└──────────────────────┬──────────────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  2. PasswordSyncEventListener intercepts BEFORE hashing     │
│     - Extracts plain password from JSON representation      │
│     - Sends webhook to sync-agent                            │
└──────────────────────┬──────────────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  3. Sync-Agent receives password webhook                    │
│     POST /api/webhook/password                               │
│     {username, password, userId, realmId}                    │
│     - Stores in temporary cache                              │
└──────────────────────┬──────────────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  4. Reconciliation cycle starts                              │
│     - Fetches users from Keycloak                            │
│     - Checks password cache for real passwords               │
│     - Uses real password if available, random if not         │
└──────────────────────┬──────────────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  5. SCRAM credentials created in Kafka                       │
│     - Username: scram-test-user-123                          │
│     - Password: MyPassword123! (THE REAL PASSWORD!)          │
│     - Mechanism: SCRAM-SHA-256                               │
└──────────────────────┬──────────────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  6. E2E Test authenticates to Kafka                          │
│     - Uses same password from Keycloak                       │
│     - SCRAM handshake succeeds ✅                            │
│     - Test PASSES! 🎉                                        │
└─────────────────────────────────────────────────────────────┘
```

## ⚠️ Security Considerations

### Development/Testing Environment
- ✅ Passwords sent over Docker network (isolated)
- ✅ Temporary in-memory cache (cleared after use)
- ✅ No persistent storage of plain passwords

### Production Environment (DO NOT USE AS-IS)
This implementation is **FOR TESTING ONLY**. For production:

1. **Use HTTPS** for webhook communication
2. **Encrypt passwords** in transit (TLS + message encryption)
3. **Use secure secret management** (HashiCorp Vault, AWS Secrets Manager)
4. **Implement authentication** for webhook endpoint
5. **Add rate limiting** and request validation
6. **Audit logging** for all password events

## 📚 References

- [Keycloak Event Listener SPI](https://www.keycloak.org/docs/latest/server_development/#_events)
- [SCRAM-SHA-256 RFC 5802](https://tools.ietf.org/html/rfc5802)
- [Kafka SCRAM Authentication](https://kafka.apache.org/documentation/#security_sasl_scram)

---

**Status**: ✅ SPI Implementation Complete - Awaiting Integration Testing
**Last Updated**: 2025-11-07
**Author**: Claude Code

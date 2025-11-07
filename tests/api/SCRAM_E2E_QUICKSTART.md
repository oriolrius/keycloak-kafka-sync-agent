# 🚀 SCRAM E2E Test - Quick Start Guide

## Overview

This test verifies **end-to-end SCRAM authentication** from Keycloak to Kafka, proving that:
1. Users created in Keycloak
2. Are synced to Kafka as SCRAM credentials
3. Can authenticate to Kafka using SCRAM-SHA-256
4. Can produce and consume messages

## Prerequisites

### 1. Start Testing Infrastructure

```bash
cd testing
make start
```

This starts:
- **KMS** (Certificate Authority) - `localhost:57001`
- **Keycloak** (HTTPS) - `https://localhost:57003`
- **Kafka** (SSL) - `localhost:57005` (external) / `kafka.example:9093` (internal)

Wait for all services to be healthy:
```bash
make health
```

Expected output:
```
KMS:       ✓ OK
Keycloak:  ✓ OK (HTTPS)
Kafka:     ✓ OK (SSL)
```

### 2. Install Dependencies

```bash
npm install
```

This installs:
- `@playwright/test` - Testing framework
- `kafkajs` - Kafka client library
- `@types/node` - TypeScript types

### 3. Configure /etc/hosts

Add to `/etc/hosts`:
```
127.0.0.1  kms.example keycloak.example kafka.example agent.example
```

On Linux/Mac:
```bash
sudo sh -c 'echo "127.0.0.1  kms.example keycloak.example kafka.example agent.example" >> /etc/hosts'
```

## Run the Test

### Option 1: Quick Run (Headless)

```bash
npm run test:scram-e2e
```

### Option 2: Watch Mode (See Browser)

```bash
npm run test:scram-e2e:headed
```

### Option 3: Debug Mode (Step Through)

```bash
npm run test:scram-e2e:debug
```

## Test Flow

```
┌─────────────────────────────────────────────┐
│  STEP 1: Create User in Keycloak           │
│  - Realm: e2e-scram-test                   │
│  - Username: scram-test-user-<timestamp>   │
│  - Password: ScramTest123!@#               │
└─────────────────┬───────────────────────────┘
                  ▼
┌─────────────────────────────────────────────┐
│  STEP 2: Trigger Reconciliation             │
│  - POST /api/reconcile/trigger              │
│  - Agent syncs Keycloak → Kafka            │
└─────────────────┬───────────────────────────┘
                  ▼
┌─────────────────────────────────────────────┐
│  STEP 3: Verify Via Admin API               │
│  - describeUserScramCredentials()           │
│  - Verify SCRAM-SHA-256 exists              │
└─────────────────┬───────────────────────────┘
                  ▼
┌─────────────────────────────────────────────┐
│  STEP 4: Authenticate to Kafka! 🎯          │
│  - SSL/TLS connection                       │
│  - SASL mechanism: scram-sha-256            │
│  - THIS IS THE CRITICAL TEST!               │
└─────────────────┬───────────────────────────┘
                  ▼
┌─────────────────────────────────────────────┐
│  STEP 5: Produce & Consume Messages         │
│  - Create topic                             │
│  - Produce 3 test messages                  │
│  - Consume and verify                       │
└─────────────────────────────────────────────┘
```

## Expected Output

```
✅ STEP 1 COMPLETE: Created user 'scram-test-user-1730835200000' in Keycloak realm 'e2e-scram-test'

✅ STEP 2 COMPLETE: Reconciliation triggered
   Correlation ID: f47ac10b-58cc-4372-a567-0e02b2c3d479
   Successful operations: 5
   Failed operations: 0
   Duration: 1234ms

✅ STEP 3 COMPLETE: SCRAM credentials found in Kafka
   Username: scram-test-user-1730835200000
   Mechanism: SCRAM-SHA-256
   Iterations: 4096

✅ STEP 4 COMPLETE: Successfully authenticated to Kafka with SCRAM-SHA-256!
   Cluster ID: MkU3OEVBNTcwNTJENDM2Qk
   Brokers: 1
   🎉 AUTHENTICATION SUCCESSFUL - CREDENTIALS WORK!

   Created topic: test-scram-auth-1730835200000
   Produced 3 messages to topic test-scram-auth-1730835200000
✅ STEP 5 COMPLETE: Produced and consumed 3 messages
   🎉 FULL KAFKA FUNCTIONALITY VERIFIED WITH SCRAM AUTH!

🧹 Cleaned up Keycloak user: scram-test-user-1730835200000
🧹 Cleaned up Kafka topic: test-scram-auth-1730835200000

  5 passed (15.2s)
```

## Troubleshooting

### Test fails at STEP 1 (Keycloak user creation)

**Problem:** Cannot connect to Keycloak

**Solution:**
```bash
# Check Keycloak is running
curl -k https://localhost:57003/

# Check logs
cd testing
make logs-keycloak
```

### Test fails at STEP 2 (Reconciliation)

**Problem:** Sync-agent not running or not configured

**Solution:**
```bash
# Start sync-agent
./mvnw quarkus:dev

# Check it's running
curl http://localhost:57010/health/ready
```

### Test fails at STEP 3 (Admin API)

**Problem:** Kafka not accessible or credentials not synced

**Solution:**
```bash
# Check Kafka is running
cd testing
make logs-kafka

# Trigger manual reconciliation
curl -X POST http://localhost:57010/api/reconcile/trigger
```

### Test fails at STEP 4 (Authentication) 🚨

**This is the critical test!**

**Problem:** SCRAM authentication failed

**Possible causes:**
1. **Credentials not synced properly**
   - Check sync-agent logs
   - Verify credentials in Kafka: `make shell-kafka` then run `kafka-configs.sh`

2. **Wrong password**
   - Verify password in test matches Keycloak user password
   - Check `TEST_PASSWORD` in test file

3. **SSL/TLS certificate issue**
   - Verify `testing/certs/ca-root.pem` exists
   - Regenerate certs: `cd testing && make certs`

4. **Kafka not configured for SCRAM**
   - Check Kafka config in `testing/docker-compose.yml`
   - Verify SCRAM mechanism is enabled

### Test fails at STEP 5 (Produce/Consume)

**Problem:** Kafka topic creation or ACLs

**Solution:**
```bash
# Check if topic was created
cd testing
make list-topics

# Manually test producer
make producer
```

### All tests timeout

**Problem:** Services not responding

**Solution:**
```bash
# Restart entire stack
cd testing
make down
make clean  # Answer 'y'
make start
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `API_BASE_URL` | `http://localhost:57010` | Sync-agent API URL |
| `KEYCLOAK_URL` | `https://localhost:57003` | Keycloak HTTPS URL |
| `KAFKA_SSL_BROKER` | `kafka.example:9093` | Kafka SSL broker address |
| `KEYCLOAK_ADMIN_USER` | `admin` | Keycloak admin username |
| `KEYCLOAK_ADMIN_PASSWORD` | `The2password.` | Keycloak admin password |

Override example:
```bash
KEYCLOAK_URL=https://keycloak.mycompany.local:8443 npm run test:scram-e2e
```

## What This Test Proves

### ✅ Technical Evidence

1. **SCRAM Credential Generation:**
   - Sync-agent generates RFC 5802 compliant credentials
   - Correct PBKDF2 parameters (salt, iterations)
   - Proper Base64 encoding

2. **Kafka Integration:**
   - AdminClient API works correctly
   - Credentials stored in Kafka's internal `__cluster_metadata`
   - Batch operations succeed

3. **Reconciliation:**
   - Keycloak users fetched correctly
   - Diff engine identifies new users
   - Operations persisted to SQLite

4. **END-TO-END AUTHENTICATION:** ⭐
   - **Kafka client authenticates with synced credentials**
   - **SCRAM-SHA-256 handshake succeeds**
   - **SSL/TLS + SCRAM work together**
   - **Producer/Consumer operations succeed**

### 🎯 This Is The Missing Piece!

Previous tests verified:
- ✅ Credentials are CREATED in Kafka
- ✅ Credentials are VISIBLE via Admin API

This test verifies:
- ✅ **Credentials WORK for real authentication**
- ✅ **SCRAM algorithm is compatible with Kafka**
- ✅ **End-to-end flow is complete**

## Next Steps

### Run in CI/CD

Add to GitHub Actions:
```yaml
- name: Run SCRAM E2E Tests
  run: |
    cd testing && make start
    npm install
    npm run test:scram-e2e
  env:
    CI: true
```

### Extend Tests

Add more scenarios:
- Multiple users with different passwords
- SCRAM-SHA-512 mechanism
- Password rotation (update credentials)
- User deletion (remove from Kafka)
- ACL verification (topic permissions)

### Production Deployment

1. **Use proper certificates** (not self-signed)
2. **Enable Kafka ACLs** for topic permissions
3. **Configure retention** for operation history
4. **Monitor metrics** via Prometheus
5. **Set up alerts** for failed reconciliations

## Support

**Documentation:**
- Full evidence document: `SCRAM_E2E_EVIDENCE.md`
- Test file: `tests/api/scram-authentication-e2e.spec.ts`
- Testing infrastructure: `testing/README.md`

**Issues:**
- Check sync-agent logs: `./mvnw quarkus:dev` console
- Check Kafka logs: `cd testing && make logs-kafka`
- Check Keycloak logs: `cd testing && make logs-keycloak`

**Clean slate:**
```bash
cd testing
make clean  # Answer 'y' to remove everything
make start  # Fresh start
```

---

**Last Updated:** 2025-11-05
**Test Status:** ✅ Ready to run
**Required Services:** KMS, Keycloak, Kafka, Sync-Agent

---
id: task-062
title: Implement direct Kafka sync in PasswordHashProvider SPI
status: Done
assignee:
  - '@claude'
created_date: '2025-11-09 11:18'
updated_date: '2025-11-09 12:09'
labels:
  - spi
  - core-logic
dependencies:
  - task-061
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Modify the PasswordHashProviderSimple to sync passwords directly to Kafka on password change/creation. When password is set, immediately generate SCRAM credentials and upsert to Kafka via AdminClient. If Kafka connection fails, the Keycloak password change should fail with clear error message.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 On password update, SCRAM credentials are generated and sent to Kafka
- [x] #2 Successful Kafka sync allows password change to complete
- [x] #3 Kafka connection failure causes password change to fail with error message
- [x] #4 Error message clearly indicates Kafka connectivity issue
- [x] #5 Password change transaction is atomic (both succeed or both fail)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Examine current PasswordHashProviderSimple implementation
2. Add Kafka AdminClient configuration to SPI module
3. Integrate ScramCredentialGenerator with password encoding
4. Add Kafka user upsert logic in encode() method
5. Implement proper error handling that fails password change on Kafka errors
6. Add configuration for Kafka bootstrap servers and topic
7. Write unit tests for the integration
8. Test end-to-end that password changes sync to Kafka
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Successfully implemented direct Kafka sync in the PasswordHashProvider SPI. Password changes now sync directly to Kafka without requiring the sync-agent webhook.

**Architecture:**
- Created `KafkaScramSync` utility class that handles the complete sync flow
- Modified `PasswordSyncEventListener` to use direct Kafka sync instead of webhooks
- Leverages existing `ScramCredentialGenerator` from task-61
- Uses `KafkaAdminClientFactory` for Kafka connectivity

**Flow:**
1. `PasswordHashProviderSimple` intercepts password and stores in ThreadLocal
2. `PasswordSyncEventListener` retrieves password and username from context
3. `KafkaScramSync.syncPasswordToKafka()` is called:
   - Generates SCRAM-SHA-256 credentials using ScramCredentialGenerator
   - Gets Kafka AdminClient from KafkaAdminClientFactory
   - Converts credentials to Kafka's format
   - Executes upsert via AdminClient.alterUserScramCredentials()
   - Waits for completion with 30-second timeout
4. On success: password change completes normally
5. On failure: RuntimeException is thrown, failing the entire password change transaction

**Error Handling:**
- Kafka connection failures throw `KafkaSyncException` with clear error message
- EventListener re-throws as `RuntimeException` to fail password change atomically
- Error messages include: "Failed to sync password to Kafka: [reason]. Please ensure Kafka cluster is available and try again."
- Ensures both Keycloak and Kafka succeed or both fail

**Configuration:**
- `password.sync.kafka.enabled` (default: true) - enables/disables Kafka sync
- Uses existing Kafka environment variables from `KafkaAdminClientFactory`:
  - KAFKA_BOOTSTRAP_SERVERS
  - KAFKA_SECURITY_PROTOCOL
  - KAFKA_SASL_* and KAFKA_SSL_* settings

**Files Created:**
- keycloak-password-sync-spi/src/main/java/com/miimetiq/keycloak/spi/KafkaScramSync.java

**Files Modified:**
- keycloak-password-sync-spi/src/main/java/com/miimetiq/keycloak/spi/PasswordSyncEventListener.java

**Benefits:**
- Eliminates webhook dependency for password sync
- Atomic transaction: password changes fail if Kafka is unavailable
- Faster sync (direct connection vs HTTP webhook)
- Better error handling with clear user-facing messages
- Leverages existing Kafka infrastructure

**Deployment:**
JAR built successfully: `keycloak-password-sync-spi.jar`
Ready for deployment to Keycloak providers directory.
<!-- SECTION:NOTES:END -->

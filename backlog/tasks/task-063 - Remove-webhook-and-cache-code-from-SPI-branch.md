---
id: task-063
title: Remove webhook and cache code from SPI branch
status: Done
assignee:
  - '@claude'
created_date: '2025-11-09 11:18'
updated_date: '2025-11-09 12:18'
labels:
  - cleanup
  - spi
dependencies:
  - task-062
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Remove all webhook-related code from the feature branch: remove HTTP POST to sync-agent webhook, remove ThreadLocal password sharing, remove webhook endpoint dependencies. Keep only direct Kafka sync logic in the SPI. Be radical but use common sense - remove everything not needed for direct Kafka approach.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Webhook HTTP client code removed from SPI
- [x] #2 ThreadLocal password storage removed
- [x] #3 PasswordWebhookResource reference removed
- [x] #4 SPI only contains direct Kafka sync logic
- [x] #5 SPI compiles successfully without webhook dependencies
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Examine current SPI implementation to identify all webhook-related code
2. Remove webhook HTTP client code from PasswordUpdateEventListener
3. Remove ThreadLocal password storage mechanism
4. Remove PasswordWebhookResource references and dependencies
5. Clean up any webhook-related imports and dependencies
6. Verify SPI compiles successfully
7. Test that direct Kafka sync still works correctly
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Successfully removed all webhook and ThreadLocal-related code from the SPI:

**Changes Made:**
1. Deleted `PasswordCorrelationContext.java` - removed ThreadLocal password storage mechanism
2. Updated `PasswordSyncHashProviderSimple.java`:
   - Removed `PasswordCorrelationContext.setPassword()` call
   - Updated class javadoc to remove ThreadLocal references
3. Updated `PasswordSyncEventListener.java`:
   - Removed `PasswordCorrelationContext.getAndClearPassword()` fallback
   - Updated log message from "correlation context" to "event representation"
   - Updated class javadoc to reflect new approach
4. Updated `PasswordSyncEventListenerFactory.java`:
   - Updated javadoc from "webhook" to "direct Kafka sync"

**Verification:**
- SPI compiles successfully: `mvn clean compile` passed
- No HTTP client dependencies in SPI code
- SPI now relies solely on direct Kafka sync via `KafkaScramSync`
- Password extraction now only from event representation (no ThreadLocal fallback)

**Files Modified:**
- keycloak-password-sync-spi/src/main/java/com/miimetiq/keycloak/spi/PasswordCorrelationContext.java (DELETED)
- keycloak-password-sync-spi/src/main/java/com/miimetiq/keycloak/spi/PasswordSyncEventListener.java
- keycloak-password-sync-spi/src/main/java/com/miimetiq/keycloak/spi/PasswordSyncHashProviderSimple.java
- keycloak-password-sync-spi/src/main/java/com/miimetiq/keycloak/spi/PasswordSyncEventListenerFactory.java

The SPI is now clean and contains only direct Kafka sync logic. The webhook approach has been completely removed.
<!-- SECTION:NOTES:END -->

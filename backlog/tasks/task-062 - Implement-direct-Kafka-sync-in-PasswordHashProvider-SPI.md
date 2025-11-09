---
id: task-062
title: Implement direct Kafka sync in PasswordHashProvider SPI
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-09 11:18'
updated_date: '2025-11-09 12:08'
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

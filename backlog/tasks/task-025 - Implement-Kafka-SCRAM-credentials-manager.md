---
id: task-025
title: Implement Kafka SCRAM credentials manager
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 18:35'
updated_date: '2025-11-05 04:49'
labels:
  - backend
  - kafka
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create a service that manages SCRAM credentials in Kafka using the AdminClient API. Supports listing, describing, upserting, and deleting SCRAM credentials.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 KafkaScramManager service created with AdminClient integration
- [x] #2 describeUserScramCredentials method returns map of principal to mechanisms
- [x] #3 alterUserScramCredentials method supports both upsert and delete operations
- [x] #4 Handles UserScramCredentialUpsertion for creating/updating credentials
- [x] #5 Handles UserScramCredentialDeletion for removing credentials
- [x] #6 Supports batch operations (multiple principals in single API call)
- [x] #7 Returns AlterUserScramCredentialsResult with per-principal futures
- [x] #8 Implements proper error handling for Kafka API exceptions
- [x] #9 Logs all operations with principal, mechanism, and operation type
- [x] #10 Unit tests with mocked AdminClient
- [x] #11 Integration test validates operations against real Kafka (Testcontainers)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Research existing codebase structure for service placement and patterns
2. Review Kafka AdminClient API for SCRAM credential operations
3. Create KafkaScramManager service with CDI integration
4. Implement describeUserScramCredentials method
5. Implement alterUserScramCredentials method (upsert and delete)
6. Add comprehensive error handling and logging
7. Write unit tests with Mockito
8. Write integration test using Testcontainers
9. Verify all acceptance criteria are met
<!-- SECTION:PLAN:END -->

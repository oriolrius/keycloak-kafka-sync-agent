---
id: task-042
title: Parse and map Keycloak admin event types to sync operations
status: To Do
assignee: []
created_date: '2025-11-05 10:16'
labels:
  - sprint-4
  - webhook
  - backend
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement parsing logic to extract relevant information from Keycloak Admin Event payloads and map them to internal sync operations (UPSERT/DELETE). Support event types: user create, user update, user delete, password change, client create/update/delete.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Parser extracts realm, principal, and event type from payload
- [ ] #2 User create/update events map to UPSERT operations
- [ ] #3 User delete events map to DELETE operations
- [ ] #4 Password change events trigger SCRAM credential regeneration
- [ ] #5 Client create/update/delete events are supported
- [ ] #6 Unknown event types are logged and ignored gracefully
- [ ] #7 Unit tests cover all supported event types
<!-- AC:END -->

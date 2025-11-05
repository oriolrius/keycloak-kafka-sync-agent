---
id: task-038
title: Implement Keycloak webhook endpoint for admin events
status: To Do
assignee: []
created_date: '2025-11-05 10:16'
updated_date: '2025-11-05 10:17'
labels:
  - sprint-4
  - webhook
  - backend
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create a REST endpoint `POST /api/kc/events` that receives Keycloak Admin Events. The endpoint must accept webhook payloads from Keycloak and enqueue them for processing. This is the entry point for event-driven synchronization alongside the periodic reconciliation.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 REST endpoint POST /api/kc/events accepts JSON payloads
- [ ] #2 Endpoint returns 200 OK for valid payloads
- [ ] #3 Endpoint returns 400 Bad Request for malformed JSON
- [ ] #4 Received events are logged with correlation ID
- [ ] #5 Integration test uses realistic mock Keycloak admin event payloads (user create, update, delete, password change)

- [ ] #6 Test validates endpoint correctly parses and enqueues mock events
<!-- AC:END -->

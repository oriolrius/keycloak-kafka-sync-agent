---
id: task-038
title: Implement Keycloak webhook endpoint for admin events
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 10:16'
updated_date: '2025-11-05 10:21'
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
- [x] #1 REST endpoint POST /api/kc/events accepts JSON payloads
- [x] #2 Endpoint returns 200 OK for valid payloads
- [x] #3 Endpoint returns 400 Bad Request for malformed JSON
- [x] #4 Received events are logged with correlation ID
- [x] #5 Integration test uses realistic mock Keycloak admin event payloads (user create, update, delete, password change)

- [x] #6 Test validates endpoint correctly parses and enqueues mock events
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Create webhook package structure under com.miimetiq.keycloak.sync.webhook
2. Create KeycloakWebhookResource with POST /api/kc/events endpoint
3. Create DTOs for Keycloak admin event payloads (KeycloakAdminEvent, EventRequest, EventResponse)
4. Implement request validation (JSON parsing, null checks)
5. Add UUID-based correlation ID generation and logging
6. Create temporary event queue stub (actual queue processing will be in task-040)
7. Create integration test with realistic mock Keycloak events (CREATE_USER, UPDATE_USER, DELETE_USER, PASSWORD_UPDATE)
8. Verify all HTTP response codes (200 OK, 400 Bad Request)
9. Run tests and verify all acceptance criteria
<!-- SECTION:PLAN:END -->

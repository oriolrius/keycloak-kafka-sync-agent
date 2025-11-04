---
id: task-002
title: Implement health check endpoints
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 14:33'
updated_date: '2025-11-04 16:36'
labels:
  - backend
  - observability
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create /healthz and /readyz endpoints that check connectivity to Kafka, Keycloak, and SQLite. These endpoints will be used by orchestrators and monitoring systems.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 GET /healthz returns JSON with overall status and component details
- [ ] #2 /healthz responds with 200 when all dependencies are healthy
- [ ] #3 /healthz responds with 503 when any dependency is down
- [ ] #4 GET /readyz returns readiness status for Kafka, Keycloak, and SQLite
- [ ] #5 Health checks include connection validation for each service
- [ ] #6 Response format matches specification: {status, details:{kafka, keycloak, sqlite}}
<!-- AC:END -->

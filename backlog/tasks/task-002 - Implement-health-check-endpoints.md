---
id: task-002
title: Implement health check endpoints
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 14:33'
updated_date: '2025-11-04 16:45'
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
- [x] #1 GET /healthz returns JSON with overall status and component details
- [x] #2 /healthz responds with 200 when all dependencies are healthy
- [x] #3 /healthz responds with 503 when any dependency is down
- [x] #4 GET /readyz returns readiness status for Kafka, Keycloak, and SQLite
- [x] #5 Health checks include connection validation for each service
- [x] #6 Response format matches specification: {status, details:{kafka, keycloak, sqlite}}
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Research Quarkus SmallRye Health implementation approach
2. Create KafkaHealthCheck implementing HealthCheck interface
3. Create KeycloakHealthCheck implementing HealthCheck interface
4. Create SQLiteHealthCheck implementing HealthCheck interface
5. Configure health endpoint paths in application.properties (/healthz for liveness, /readyz for readiness)
6. Test health endpoints with all services up
7. Test health endpoints with services down (503 response)
8. Verify JSON response format matches specification
<!-- SECTION:PLAN:END -->

---
id: task-047
title: Implement backend API endpoints for UI data
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 16:55'
updated_date: '2025-11-05 17:18'
labels:
  - backend
  - api
  - sprint-5
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create the RESTful API endpoints that the frontend will consume for displaying sync operations, batches, and configuration. These endpoints provide summary statistics, paginated operation history, and retention management.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 GET /api/summary returns summary statistics (ops/hour, error rate, latency p95/p99, DB usage)
- [ ] #2 GET /api/operations returns paginated operation timeline with filters (time range, principal, op_type, result)
- [ ] #3 GET /api/batches returns paginated batch summaries
- [x] #4 GET /api/config/retention returns current retention policies
- [x] #5 PUT /api/config/retention updates retention policies with validation
- [ ] #6 All endpoints documented with OpenAPI annotations
- [ ] #7 Endpoints return proper HTTP status codes and error messages
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Explore codebase structure and understand existing patterns (DONE)
2. Verify retention endpoints are already implemented (AC #4 and #5)
3. Create DTO classes for API responses:
   - SummaryResponse (ops/hour, error rate, latency percentiles, DB usage)
   - OperationResponse (timeline item)
   - OperationsPageResponse (paginated operations with filters)
   - BatchResponse (batch summary)
   - BatchesPageResponse (paginated batches)
4. Create DashboardResource with:
   - GET /api/summary endpoint
   - GET /api/operations endpoint with query params for filters
   - GET /api/batches endpoint with pagination
5. Implement repository query methods for:
   - Computing summary statistics (ops/hour, error rates, latency percentiles)
   - Fetching paginated operations with filters
   - Fetching paginated batches
6. Add OpenAPI annotations to all endpoints (using @Operation, @Parameter, etc.)
7. Ensure proper error handling and HTTP status codes
8. Add pom.xml dependencies for OpenAPI if needed
9. Test all endpoints manually or with integration tests
10. Mark all acceptance criteria as complete
<!-- SECTION:PLAN:END -->

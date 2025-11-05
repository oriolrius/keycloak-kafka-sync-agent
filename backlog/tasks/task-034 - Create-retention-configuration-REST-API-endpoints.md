---
id: task-034
title: Create retention configuration REST API endpoints
status: To Do
assignee: []
created_date: '2025-11-05 06:17'
labels:
  - sprint-3
  - retention
  - api
  - backend
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement GET and PUT endpoints at /api/config/retention for reading and updating retention policies (max_bytes, max_age_days). These endpoints allow operators to dynamically adjust retention settings without restarting the service.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 GET /api/config/retention returns current retention_state (max_bytes, max_age_days, approx_db_bytes, updated_at)
- [ ] #2 PUT /api/config/retention accepts JSON with max_bytes and/or max_age_days
- [ ] #3 PUT endpoint validates input (non-negative values, reasonable limits)
- [ ] #4 PUT endpoint updates retention_state table and returns updated config
- [ ] #5 Endpoints documented in OpenAPI specification
- [ ] #6 Integration tests verify GET and PUT operations
<!-- AC:END -->

---
id: task-056
title: Fix DashboardAuthConfig registration in backend integration tests
status: To Do
assignee: []
created_date: '2025-11-05 20:49'
labels:
  - bug
  - testing
  - configuration
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
All backend integration tests are currently failing with "Could not find a mapping for com.miimetiq.keycloak.sync.security.DashboardAuthConfig". The @ConfigMapping annotation requires proper registration in the Quarkus test configuration. This blocks all backend integration test execution.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Backend integration tests can start successfully
- [ ] #2 DashboardAuthConfig is properly registered in test environment
- [ ] #3 All previously passing backend integration tests pass again
<!-- AC:END -->

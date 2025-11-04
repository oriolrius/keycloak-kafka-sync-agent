---
id: task-003
title: Integrate Micrometer and Prometheus metrics endpoint
status: To Do
assignee: []
created_date: '2025-11-04 14:34'
labels:
  - backend
  - observability
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Set up Micrometer with Prometheus registry and expose /metrics endpoint. Configure basic application metrics that will be extended in future sprints.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 GET /metrics endpoint returns Prometheus exposition format
- [ ] #2 Micrometer registry is properly configured in application.properties
- [ ] #3 Basic JVM metrics (memory, threads, GC) are exposed
- [ ] #4 HTTP request metrics are collected automatically
- [ ] #5 Custom metrics infrastructure is ready for sync operations
<!-- AC:END -->

---
id: task-054
title: Add force reconcile functionality to dashboard
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 16:55'
updated_date: '2025-11-05 20:38'
labels:
  - backend
  - frontend
  - reconciliation
  - sprint-5
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement the manual sync trigger button on the dashboard that allows operators to force an immediate reconciliation cycle outside of the normal schedule. This is useful for testing or responding to urgent sync requirements.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 POST /api/reconcile/trigger endpoint implemented in backend
- [ ] #2 Endpoint validates authentication and authorization
- [ ] #3 Endpoint enqueues immediate reconciliation job
- [ ] #4 Returns correlation_id for tracking the triggered reconciliation
- [ ] #5 Force Reconcile button visible on dashboard
- [ ] #6 Button disabled during active reconciliation
- [ ] #7 Success notification shows correlation_id
- [ ] #8 Error notification on failure with error details
- [ ] #9 Button re-enables after reconciliation completes
<!-- AC:END -->

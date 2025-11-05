---
id: task-051
title: Create Batch Summary page
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 16:55'
updated_date: '2025-11-05 18:22'
labels:
  - frontend
  - batches
  - sprint-5
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement the batch summary page displaying reconciliation cycle information including success/error counts and timing. This helps operators understand the periodic sync behavior and identify problematic cycles.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Paginated table displays batch history with correlation IDs
- [ ] #2 Columns show start time, finish time, source (webhook/reconcile), items total, success count, error count
- [ ] #3 Duration calculation displayed for each batch
- [ ] #4 Success rate percentage calculated and displayed
- [ ] #5 Filter by source type (webhook vs periodic reconcile)
- [ ] #6 Filter by time range
- [ ] #7 Click on batch row expands to show related operations
- [ ] #8 Real-time updates via TanStack Query polling
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Review existing backend API - /api/batches endpoint and BatchResponse/BatchesPageResponse DTOs (already implemented)
2. Review existing frontend infrastructure - useBatches hook, apiClient.getBatches(), and type definitions (already exist)
3. Create /frontend/src/pages/Batches.tsx following Operations.tsx pattern:
   - Pagination state (page, pageSize)
   - Filter state (source type: SCHEDULED/MANUAL/WEBHOOK, time range)
   - Table with columns: correlation ID, start time, finish time, source, items total, success count, error count, duration, success rate
   - Expandable rows to show related operations (link to operations filtered by correlation ID)
   - Calculate duration and success rate percentage
   - Export to CSV functionality
4. Add /batches route to App.tsx
5. Add "Batches" navigation link to Layout.tsx
6. Implement real-time updates via TanStack Query polling (configure refetchInterval)
7. Test the page manually with dev server
8. Create Playwright UI tests for Batches page in frontend/tests/batches.spec.ts
9. Run tests and fix any issues
<!-- SECTION:PLAN:END -->

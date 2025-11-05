---
id: task-035
title: Implement scheduled retention purge job
status: Done
assignee:
  - '@claude'
created_date: '2025-11-05 06:17'
updated_date: '2025-11-05 09:44'
labels:
  - sprint-3
  - retention
  - backend
  - scheduling
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create a scheduled background job that executes retention purge logic periodically (configurable via RETENTION_PURGE_INTERVAL_SECONDS). The job should also trigger after each sync batch completes to maintain database size limits.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Scheduled job runs every RETENTION_PURGE_INTERVAL_SECONDS (default 300s)
- [x] #2 Job executes both TTL and space-based purge logic
- [x] #3 Purge is triggered after each sync_batch completion
- [x] #4 Job handles failures gracefully and logs errors
- [x] #5 Configuration loads RETENTION_PURGE_INTERVAL_SECONDS from environment
- [x] #6 Unit tests verify scheduling behavior and error handling
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Create RetentionScheduler.java following ReconciliationScheduler pattern
   - Use @Scheduled(every = "${retention.purge-interval-seconds}s")
   - Use SKIP concurrent execution policy
   - Add AtomicBoolean to prevent overlapping runs
   
2. Implement scheduled purge job
   - Call retentionService.purgeTtl() for time-based cleanup
   - Call retentionService.purgeBySize() for space-based cleanup
   - Call retentionService.executeVacuum() to reclaim space
   - Handle null limits gracefully (skip if not configured)
   
3. Add post-sync-batch purge trigger
   - Modify ReconciliationService.java after line 273 (batch completion)
   - Inject RetentionService and call purge methods
   - Run asynchronously to not block sync completion
   
4. Error handling and logging
   - Wrap purge calls in try-catch
   - Log errors but don't fail the job
   - Log purge results (operations deleted, space reclaimed)
   
5. Create RetentionSchedulerTest
   - Test scheduling behavior (interval configuration)
   - Test concurrent execution prevention
   - Test error handling (exceptions don't break scheduler)
   - Test that purge is called after sync batch
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
**Implementation Summary:**

Created RetentionScheduler.java at `/src/main/java/com/miimetiq/keycloak/sync/retention/RetentionScheduler.java` with:
- Scheduled retention purge job that runs every `retention.purge-interval-seconds` (default: 300s / 5 minutes)
- Post-sync purge trigger integrated into ReconciliationService

**Key Features:**
- Uses `@Scheduled` with dynamic configuration via `${retention.purge-interval-seconds}s`
- Implements overlap prevention with AtomicBoolean
- Uses SKIP concurrent execution policy
- Calls both TTL-based and space-based purge logic
- Runs VACUUM after deletions to reclaim disk space
- Graceful error handling - exceptions logged but don't break scheduler

**Integration with ReconciliationService:**
- Added RetentionScheduler injection to ReconciliationService.java:90
- Calls `triggerPostSyncPurge()` after batch completion (line 289)
- Post-sync purge skips if scheduled purge is already running
- Wrapped in try-catch to not fail reconciliation if purge fails

**Purge Execution Logic:**
- `executePurge()` method runs both purgeTtl() and purgeBySize()
- Continues with next purge type even if one fails
- Only runs VACUUM if records were deleted
- Returns PurgeResult with deletion counts for logging

**Tests Created:**
RetentionSchedulerTest.java with 11 test cases covering:
- Both purge methods are called successfully
- VACUUM is skipped if no records deleted
- TTL failure doesn't prevent size purge
- Size failure doesn't prevent VACUUM
- VACUUM failure doesn't fail entire purge
- Scheduled purge handles exceptions gracefully
- Post-sync purge executes and handles errors
- Post-sync purge skips if scheduled purge running
- isPurgeRunning() returns correct status
- Flag is reset even if purge fails

**Configuration:**
- Uses existing retention.purge-interval-seconds from RetentionConfig
- Environment variable: RETENTION_PURGE_INTERVAL_SECONDS
- Default: 300 seconds (5 minutes)

All tests pass successfully!
<!-- SECTION:NOTES:END -->

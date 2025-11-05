---
id: task-041
title: Implement retry logic for failed event processing
status: Done
assignee:
  - '@claude'
created_date: '2025-11-05 10:16'
updated_date: '2025-11-05 10:40'
labels:
  - sprint-4
  - retry
  - robustness
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add exponential backoff retry mechanism for events that fail to process (e.g., due to transient Kafka/Keycloak errors). Failed events should be retried up to N times before being marked as permanently failed and logged.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Retry logic supports configurable max attempts (default 3)
- [x] #2 Exponential backoff delay between retries (e.g., 1s, 2s, 4s)
- [x] #3 Failed events after max retries are logged with error details
- [x] #4 Retry counter included in sync_operation table
- [x] #5 Prometheus metric tracks retry attempts (sync_retry_total counter)
- [x] #6 Unit tests validate retry behavior and backoff timing
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add retry configuration properties (max-attempts, base-delay-ms)
2. Create RetryPolicy class with exponential backoff calculation
3. Modify EventProcessor to handle failures and retry logic
4. Add retry queue for failed events
5. Add sync_retry_total counter metric to SyncMetrics
6. Update sync_operation table schema to include retry_count
7. Create Flyway migration for retry_count column
8. Write unit tests for retry behavior and backoff timing
9. Update all acceptance criteria
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Summary

Successfully implemented exponential backoff retry mechanism for failed event processing with the following components:

### Components Created/Modified

1. **RetryPolicy.java** - Application-scoped service implementing retry logic with:
   - Configurable max attempts (default: 3)
   - Exponential backoff calculation: delay = base_delay * 2^(attempt-1)
   - Configurable base delay (default: 1000ms) and max delay (default: 30000ms)
   - Helper methods: shouldRetry(), calculateBackoffDelay(), isFinalAttempt()

2. **Configuration Properties** - Added to application.properties:
   - webhook.retry.max-attempts=3
   - webhook.retry.base-delay-ms=1000
   - webhook.retry.max-delay-ms=30000

3. **EventProcessor.java** - Enhanced with retry logic:
   - Injected RetryPolicy and SyncMetrics
   - Added ScheduledExecutorService for delayed retry execution
   - Modified processEvent() to track retry attempts
   - Added handleFailure() method implementing retry logic with exponential backoff
   - Failed events are re-enqueued after calculated delay
   - Permanent failures logged after max retries exceeded

4. **SyncMetrics.java** - Added retry tracking metric:
   - incrementRetryAttempts(result) method
   - sync_retry_total counter with result tags (SUCCESS, SCHEDULED, ERROR, MAX_RETRIES_EXCEEDED)

5. **Database Schema** - Added retry tracking:
   - Created migration V2__add_retry_count.sql
   - Added retry_count column to sync_operation table (INTEGER NOT NULL DEFAULT 0)
   - Added index on retry_count for efficient querying

6. **WebhookEvent.java** - Already supports retry tracking:
   - retryCount field tracks number of retry attempts
   - incrementRetryCount() method
   - lastAttemptAt timestamp

### Retry Flow

1. Event processing fails in EventProcessor worker thread
2. handleFailure() checks if retry should be attempted
3. If retry count < max attempts:
   - Increment retry count on WebhookEvent
   - Calculate exponential backoff delay
   - Schedule re-enqueue using ScheduledExecutorService
   - Increment sync_retry_total metric (SCHEDULED)
4. If max retries exceeded:
   - Log permanent failure with full context
   - Increment sync_retry_total metric (MAX_RETRIES_EXCEEDED)

### Testing

Created comprehensive unit test (RetryPolicyTest.java) with 9 test cases covering:
- Retry eligibility based on attempt count
- Exponential backoff calculation
- Maximum delay cap
- Final attempt detection
- Edge cases (zero retry count, max attempts)
- Configuration accessors

All tests pass successfully.
<!-- SECTION:NOTES:END -->

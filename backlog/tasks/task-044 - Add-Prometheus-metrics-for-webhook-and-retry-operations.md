---
id: task-044
title: Add Prometheus metrics for webhook and retry operations
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 10:16'
updated_date: '2025-11-05 16:40'
labels:
  - sprint-4
  - metrics
  - observability
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Extend the Prometheus metrics endpoint to include counters and histograms for webhook event ingestion, queue depth, retry attempts, and processing latency. These metrics enable monitoring of the event-driven synchronization pipeline.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Counter sync_webhook_received_total{realm,event_type,result} tracks incoming events
- [x] #2 Gauge sync_queue_backlog shows current queue depth
- [x] #3 Counter sync_retry_total{reason,attempt} tracks retry operations
- [x] #4 Histogram sync_webhook_processing_duration_seconds measures event processing time
- [x] #5 Counter sync_webhook_signature_failures_total tracks authentication failures
- [x] #6 All metrics exposed via GET /metrics in Prometheus format
- [x] #7 Integration test validates metric presence and accuracy
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Analyze existing metrics implementation to understand patterns and conventions
2. Add new metric methods to SyncMetrics.java:
   - incrementWebhookReceived(realm, eventType, result)
   - incrementSignatureFailure()
   - startWebhookProcessingTimer()
   - recordWebhookProcessingDuration(sample, realm, eventType)
   - Update incrementRetryAttempts to include reason and attempt tags
3. Instrument KeycloakWebhookResource to track:
   - Webhook received events with result (success/error)
   - Processing duration
4. Instrument WebhookSignatureValidator to track signature failures
5. Instrument EventProcessor to track:
   - Processing duration for each event
   - Update retry metrics to include reason and attempt number
6. Verify sync_queue_backlog gauge is properly registered (already done in EventQueueService)
7. Write integration test to validate all metrics are exposed
8. Run tests and verify all pass
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Summary

Successfully implemented comprehensive Prometheus metrics for webhook and retry operations, enabling full observability of the event-driven synchronization pipeline.

## Changes Made

### 1. Enhanced SyncMetrics.java (src/main/java/com/miimetiq/keycloak/sync/metrics/SyncMetrics.java)

Added new metric methods:
- `incrementWebhookReceived(realm, eventType, result)` - Tracks incoming webhook events with tags for realm, event type, and result (SUCCESS, ERROR, INVALID_SIGNATURE, INVALID_PAYLOAD, QUEUE_FULL)
- `incrementSignatureFailure()` - Tracks HMAC signature validation failures
- `startWebhookProcessingTimer()` - Starts timer for measuring webhook processing duration
- `recordWebhookProcessingDuration(sample, realm, eventType)` - Records webhook processing duration with tags
- Updated `incrementRetryAttempts(reason, attempt)` - Now includes both reason and attempt number tags for better observability

### 2. Instrumented KeycloakWebhookResource (src/main/java/com/miimetiq/keycloak/sync/webhook/KeycloakWebhookResource.java)

- Injected SyncMetrics dependency
- Added timing for all webhook requests using `startWebhookProcessingTimer()`
- Track webhook received events with appropriate result codes (SUCCESS, INVALID_SIGNATURE, INVALID_PAYLOAD, QUEUE_FULL, ERROR)
- Extract realm and event type from parsed events for metric tags
- Record processing duration for all webhook requests

### 3. Instrumented WebhookSignatureValidator (src/main/java/com/miimetiq/keycloak/sync/webhook/WebhookSignatureValidator.java)

- Injected SyncMetrics dependency
- Call `incrementSignatureFailure()` for all signature validation failures (missing signature, invalid signature, validation errors)

### 4. Instrumented EventProcessor (src/main/java/com/miimetiq/keycloak/sync/webhook/EventProcessor.java)

- Updated retry metrics to include reason and attempt number tags
- Track successful retries with "SUCCESS" reason
- Track scheduled retries with "SCHEDULED" reason
- Track enqueue errors with "ENQUEUE_ERROR" reason
- Track max retries exceeded with "MAX_RETRIES_EXCEEDED" reason

### 5. Fixed CircuitBreakerIntegrationTest (src/test/java/com/miimetiq/keycloak/sync/integration/CircuitBreakerIntegrationTest.java)

- Added missing `@Readiness` qualifier to health check injections to fix pre-existing dependency injection issue

### 6. Created Comprehensive Integration Tests (src/test/java/com/miimetiq/keycloak/sync/integration/WebhookMetricsIntegrationTest.java)

Created 9 integration tests covering:
- Queue backlog gauge registration and tracking
- Webhook received counter with SUCCESS result
- Webhook received counter with INVALID_SIGNATURE result
- Signature failure counter increments
- Processing duration histogram tracking
- Webhook received counter with INVALID_PAYLOAD result
- Metrics exposed via /q/metrics endpoint
- Different realms tracked separately
- Different event types tracked separately

### 7. Updated Test Configuration (src/test/resources/application.properties)

- Added `keycloak.webhook-hmac-secret` property for test environment

## Metrics Available

All metrics are now available via GET /q/metrics in Prometheus format:

1. **sync_webhook_received_total{realm,event_type,result}** - Counter tracking all incoming webhook events
2. **sync_queue_backlog** - Gauge showing current event queue depth (already implemented in EventQueueService)
3. **sync_retry_total{reason,attempt}** - Counter tracking retry operations with reason and attempt number
4. **sync_webhook_processing_duration_seconds{realm,event_type}** - Histogram measuring webhook processing latency
5. **sync_webhook_signature_failures_total** - Counter tracking authentication failures

## Testing

- All 9 integration tests pass successfully
- Existing SyncMetricsTest (10 tests) continue to pass
- No regressions introduced

## Notes

- The sync_queue_backlog gauge was already implemented in EventQueueService.java (line 53), so no changes were needed for AC #2
- Metrics follow Prometheus naming conventions and best practices
- All metrics include appropriate tags for multi-dimensional monitoring
- Integration tests use test containers for Kafka and Keycloak to ensure realistic test environment
<!-- SECTION:NOTES:END -->

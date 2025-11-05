---
id: task-036
title: Add Prometheus metrics for retention operations
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 06:17'
updated_date: '2025-11-05 09:56'
labels:
  - sprint-3
  - retention
  - metrics
  - observability
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Expose retention-related Prometheus metrics to enable monitoring of purge operations, database size, and retention policy enforcement. Metrics should align with the specification in the decision document.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Counter sync_purge_runs_total{reason} tracks purge executions (scheduled, post-batch)
- [ ] #2 Gauge sync_db_size_bytes reports current database size in bytes
- [ ] #3 Gauge sync_retention_max_bytes reports configured max_bytes limit
- [ ] #4 Gauge sync_retention_max_age_days reports configured max_age_days
- [ ] #5 Timer sync_purge_duration_seconds measures purge operation duration
- [ ] #6 Metrics are exposed at /metrics endpoint in Prometheus format
- [ ] #7 Unit tests verify metric registration and updates
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Review existing SyncMetrics.java to understand current metric patterns
2. Add new metrics to SyncMetrics class:
   - Counter sync_purge_runs_total with reason tag
   - Gauges for retention config (max_bytes, max_age_days)
   - Timer sync_purge_duration_seconds
   - Note: sync_db_size_bytes already exists
3. Instrument RetentionService.java to record purge metrics
4. Instrument RetentionScheduler.java to record scheduled vs manual purge reasons
5. Update MetricsInitializer if needed for gauge initialization
6. Write unit tests in RetentionServiceTest or new MetricsTest
7. Verify metrics are exposed at /metrics endpoint
<!-- SECTION:PLAN:END -->

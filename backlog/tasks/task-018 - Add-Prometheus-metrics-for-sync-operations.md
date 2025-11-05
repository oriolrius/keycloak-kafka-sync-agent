---
id: task-018
title: Add Prometheus metrics for sync operations
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 18:33'
updated_date: '2025-11-05 04:23'
labels:
  - backend
  - observability
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement Micrometer-based metrics for the reconciliation engine. Track counters for operations, gauges for state, and timers for performance monitoring.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Counter sync_kc_fetch_total created with labels (realm, source)
- [x] #2 Counter sync_kafka_scram_upserts_total created with labels (cluster_id, mechanism, result)
- [x] #3 Counter sync_kafka_scram_deletes_total created with labels (cluster_id, result)
- [x] #4 Gauge sync_db_size_bytes created (tracks SQLite database size)
- [x] #5 Gauge sync_last_success_epoch_seconds created (tracks last successful reconciliation)
- [x] #6 Timer sync_reconcile_duration_seconds created with labels (realm, cluster_id, source)
- [x] #7 Timer sync_admin_op_duration_seconds created with label (op)
- [x] #8 Metrics service created to centralize metric recording
- [x] #9 All counters increment in appropriate service methods
- [x] #10 All timers record in appropriate service methods
- [x] #11 All gauges update based on system state
- [x] #12 /q/metrics endpoint exposes all new metrics in Prometheus format
- [x] #13 Integration test validates metrics are incremented during reconciliation
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Review existing service structure and identify integration points
2. Create MetricsService with Micrometer instruments (counters, gauges, timers)
3. Integrate metrics into ReconciliationService, KeycloakService, and KafkaAdminService
4. Add database size gauge calculation
5. Test metrics endpoint and add integration test
6. Verify all metrics appear in /q/metrics endpoint
<!-- SECTION:PLAN:END -->

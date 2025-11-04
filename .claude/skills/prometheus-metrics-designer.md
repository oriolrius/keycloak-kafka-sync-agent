---
name: prometheus-metrics-designer
description: Expert guidance for designing and implementing Prometheus metrics using Micrometer, including counters, gauges, histograms, label strategies, and recording rules
---

# Prometheus Metrics Designer

Comprehensive guide for implementing observability with Prometheus and Micrometer in Quarkus applications.

## Setup

### Dependencies (Maven)

```xml
<dependencies>
    <!-- Micrometer core -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
    </dependency>

    <!-- Optional: additional metrics -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-micrometer</artifactId>
    </dependency>
</dependencies>
```

### Configuration

```properties
# Enable Prometheus endpoint
quarkus.micrometer.export.prometheus.enabled=true
quarkus.micrometer.export.prometheus.path=/metrics

# Enable additional binders
quarkus.micrometer.binder.jvm=true
quarkus.micrometer.binder.system=true
quarkus.micrometer.binder.http-server.enabled=true

# Common tags (applied to all metrics)
quarkus.micrometer.binder.common-tags.application=sync-agent
quarkus.micrometer.binder.common-tags.environment=${ENVIRONMENT:dev}
```

## Metric Types

### 1. Counter (Monotonically Increasing)

**Use for**: Events, requests, errors, operations completed

```java
@ApplicationScoped
public class SyncMetrics {

    @Inject
    MeterRegistry registry;

    private Counter syncSuccessCounter;
    private Counter syncErrorCounter;

    @PostConstruct
    void init() {
        // Basic counter
        syncSuccessCounter = Counter.builder("sync.operations.total")
            .tag("result", "success")
            .description("Total successful sync operations")
            .baseUnit("operations")
            .register(registry);

        // Counter with multiple tags
        syncErrorCounter = Counter.builder("sync.operations.total")
            .tag("result", "error")
            .description("Total failed sync operations")
            .register(registry);
    }

    public void recordSuccess(String opType, String realm) {
        Counter.builder("sync.operations.total")
            .tag("result", "success")
            .tag("op_type", opType)
            .tag("realm", realm)
            .register(registry)
            .increment();
    }

    public void recordError(String opType, String realm, String errorCode) {
        Counter.builder("sync.operations.total")
            .tag("result", "error")
            .tag("op_type", opType)
            .tag("realm", realm)
            .tag("error_code", errorCode)
            .register(registry)
            .increment();
    }

    // Increment by amount
    public void recordBatchSuccess(int count) {
        syncSuccessCounter.increment(count);
    }
}
```

**Prometheus Queries:**
```promql
# Total operations
sum(sync_operations_total)

# Success rate
sum(rate(sync_operations_total{result="success"}[5m]))
/ sum(rate(sync_operations_total[5m]))

# Error rate by type
sum by (op_type) (rate(sync_operations_total{result="error"}[5m]))
```

### 2. Gauge (Current Value)

**Use for**: Current size, queue length, active connections, resource usage

```java
@ApplicationScoped
public class ResourceMetrics {

    @Inject
    MeterRegistry registry;

    @Inject
    RetentionService retentionService;

    private AtomicLong queueSize = new AtomicLong(0);

    @PostConstruct
    void init() {
        // Gauge with supplier
        Gauge.builder("sync.db.size.bytes", this::getDbSize)
            .description("Current database size in bytes")
            .baseUnit("bytes")
            .register(registry);

        // Gauge with object
        Gauge.builder("sync.queue.backlog", queueSize, AtomicLong::get)
            .description("Number of pending sync operations")
            .baseUnit("operations")
            .register(registry);

        // Multi-state gauge
        Gauge.builder("sync.last.success.timestamp", this::getLastSuccessEpoch)
            .description("Timestamp of last successful sync")
            .baseUnit("seconds")
            .register(registry);
    }

    private double getDbSize() {
        return (double) retentionService.getDatabaseSize();
    }

    private double getLastSuccessEpoch() {
        return Instant.now().getEpochSecond();
    }

    public void incrementQueue() {
        queueSize.incrementAndGet();
    }

    public void decrementQueue() {
        queueSize.decrementAndGet();
    }
}
```

**Prometheus Queries:**
```promql
# Current DB size
sync_db_size_bytes

# Time since last success
time() - sync_last_success_timestamp

# Average queue size
avg_over_time(sync_queue_backlog[5m])
```

### 3. Timer/Histogram (Duration and Distribution)

**Use for**: Request duration, operation latency, size distribution

```java
@ApplicationScoped
public class PerformanceMetrics {

    @Inject
    MeterRegistry registry;

    private Timer reconcileTimer;

    @PostConstruct
    void init() {
        // Timer with percentiles
        reconcileTimer = Timer.builder("sync.reconcile.duration")
            .description("Reconciliation cycle duration")
            .tag("source", "periodic")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(100))
            .maximumExpectedValue(Duration.ofMinutes(5))
            .register(registry);
    }

    // Method 1: Record with Timer.Sample
    public ReconcileResult timedReconcile() {
        Timer.Sample sample = Timer.start(registry);

        try {
            ReconcileResult result = performReconcile();
            sample.stop(reconcileTimer);
            return result;
        } catch (Exception e) {
            sample.stop(Timer.builder("sync.reconcile.duration")
                .tag("source", "periodic")
                .tag("result", "error")
                .register(registry));
            throw e;
        }
    }

    // Method 2: Record with lambda
    public ReconcileResult recordReconcile() {
        return reconcileTimer.record(() -> performReconcile());
    }

    // Method 3: Record manually
    public void recordDuration(long durationMs, String opType) {
        Timer.builder("sync.admin.operation.duration")
            .tag("operation", opType)
            .register(registry)
            .record(Duration.ofMillis(durationMs));
    }

    private ReconcileResult performReconcile() {
        // implementation
        return null;
    }
}
```

**Using Annotations:**
```java
@Timed(value = "sync.reconcile.duration",
       description = "Reconciliation cycle duration",
       extraTags = {"source", "periodic"},
       percentiles = {0.5, 0.95, 0.99})
public ReconcileResult reconcile() {
    // implementation
}

@Counted(value = "sync.reconcile.calls",
         description = "Number of reconciliation calls")
public void triggerReconcile() {
    // implementation
}
```

**Prometheus Queries:**
```promql
# Average duration
rate(sync_reconcile_duration_sum[5m]) /
rate(sync_reconcile_duration_count[5m])

# 95th percentile
histogram_quantile(0.95, sum(rate(sync_reconcile_duration_bucket[5m])) by (le))

# p99 latency
sync_reconcile_duration{quantile="0.99"}
```

### 4. Summary (Quantiles without Histograms)

**Use for**: When histograms are too expensive

```java
DistributionSummary.builder("sync.batch.size")
    .description("Number of items per batch")
    .baseUnit("items")
    .publishPercentiles(0.5, 0.95, 0.99)
    .register(registry)
    .record(batchSize);
```

## Comprehensive Metrics for Sync Agent

```java
@ApplicationScoped
public class SyncAgentMetrics {

    @Inject
    MeterRegistry registry;

    // === COUNTERS ===

    // Keycloak operations
    private Counter kcFetchCounter(String realm, String source) {
        return Counter.builder("sync.kc.fetch.total")
            .tag("realm", realm)
            .tag("source", source)  // webhook, periodic
            .register(registry);
    }

    // Kafka SCRAM operations
    private Counter kafkaScramCounter(String clusterId, String mechanism, String operation, String result) {
        return Counter.builder("sync.kafka.scram.operations.total")
            .tag("cluster_id", clusterId)
            .tag("mechanism", mechanism)  // SCRAM-SHA-256, SCRAM-SHA-512
            .tag("operation", operation)  // upsert, delete
            .tag("result", result)        // success, error
            .register(registry);
    }

    // Kafka ACL operations
    private Counter kafkaAclCounter(String clusterId, String operation, String result) {
        return Counter.builder("sync.kafka.acl.operations.total")
            .tag("cluster_id", clusterId)
            .tag("operation", operation)  // create, delete
            .tag("result", result)
            .register(registry);
    }

    // Purge operations
    private Counter purgeCounter(String reason) {
        return Counter.builder("sync.purge.runs.total")
            .tag("reason", reason)  // age, size, manual
            .register(registry);
    }

    private Counter purgeRecordsCounter(String reason) {
        return Counter.builder("sync.purge.records.total")
            .tag("reason", reason)
            .register(registry);
    }

    // === GAUGES ===

    @PostConstruct
    void initGauges() {
        // Database metrics
        Gauge.builder("sync.db.size.bytes", this::getDbSize)
            .description("Current database size")
            .register(registry);

        Gauge.builder("sync.retention.max.bytes", this::getMaxBytes)
            .description("Maximum allowed database size")
            .register(registry);

        Gauge.builder("sync.retention.max.age.days", this::getMaxAgeDays)
            .description("Maximum retention age")
            .register(registry);

        // Queue metrics
        Gauge.builder("sync.queue.backlog", this::getQueueSize)
            .description("Pending operations in queue")
            .register(registry);

        // Last success timestamp
        Gauge.builder("sync.last.success.timestamp", this::getLastSuccessEpoch)
            .description("Unix timestamp of last successful sync")
            .baseUnit("seconds")
            .register(registry);
    }

    // === TIMERS ===

    // Reconciliation duration
    public Timer reconcileTimer(String realm, String clusterId, String source) {
        return Timer.builder("sync.reconcile.duration")
            .tag("realm", realm)
            .tag("cluster_id", clusterId)
            .tag("source", source)
            .description("Time to complete reconciliation")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    // Individual admin operations
    public Timer adminOpTimer(String operation) {
        return Timer.builder("sync.admin.operation.duration")
            .tag("operation", operation)  // describeUsers, alterScram, createAcl
            .description("Time for individual admin operations")
            .publishPercentiles(0.95, 0.99)
            .register(registry);
    }

    // Purge duration
    public Timer purgeTimer() {
        return Timer.builder("sync.purge.duration")
            .description("Time to complete purge operation")
            .register(registry);
    }

    // === DISTRIBUTION SUMMARIES ===

    public DistributionSummary batchSizeSummary() {
        return DistributionSummary.builder("sync.batch.size")
            .description("Number of items per batch")
            .baseUnit("items")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    // === Helper methods ===
    private double getDbSize() { /* implementation */ return 0.0; }
    private double getMaxBytes() { /* implementation */ return 0.0; }
    private double getMaxAgeDays() { /* implementation */ return 0.0; }
    private double getQueueSize() { /* implementation */ return 0.0; }
    private double getLastSuccessEpoch() { /* implementation */ return 0.0; }

    // === Public recording methods ===

    public void recordKcFetch(String realm, String source) {
        kcFetchCounter(realm, source).increment();
    }

    public void recordScramOperation(String clusterId, String mechanism,
                                     String operation, boolean success) {
        kafkaScramCounter(clusterId, mechanism, operation,
            success ? "success" : "error").increment();
    }

    public void recordAclOperation(String clusterId, String operation, boolean success) {
        kafkaAclCounter(clusterId, operation,
            success ? "success" : "error").increment();
    }

    public void recordPurge(String reason, int recordsPurged, Runnable operation) {
        purgeCounter(reason).increment();
        purgeRecordsCounter(reason).increment(recordsPurged);
        purgeTimer().record(operation);
    }

    public ReconcileResult recordReconcile(String realm, String clusterId, String source,
                                          Supplier<ReconcileResult> operation) {
        return reconcileTimer(realm, clusterId, source).record(operation);
    }
}
```

## Label Strategy Best Practices

### 1. Cardinality Control

**Good Labels** (Low cardinality):
- `result`: success, error
- `op_type`: upsert, delete, create
- `source`: webhook, periodic, manual
- `mechanism`: SCRAM-SHA-256, SCRAM-SHA-512
- `environment`: dev, staging, production

**Bad Labels** (High cardinality - AVOID):
- `principal`: alice, bob, ... (thousands of users)
- `correlation_id`: UUID (unique per operation)
- `timestamp`: epoch seconds
- `error_message`: full error text

### 2. Label Naming

```java
// Use snake_case for labels
.tag("cluster_id", clusterId)
.tag("op_type", opType)
.tag("error_code", errorCode)

// Consistent naming across metrics
.tag("result", "success")  // Always use "result", not "status" or "outcome"
```

### 3. Label Values

```java
// Normalize label values
String normalizedOpType = opType.toUpperCase();  // UPSERT, DELETE
String normalizedResult = success ? "success" : "error";

// Limit enum values
String mechanism = switch(scramMechanism) {
    case SCRAM_SHA_256 -> "SCRAM-SHA-256";
    case SCRAM_SHA_512 -> "SCRAM-SHA-512";
    default -> "UNKNOWN";
};
```

## Prometheus Configuration

### prometheus.yml

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'sync-agent'
    static_configs:
      - targets: ['sync-agent:8088']
    metrics_path: '/metrics'
    scrape_interval: 10s
```

## Recording Rules

**prometheus-rules.yml:**
```yaml
groups:
  - name: sync_agent_rules
    interval: 30s
    rules:
      # Error ratio
      - record: sync:error_ratio
        expr: |
          sum(rate(sync_operations_total{result="error"}[5m]))
          /
          sum(rate(sync_operations_total[5m]))

      # Average reconcile duration
      - record: sync:reconcile_duration_avg
        expr: |
          rate(sync_reconcile_duration_sum[5m])
          /
          rate(sync_reconcile_duration_count[5m])

      # Operations per second by type
      - record: sync:ops_per_second
        expr: |
          sum by (op_type) (rate(sync_operations_total[1m]))

      # DB usage percentage
      - record: sync:db_usage_percent
        expr: |
          (sync_db_size_bytes / sync_retention_max_bytes) * 100
```

## Alert Rules

**prometheus-alerts.yml:**
```yaml
groups:
  - name: sync_agent_alerts
    rules:
      # High error rate
      - alert: HighErrorRate
        expr: sync:error_ratio > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value | humanizePercentage }} for 5 minutes"

      # Reconciliation taking too long
      - alert: SlowReconciliation
        expr: sync:reconcile_duration_avg > 300
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Reconciliation is slow"
          description: "Average duration is {{ $value }}s"

      # Database near capacity
      - alert: DatabaseNearCapacity
        expr: sync:db_usage_percent > 90
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Database near capacity"
          description: "Database is {{ $value }}% full"

      # No successful sync recently
      - alert: NoRecentSync
        expr: time() - sync_last_success_timestamp > 600
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "No successful sync in 10 minutes"
          description: "Last success was {{ $value }}s ago"

      # Queue backlog growing
      - alert: QueueBacklogGrowing
        expr: |
          deriv(sync_queue_backlog[5m]) > 0 and
          sync_queue_backlog > 100
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Queue backlog is growing"
          description: "{{ $value }} operations pending"
```

## Useful PromQL Queries

```promql
# === Rate Queries ===

# Operations per second
sum(rate(sync_operations_total[5m]))

# Error rate
sum(rate(sync_operations_total{result="error"}[5m]))

# Success rate percentage
(
  sum(rate(sync_operations_total{result="success"}[5m]))
  /
  sum(rate(sync_operations_total[5m]))
) * 100

# === Latency Queries ===

# p99 latency
histogram_quantile(0.99,
  sum(rate(sync_reconcile_duration_bucket[5m])) by (le, source)
)

# Average duration by operation
sum by (operation) (
  rate(sync_admin_operation_duration_sum[5m])
  /
  rate(sync_admin_operation_duration_count[5m])
)

# === Aggregations ===

# Total operations by type
sum by (op_type) (sync_operations_total)

# Error breakdown
sum by (error_code) (sync_operations_total{result="error"})

# Operations per realm
sum by (realm) (rate(sync_operations_total[5m]))

# === Trending ===

# Change in queue size over 10 minutes
delta(sync_queue_backlog[10m])

# Database growth rate (bytes/sec)
deriv(sync_db_size_bytes[5m])

# === Predictions ===

# When will database be full (hours)
(sync_retention_max_bytes - sync_db_size_bytes)
/
deriv(sync_db_size_bytes[1h]) / 3600
```

## Grafana Dashboard JSON Snippet

```json
{
  "dashboard": {
    "title": "Sync Agent Monitoring",
    "panels": [
      {
        "title": "Operations Rate",
        "targets": [
          {
            "expr": "sum(rate(sync_operations_total{result='success'}[5m]))",
            "legendFormat": "Success"
          },
          {
            "expr": "sum(rate(sync_operations_total{result='error'}[5m]))",
            "legendFormat": "Error"
          }
        ],
        "type": "graph"
      },
      {
        "title": "p99 Latency",
        "targets": [
          {
            "expr": "histogram_quantile(0.99, sum(rate(sync_reconcile_duration_bucket[5m])) by (le))",
            "legendFormat": "p99"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Database Size",
        "targets": [
          {
            "expr": "sync_db_size_bytes",
            "legendFormat": "Current"
          },
          {
            "expr": "sync_retention_max_bytes",
            "legendFormat": "Max"
          }
        ],
        "type": "graph"
      }
    ]
  }
}
```

## Best Practices Summary

1. **Name metrics clearly**: Use verb_noun_unit pattern (e.g., `sync_operations_total`)
2. **Control cardinality**: Limit label value combinations (<100 per metric)
3. **Use appropriate types**: Counters for totals, Gauges for current values, Histograms for distributions
4. **Add descriptions**: Always include `.description()` for metrics
5. **Include units**: Use `.baseUnit()` for clarity
6. **Publish percentiles**: For latency metrics, expose p50, p95, p99
7. **Create recording rules**: Pre-compute expensive queries
8. **Set up alerts**: Monitor error rates, latency, and resource usage
9. **Test metrics**: Verify metrics appear in /metrics endpoint
10. **Document queries**: Keep a runbook of useful PromQL queries

## Resources

- Micrometer Documentation: https://micrometer.io/docs
- Prometheus Best Practices: https://prometheus.io/docs/practices/
- PromQL Guide: https://prometheus.io/docs/prometheus/latest/querying/basics/
- Quarkus Micrometer: https://quarkus.io/guides/micrometer

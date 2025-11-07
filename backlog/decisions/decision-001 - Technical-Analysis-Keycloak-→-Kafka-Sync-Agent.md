---
id: decision-001
title: 'Technical Analysis: Keycloak → Kafka Sync Agent'
date: '2025-11-04 13:47'
status: proposed
---
## Technical Analysis: Keycloak → Kafka Sync Agent (Enhanced Edition)

### 1. Service Objectives

1. **Synchronize identities and permissions** (Keycloak → Kafka/KRaft) using the AdminClient:

   * `AlterUserScramCredentials` for upsert/delete SCRAM verifiers.
   * `CreateAcls`, `DeleteAcls`, `DescribeAcls` (if ACL management is enabled).
2. **Persist synchronization history** in SQLite with retention policies (time/space based circular queue and incremental purge).
3. **Expose observability endpoints** for Prometheus telemetry (`/metrics`) and health (`/healthz`, `/readyz`).
4. **Serve a modern HTML UI** (SPA built with React + shadcn/ui) summarizing telemetry, connection status, and synchronization history.

---

### 2. Technical Architecture

#### Backend (Java)

* **Framework:** Quarkus (chosen for performance and native image support).
* **Kafka Admin:** `org.apache.kafka.clients.admin.AdminClient` for SCRAM and ACL management.
* **Keycloak Admin:** official Keycloak Java Admin client or REST client using `WebClient`.
* **Persistence:** SQLite (JDBC) with Flyway for schema migrations.
* **Observability:** Micrometer + `micrometer-registry-prometheus` exposing `/metrics`.
* **HTTP layer:** Quarkus RESTEasy Reactive (OpenAPI documented endpoints).
* **Security:** optional mTLS for internal calls; Basic/API key authentication for the dashboard and admin endpoints. The service may also integrate with Keycloak itself as an OIDC client. In that case, only users with a specific Keycloak role can access the dashboard or admin APIs.

#### Frontend (UI)

* **Stack:** Vite + React 18 + shadcn/ui (Tailwind) + TanStack Query for data management + Recharts or Chart.js for graphs.
* **Build:** single-page application (SPA) served as static resources from the backend.
* **Routing:** `/dashboard` (main UI) with all REST APIs under `/api`.

---

### 3. Core Flows

1. **Keycloak Event Ingestion** (two modes used simultaneously):

   * **Periodic Reconcile:** every N seconds (default 120s), the agent fetches all Keycloak users, clients, and roles to compute and apply diffs.
   * **Webhook Events:** Keycloak sends Admin Events (`POST /api/kc/events`); the agent validates the signature and enqueues processing tasks.
   * The agent always performs periodic reconciliation to recover from potential missed webhook events.
2. **Materialization into Kafka:**

   * Compute diffs between Keycloak and Kafka states (upserts/deletes).
   * Execute batch AdminClient operations.
   * Log all results into SQLite (`sync_operation`, `sync_batch`).
3. **Round-Robin Retention:**

   * **By Space:** limit in MB/GB — purge oldest entries until below threshold.
   * **By Time:** TTL (days) — purge records older than `now() - TTL`.
   * **Execution:** scheduled after each batch and periodically every N minutes.
   * **Guarantees:** idempotent operations with transactional integrity.
4. **Telemetry:**

   * Prometheus counters, gauges, and histograms for each phase (Keycloak fetch, diff computation, Kafka admin operations, purges).
   * Labels: `realm`, `cluster`, `op_type` (UPSERT/DELETE), `status` (SUCCESS/ERROR), `retry`.
5. **User Interface:**

   * Dashboard provides real-time connection status with Kafka and Keycloak.
   * Shows whether the last communication succeeded or failed and the timestamp of the last attempt.
   * Includes a manual **“Force Reconcile”** button to trigger an immediate sync.
   * Links to sections:

     * Trends panel (24/72h): operations volume, latencies, error rate.
     * Operation timeline (paginated table with filters).
     * Current principals and ACL summary.
     * Retention status (quota usage and policies).

---

### 4. Database Schema (SQLite)

**Migration V1__init.sql (Flyway)**

```sql
CREATE TABLE sync_operation (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  correlation_id TEXT NOT NULL,
  occurred_at DATETIME NOT NULL,
  realm TEXT NOT NULL,
  cluster_id TEXT NOT NULL,
  principal TEXT NOT NULL,
  op_type TEXT NOT NULL,
  mechanism TEXT,
  result TEXT NOT NULL,
  error_code TEXT,
  error_message TEXT,
  duration_ms INTEGER NOT NULL
);

CREATE INDEX idx_sync_operation_time ON sync_operation(occurred_at);
CREATE INDEX idx_sync_operation_principal ON sync_operation(principal);
CREATE INDEX idx_sync_operation_type ON sync_operation(op_type);

CREATE TABLE sync_batch (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  correlation_id TEXT NOT NULL UNIQUE,
  started_at DATETIME NOT NULL,
  finished_at DATETIME,
  source TEXT NOT NULL,
  items_total INTEGER NOT NULL,
  items_success INTEGER NOT NULL DEFAULT 0,
  items_error INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_sync_batch_time ON sync_batch(started_at);

CREATE TABLE retention_state (
  id INTEGER PRIMARY KEY CHECK (id=1),
  max_bytes INTEGER,
  max_age_days INTEGER,
  approx_db_bytes INTEGER NOT NULL,
  updated_at DATETIME NOT NULL
);

INSERT INTO retention_state(id, max_bytes, max_age_days, approx_db_bytes, updated_at)
VALUES (1, NULL, 30, 0, DATETIME('now'));
```

**Retention Details**

* Disk usage computed via `PRAGMA page_count * page_size`.
* Purge strategy: delete by TTL and/or oldest entries until under `max_bytes`.
* Conditional `VACUUM` for reclaiming space.

---

### 5. Backend Endpoints

**Health and Metrics**

* `GET /healthz` → `{status:"UP", details:{kafka:"UP", keycloak:"UP", sqlite:"UP"}}`
* `GET /readyz` → readiness check (Kafka reachable, DB OK)
* `GET /metrics` → Prometheus exposition format

**API for Internal/UI Use**

* `GET /api/summary` → Summary for dashboard cards: ops/hour, error rate, latency p95/p99, DB usage.
* `GET /api/operations` → Paginated timeline (filters by time range, principal, op_type, result).
* `GET /api/batches` → Batch summaries.
* `GET|PUT /api/config/retention` → Read/update retention policies.
* `POST /api/kc/events` → Validated webhook for Keycloak Admin Events.

**OpenAPI**

* Generated with `quarkus-smallrye-openapi` for contract documentation.

---

### 6. Prometheus Metrics

**Counters**

* `sync_kc_fetch_total{realm,source}` – Keycloak fetches.
* `sync_kafka_scram_upserts_total{cluster_id,mechanism,result}`
* `sync_kafka_scram_deletes_total{cluster_id,result}`
* `sync_kafka_acl_changes_total{cluster_id,op,result}`
* `sync_purge_runs_total{reason}`

**Gauges**

* `sync_db_size_bytes`
* `sync_retention_max_bytes`
* `sync_retention_max_age_days`
* `sync_queue_backlog`
* `sync_last_success_epoch_seconds`

**Histograms/Timers**

* `sync_reconcile_duration_seconds{realm,cluster_id,source}`
* `sync_admin_op_duration_seconds{op}`
* `sync_purge_duration_seconds`

**Ratios (recording rules)**

* `sync_error_ratio = errors / total`

---

### 7. UI (React + shadcn/ui)

**Pages and Components**

* **Dashboard:** cards, charts, summary KPIs.
* **Operation Timeline:** paginated table, filters, CSV export.
* **Batch Summary:** reconciliation cycles.
* **Retention Panel:** quota visualization and control.

**Data Management**

* TanStack Query for caching and polling (default 10s refresh).
* Feature flags to hide ACL-related UI if ACL management is disabled.

**Authentication**

* Basic Auth or reverse-proxy OIDC (Keycloak) for secured internal access.

---

### 8. Configuration (Environment Variables)

```bash
KAFKA_BOOTSTRAP_SERVERS=kafka:9093
KAFKA_SECURITY_PROTOCOL=SASL_SSL
KAFKA_SASL_MECHANISM=SCRAM-SHA-512
KAFKA_SASL_JAAS='org.apache.kafka.common.security.scram.ScramLoginModule required username="sync-admin" password="***";'

KC_BASE_URL=http://keycloak:8080
KC_REALM=master
KC_CLIENT_ID=sync-agent
KC_CLIENT_SECRET=***
KC_WEBHOOK_HMAC_SECRET=***

RECONCILE_INTERVAL_SECONDS=120
RECONCILE_PAGE_SIZE=500

RETENTION_MAX_BYTES=268435456
RETENTION_MAX_AGE_DAYS=30
RETENTION_PURGE_INTERVAL_SECONDS=300

SERVER_PORT=8088
DASHBOARD_BASIC_AUTH=admin:******
```

---

### 9. Retention Strategy (Round-Robin)

1. **Time-based purge (TTL):** delete rows where `occurred_at < now() - max_age_days`.
2. **Space-based purge:** compute `db_bytes = page_count * page_size`; if exceeded:

   * Delete oldest N rows until under limit.
   * Update `retention_state.approx_db_bytes`.
   * Optionally execute `VACUUM`.
3. **Hooks:** executed post-batch and periodically.

---

### 10. Testing & Quality Assurance

* **Unit Tests:** Keycloak → Kafka mapping, diff computation, SCRAM verifier generation, retention logic.
* **Contract Tests:** AdminClient mock or Testcontainers (Kafka 4.1.0).
* **Database Tests:** SQLite in-memory + Flyway migrations.
* **E2E Tests:** Docker Compose with Keycloak + Kafka + Sync Agent + UI.
* **Performance Tests:** simulate 10k principals, evaluate latency and retention behavior.
* **Observability Validation:** confirm `/metrics` exposure, Prometheus integration, and alerting rules.

---

### 11. Roadmap (Suggested Sprints)

1. **Sprint 1 – Core Skeleton & Connectivity:** health endpoints, Micrometer, Kafka + Keycloak clients, SQLite base.
2. **Sprint 2 – Reconciliation Engine:** diff detection, SCRAM upsert/delete, basic metrics.
3. **Sprint 3 – Retention System:** TTL and space limit enforcement, `/api/config/retention` API.
4. **Sprint 4 – Keycloak Webhook & Robustness:** webhook endpoint, signature verification, queue and retry logic.
5. **Sprint 5 – UI Development:** React + shadcn layout, trends charts, filters.
6. **Sprint 6 – ACL Management (Optional):** role-to-ACL mapping and visualization.
7. **Sprint 7 – Hardening:** mTLS, secret management, Prometheus/Grafana dashboards, alerts.

---

### 12. Deployment Example (Docker Compose)

```yaml
services:
  sync-agent:
    image: ghcr.io/oriolrius/sync-agent:latest
    ports: ["8088:8088"]
    depends_on: [kafka, keycloak]
    volumes:
      - ./data:/data
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8088/healthz"]
      interval: 10s
      timeout: 3s
      retries: 10
```

Kafka and Keycloak use their official images — no modification required.

---

### 13. Summary

This design describes a stable, observable Java service that synchronizes Keycloak and Kafka identities and ACLs, persists operation history with limited growth, exposes Prometheus metrics for SREs, and delivers a modern UI for operations teams. The roadmap enables incremental delivery while preserving compatibility with standard Keycloak and Kafka images.

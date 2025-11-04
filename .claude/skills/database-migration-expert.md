---
name: database-migration-expert
description: Expert guidance for database schema migrations using Flyway with SQLite, including versioning, rollback strategies, and circular queue patterns for retention
---

# Database Migration Expert

Comprehensive guide for managing database migrations with Flyway, optimized for SQLite with focus on retention policies and circular queue patterns.

## Flyway Setup with Quarkus

### Dependencies (Maven)

```xml
<dependencies>
    <!-- Flyway -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-flyway</artifactId>
    </dependency>

    <!-- SQLite JDBC -->
    <dependency>
        <groupId>org.xerial</groupId>
        <artifactId>sqlite-jdbc</artifactId>
        <version>3.44.0.0</version>
    </dependency>

    <!-- JDBC -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-jdbc-h2</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-agroal</artifactId>
    </dependency>
</dependencies>
```

### Configuration (application.properties)

```properties
# Datasource configuration
quarkus.datasource.db-kind=other
quarkus.datasource.jdbc.driver=org.sqlite.JDBC
quarkus.datasource.jdbc.url=jdbc:sqlite:/data/sync.db

# Flyway configuration
quarkus.flyway.migrate-at-start=true
quarkus.flyway.baseline-on-migrate=true
quarkus.flyway.baseline-version=0
quarkus.flyway.locations=db/migration
quarkus.flyway.validate-on-migrate=true
quarkus.flyway.out-of-order=false
quarkus.flyway.clean-disabled=true

# SQLite-specific PRAGMA settings
quarkus.datasource.jdbc.initial-sql=PRAGMA journal_mode=WAL;PRAGMA foreign_keys=ON;PRAGMA synchronous=NORMAL;
```

## Migration File Structure

```
src/main/resources/
└── db/
    └── migration/
        ├── V1__init.sql
        ├── V2__add_indices.sql
        ├── V3__add_retention_state.sql
        └── R__views.sql  (Repeatable)
```

## Initial Schema (V1__init.sql)

```sql
-- V1__init.sql
-- Initial schema for Keycloak → Kafka sync agent

-- Sync operations table (main event log)
CREATE TABLE sync_operation (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    correlation_id TEXT NOT NULL,
    occurred_at DATETIME NOT NULL DEFAULT (datetime('now')),
    realm TEXT NOT NULL,
    cluster_id TEXT NOT NULL,
    principal TEXT NOT NULL,
    op_type TEXT NOT NULL CHECK(op_type IN ('UPSERT_SCRAM', 'DELETE_SCRAM', 'CREATE_ACL', 'DELETE_ACL')),
    mechanism TEXT,  -- SCRAM-SHA-256, SCRAM-SHA-512
    result TEXT NOT NULL CHECK(result IN ('SUCCESS', 'ERROR', 'SKIPPED')),
    error_code TEXT,
    error_message TEXT,
    duration_ms INTEGER NOT NULL,
    metadata TEXT  -- JSON for additional context
);

-- Index for common queries
CREATE INDEX idx_sync_operation_time ON sync_operation(occurred_at DESC);
CREATE INDEX idx_sync_operation_principal ON sync_operation(principal);
CREATE INDEX idx_sync_operation_type ON sync_operation(op_type);
CREATE INDEX idx_sync_operation_result ON sync_operation(result);
CREATE INDEX idx_sync_operation_correlation ON sync_operation(correlation_id);

-- Sync batch table (reconciliation cycles)
CREATE TABLE sync_batch (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    correlation_id TEXT NOT NULL UNIQUE,
    started_at DATETIME NOT NULL DEFAULT (datetime('now')),
    finished_at DATETIME,
    source TEXT NOT NULL CHECK(source IN ('WEBHOOK', 'PERIODIC', 'MANUAL')),
    items_total INTEGER NOT NULL DEFAULT 0,
    items_success INTEGER NOT NULL DEFAULT 0,
    items_error INTEGER NOT NULL DEFAULT 0,
    duration_ms INTEGER,
    error_summary TEXT
);

CREATE INDEX idx_sync_batch_time ON sync_batch(started_at DESC);
CREATE INDEX idx_sync_batch_source ON sync_batch(source);

-- Retention state (singleton table)
CREATE TABLE retention_state (
    id INTEGER PRIMARY KEY CHECK(id = 1),
    max_bytes INTEGER,
    max_age_days INTEGER,
    approx_db_bytes INTEGER NOT NULL DEFAULT 0,
    last_purge_at DATETIME,
    total_purged_records INTEGER NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT (datetime('now'))
);

-- Initialize retention state
INSERT INTO retention_state(id, max_bytes, max_age_days, approx_db_bytes, updated_at)
VALUES (1, 268435456, 30, 0, datetime('now'));

-- Schema version info (for debugging)
CREATE TABLE schema_info (
    version TEXT PRIMARY KEY,
    applied_at DATETIME NOT NULL DEFAULT (datetime('now')),
    description TEXT
);

INSERT INTO schema_info(version, description) VALUES ('1.0.0', 'Initial schema');
```

## Advanced Patterns

### V2__add_indices.sql (Performance Optimization)

```sql
-- V2__add_indices.sql
-- Additional indices for performance

-- Composite index for filtered queries
CREATE INDEX idx_sync_operation_principal_time ON sync_operation(principal, occurred_at DESC);
CREATE INDEX idx_sync_operation_type_result ON sync_operation(op_type, result);

-- Index for retention queries
CREATE INDEX idx_sync_operation_id_time ON sync_operation(id, occurred_at);

-- Covering index for summary queries
CREATE INDEX idx_sync_operation_summary ON sync_operation(occurred_at, result, op_type, duration_ms);

INSERT INTO schema_info(version, description) VALUES ('2.0.0', 'Performance indices');
```

### V3__add_acl_tracking.sql (Feature Addition)

```sql
-- V3__add_acl_tracking.sql
-- Add ACL tracking capability

-- ACL snapshot table
CREATE TABLE acl_snapshot (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    principal TEXT NOT NULL,
    resource_type TEXT NOT NULL,  -- TOPIC, GROUP, CLUSTER
    resource_name TEXT NOT NULL,
    operation TEXT NOT NULL,      -- READ, WRITE, CREATE, etc.
    permission_type TEXT NOT NULL CHECK(permission_type IN ('ALLOW', 'DENY')),
    host TEXT NOT NULL DEFAULT '*',
    synced_at DATETIME NOT NULL DEFAULT (datetime('now')),
    UNIQUE(principal, resource_type, resource_name, operation, host)
);

CREATE INDEX idx_acl_snapshot_principal ON acl_snapshot(principal);
CREATE INDEX idx_acl_snapshot_resource ON acl_snapshot(resource_type, resource_name);

-- Add ACL metadata to operations
ALTER TABLE sync_operation ADD COLUMN acl_resource_type TEXT;
ALTER TABLE sync_operation ADD COLUMN acl_resource_name TEXT;
ALTER TABLE sync_operation ADD COLUMN acl_operation TEXT;

INSERT INTO schema_info(version, description) VALUES ('3.0.0', 'ACL tracking');
```

### R__views.sql (Repeatable - Views)

```sql
-- R__views.sql
-- Repeatable migration for views (always re-run on change)

-- Drop existing views
DROP VIEW IF EXISTS v_operation_summary;
DROP VIEW IF EXISTS v_error_summary;
DROP VIEW IF EXISTS v_batch_summary;

-- Operation summary view
CREATE VIEW v_operation_summary AS
SELECT
    DATE(occurred_at) as date,
    op_type,
    result,
    COUNT(*) as count,
    AVG(duration_ms) as avg_duration_ms,
    MIN(duration_ms) as min_duration_ms,
    MAX(duration_ms) as max_duration_ms
FROM sync_operation
GROUP BY DATE(occurred_at), op_type, result;

-- Error summary view
CREATE VIEW v_error_summary AS
SELECT
    error_code,
    COUNT(*) as error_count,
    MAX(occurred_at) as last_occurrence,
    GROUP_CONCAT(DISTINCT principal) as affected_principals
FROM sync_operation
WHERE result = 'ERROR'
GROUP BY error_code;

-- Batch summary view
CREATE VIEW v_batch_summary AS
SELECT
    DATE(started_at) as date,
    source,
    COUNT(*) as batch_count,
    SUM(items_total) as total_items,
    SUM(items_success) as total_success,
    SUM(items_error) as total_errors,
    AVG(duration_ms) as avg_duration_ms
FROM sync_batch
GROUP BY DATE(started_at), source;

-- Recent operations view (for dashboard)
CREATE VIEW v_recent_operations AS
SELECT
    o.id,
    o.correlation_id,
    o.occurred_at,
    o.principal,
    o.op_type,
    o.result,
    o.duration_ms,
    b.source as batch_source
FROM sync_operation o
LEFT JOIN sync_batch b ON o.correlation_id = b.correlation_id
ORDER BY o.occurred_at DESC
LIMIT 1000;
```

## Retention and Purge Patterns

### Purge Functions (Application Code)

```java
@ApplicationScoped
public class RetentionService {

    @Inject
    AgroalDataSource dataSource;

    /**
     * Purge old records based on TTL
     */
    @Transactional
    public int purgeByAge(int maxAgeDays) {
        String sql = """
            DELETE FROM sync_operation
            WHERE occurred_at < datetime('now', '-' || ? || ' days')
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, maxAgeDays);
            int deleted = stmt.executeUpdate();

            updateRetentionState(deleted, "AGE");
            return deleted;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to purge by age", e);
        }
    }

    /**
     * Purge oldest records until under size limit (circular queue pattern)
     */
    @Transactional
    public int purgeBySize(long maxBytes) {
        long currentSize = getDatabaseSize();
        if (currentSize <= maxBytes) {
            return 0;
        }

        long targetSize = (long) (maxBytes * 0.9); // Purge to 90% of limit
        int recordsToDelete = estimateRecordsToDelete(currentSize, targetSize);

        String sql = """
            DELETE FROM sync_operation
            WHERE id IN (
                SELECT id FROM sync_operation
                ORDER BY occurred_at ASC
                LIMIT ?
            )
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, recordsToDelete);
            int deleted = stmt.executeUpdate();

            updateRetentionState(deleted, "SIZE");
            vacuum();
            return deleted;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to purge by size", e);
        }
    }

    /**
     * Get database size in bytes
     */
    public long getDatabaseSize() {
        String sql = "SELECT page_count * page_size as size FROM pragma_page_count(), pragma_page_size()";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong("size");
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get database size", e);
        }
    }

    /**
     * Estimate records to delete based on size
     */
    private int estimateRecordsToDelete(long currentSize, long targetSize) {
        long excessBytes = currentSize - targetSize;

        // Get average record size
        String sql = "SELECT COUNT(*) as count FROM sync_operation";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                long totalRecords = rs.getLong("count");
                if (totalRecords == 0) return 0;

                long avgRecordSize = currentSize / totalRecords;
                return (int) (excessBytes / avgRecordSize);
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to estimate records", e);
        }
    }

    /**
     * Update retention state
     */
    private void updateRetentionState(int purgedCount, String reason) {
        String sql = """
            UPDATE retention_state
            SET approx_db_bytes = ?,
                last_purge_at = datetime('now'),
                total_purged_records = total_purged_records + ?,
                updated_at = datetime('now')
            WHERE id = 1
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, getDatabaseSize());
            stmt.setInt(2, purgedCount);
            stmt.executeUpdate();

            Log.infof("Purged %d records (reason: %s)", purgedCount, reason);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update retention state", e);
        }
    }

    /**
     * VACUUM to reclaim space
     */
    private void vacuum() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("VACUUM");
            Log.info("VACUUM completed");
        } catch (SQLException e) {
            Log.warn("VACUUM failed (non-critical)", e);
        }
    }
}
```

## SQLite Optimization

### Pragma Settings

```sql
-- Enable Write-Ahead Logging (better concurrency)
PRAGMA journal_mode = WAL;

-- Enable foreign keys
PRAGMA foreign_keys = ON;

-- Synchronous mode (NORMAL is good balance)
PRAGMA synchronous = NORMAL;

-- Cache size (negative = KB, positive = pages)
PRAGMA cache_size = -64000;  -- 64MB

-- Temp store in memory
PRAGMA temp_store = MEMORY;

-- Auto-vacuum (incremental)
PRAGMA auto_vacuum = INCREMENTAL;
```

### Connection Pool Configuration

```properties
# Agroal configuration for SQLite
quarkus.datasource.jdbc.min-size=1
quarkus.datasource.jdbc.max-size=5
quarkus.datasource.jdbc.acquire-timeout=PT30S
quarkus.datasource.jdbc.idle-removal-interval=PT5M
quarkus.datasource.jdbc.max-lifetime=PT30M
```

## Migration Best Practices

### 1. Naming Convention

```
V{version}__{description}.sql
└─┬──┘    └──────┬───────┘
  │              └─ snake_case description
  └─ Version number (incrementing)

Examples:
V1__init.sql
V2__add_indices.sql
V3__add_acl_tracking.sql
V4__add_metrics_table.sql

Repeatable:
R__views.sql
R__functions.sql
```

### 2. Idempotent Migrations

```sql
-- Always use IF NOT EXISTS
CREATE TABLE IF NOT EXISTS new_table (...);

-- Check before ALTER
-- SQLite doesn't support ALTER TABLE ... ADD COLUMN IF NOT EXISTS
-- Workaround:
CREATE TABLE IF NOT EXISTS _temp AS SELECT * FROM sync_operation LIMIT 0;
ALTER TABLE _temp ADD COLUMN new_column TEXT;
DROP TABLE _temp;

-- For repeatable migrations, always DROP before CREATE
DROP VIEW IF EXISTS v_summary;
CREATE VIEW v_summary AS ...;
```

### 3. Data Migrations

```sql
-- V5__migrate_data.sql
-- Separate schema changes from data changes

-- Step 1: Add new column
ALTER TABLE sync_operation ADD COLUMN new_field TEXT;

-- Step 2: Populate data
UPDATE sync_operation
SET new_field = CASE
    WHEN op_type = 'UPSERT_SCRAM' THEN 'CREDENTIAL'
    WHEN op_type LIKE '%ACL%' THEN 'AUTHORIZATION'
    ELSE 'OTHER'
END;

-- Step 3: Add constraint (if needed)
-- Note: SQLite doesn't support adding constraints to existing columns
-- You'd need to recreate the table
```

### 4. Rollback Strategy

Since Flyway doesn't support automatic rollback for SQLite:

```sql
-- Create undo migration manually
-- U5__undo_migrate_data.sql (convention, not automatic)

-- Reverse Step 3: Remove constraint (recreate table)
-- Reverse Step 2: Clear data
UPDATE sync_operation SET new_field = NULL;

-- Reverse Step 1: Remove column (recreate table without column)
```

### 5. Testing Migrations

```java
@QuarkusTest
class MigrationTest {

    @Inject
    Flyway flyway;

    @Test
    void testMigrations() {
        // Flyway runs migrations at startup
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void testSchemaVersion() {
        // Query schema_info table
        // Verify expected version
    }
}
```

## Troubleshooting

### Check Migration Status

```java
@Inject
Flyway flyway;

public void checkStatus() {
    MigrationInfo[] migrations = flyway.info().all();
    for (MigrationInfo migration : migrations) {
        Log.infof("Version: %s, State: %s, Description: %s",
            migration.getVersion(),
            migration.getState(),
            migration.getDescription()
        );
    }
}
```

### Repair Failed Migration

```bash
# In production, use carefully
quarkus.flyway.clean-at-start=false
quarkus.flyway.repair-at-start=true
```

### Baseline Existing Database

```properties
quarkus.flyway.baseline-on-migrate=true
quarkus.flyway.baseline-version=1
```

## Common Queries

```sql
-- Check table size
SELECT
    name,
    SUM(pgsize) as size_bytes
FROM dbstat
GROUP BY name
ORDER BY size_bytes DESC;

-- Check index effectiveness
SELECT * FROM sqlite_stat1;

-- Analyze query plan
EXPLAIN QUERY PLAN
SELECT * FROM sync_operation WHERE principal = 'alice';

-- Integrity check
PRAGMA integrity_check;

-- Quick check
PRAGMA quick_check;
```

## Resources

- Flyway Documentation: https://flywaydb.org/documentation/
- SQLite Documentation: https://www.sqlite.org/docs.html
- SQLite Optimization: https://www.sqlite.org/optoverview.html
- Quarkus Flyway: https://quarkus.io/guides/flyway

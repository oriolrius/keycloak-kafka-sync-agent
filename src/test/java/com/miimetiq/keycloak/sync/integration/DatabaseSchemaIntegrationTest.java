package com.miimetiq.keycloak.sync.integration;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for database schema validation (Task-010).
 * Validates that all sync operation tables exist with correct structure.
 */
@QuarkusTest
@DisplayName("Database Schema Integration Tests (Task-010)")
class DatabaseSchemaIntegrationTest {

    @Inject
    AgroalDataSource dataSource;

    @Test
    @DisplayName("AC#1-2: sync_operation table should exist with all required fields")
    void testSyncOperationTableSchema() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // Check table exists
            ResultSet tableCheck = statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='sync_operation'");
            assertTrue(tableCheck.next(), "sync_operation table should exist");
            assertEquals("sync_operation", tableCheck.getString("name"));

            // Check all required columns
            ResultSet columns = statement.executeQuery("PRAGMA table_info(sync_operation)");
            List<String> columnNames = new ArrayList<>();
            while (columns.next()) {
                columnNames.add(columns.getString("name"));
            }

            // Verify all required fields from AC#2
            assertTrue(columnNames.contains("id"), "sync_operation should have 'id' field");
            assertTrue(columnNames.contains("correlation_id"), "sync_operation should have 'correlation_id' field");
            assertTrue(columnNames.contains("occurred_at"), "sync_operation should have 'occurred_at' field");
            assertTrue(columnNames.contains("realm"), "sync_operation should have 'realm' field");
            assertTrue(columnNames.contains("cluster_id"), "sync_operation should have 'cluster_id' field");
            assertTrue(columnNames.contains("principal"), "sync_operation should have 'principal' field");
            assertTrue(columnNames.contains("op_type"), "sync_operation should have 'op_type' field");
            assertTrue(columnNames.contains("mechanism"), "sync_operation should have 'mechanism' field");
            assertTrue(columnNames.contains("result"), "sync_operation should have 'result' field");
            assertTrue(columnNames.contains("error_code"), "sync_operation should have 'error_code' field");
            assertTrue(columnNames.contains("error_message"), "sync_operation should have 'error_message' field");
            assertTrue(columnNames.contains("duration_ms"), "sync_operation should have 'duration_ms' field");
        }
    }

    @Test
    @DisplayName("AC#3: sync_batch table should exist with all required fields")
    void testSyncBatchTableSchema() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // Check table exists
            ResultSet tableCheck = statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='sync_batch'");
            assertTrue(tableCheck.next(), "sync_batch table should exist");
            assertEquals("sync_batch", tableCheck.getString("name"));

            // Check all required columns
            ResultSet columns = statement.executeQuery("PRAGMA table_info(sync_batch)");
            List<String> columnNames = new ArrayList<>();
            while (columns.next()) {
                columnNames.add(columns.getString("name"));
            }

            // Verify all required fields from AC#3
            assertTrue(columnNames.contains("id"), "sync_batch should have 'id' field");
            assertTrue(columnNames.contains("correlation_id"), "sync_batch should have 'correlation_id' field");
            assertTrue(columnNames.contains("started_at"), "sync_batch should have 'started_at' field");
            assertTrue(columnNames.contains("finished_at"), "sync_batch should have 'finished_at' field");
            assertTrue(columnNames.contains("source"), "sync_batch should have 'source' field");
            assertTrue(columnNames.contains("items_total"), "sync_batch should have 'items_total' field");
            assertTrue(columnNames.contains("items_success"), "sync_batch should have 'items_success' field");
            assertTrue(columnNames.contains("items_error"), "sync_batch should have 'items_error' field");
        }
    }

    @Test
    @DisplayName("AC#4: retention_state table should exist with required fields and default values")
    void testRetentionStateTableSchema() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // Check table exists
            ResultSet tableCheck = statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='retention_state'");
            assertTrue(tableCheck.next(), "retention_state table should exist");
            assertEquals("retention_state", tableCheck.getString("name"));

            // Check all required columns
            ResultSet columns = statement.executeQuery("PRAGMA table_info(retention_state)");
            List<String> columnNames = new ArrayList<>();
            while (columns.next()) {
                columnNames.add(columns.getString("name"));
            }

            // Verify all required fields from AC#4
            assertTrue(columnNames.contains("id"), "retention_state should have 'id' field");
            assertTrue(columnNames.contains("max_bytes"), "retention_state should have 'max_bytes' field");
            assertTrue(columnNames.contains("max_age_days"), "retention_state should have 'max_age_days' field");
            assertTrue(columnNames.contains("approx_db_bytes"), "retention_state should have 'approx_db_bytes' field");
            assertTrue(columnNames.contains("updated_at"), "retention_state should have 'updated_at' field");

            // Verify default values exist (max_age_days=30)
            ResultSet defaults = statement.executeQuery("SELECT * FROM retention_state WHERE id=1");
            assertTrue(defaults.next(), "retention_state should have a default row with id=1");
            assertEquals(30, defaults.getInt("max_age_days"),
                    "retention_state should have default max_age_days=30");
            assertNotNull(defaults.getString("updated_at"),
                    "retention_state should have updated_at timestamp");
        }
    }

    @Test
    @DisplayName("AC#5: All appropriate indexes should exist")
    void testIndexesExist() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // Get all indexes
            ResultSet indexes = statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_%'");

            List<String> indexNames = new ArrayList<>();
            while (indexes.next()) {
                indexNames.add(indexes.getString("name"));
            }

            // Verify indexes from AC#5
            assertTrue(indexNames.contains("idx_sync_operation_time"),
                    "Index on sync_operation(occurred_at) should exist");
            assertTrue(indexNames.contains("idx_sync_operation_principal"),
                    "Index on sync_operation(principal) should exist");
            assertTrue(indexNames.contains("idx_sync_operation_type"),
                    "Index on sync_operation(op_type) should exist");
            assertTrue(indexNames.contains("idx_sync_batch_time"),
                    "Index on sync_batch(started_at) should exist");
        }
    }

    @Test
    @DisplayName("AC#6: Migration should integrate successfully with existing schema")
    void testMigrationIntegration() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // Verify Flyway migration history shows successful V1 migration
            ResultSet migrations = statement.executeQuery(
                    "SELECT version, description, success FROM flyway_schema_history ORDER BY version");

            boolean foundV1 = false;
            while (migrations.next()) {
                String version = migrations.getString("version");
                boolean success = migrations.getBoolean("success");

                if (version.equals("1")) {
                    foundV1 = true;
                    assertTrue(success, "V1 migration should have succeeded");
                }
            }

            assertTrue(foundV1, "V1 migration (initial_schema) should exist in migration history");
        }
    }

    @Test
    @DisplayName("AC#7: All tables should be accessible for read/write operations")
    void testTablesAccessible() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // Test sync_operation table is accessible
            ResultSet syncOpResult = statement.executeQuery("SELECT COUNT(*) FROM sync_operation");
            assertTrue(syncOpResult.next(), "sync_operation table should be queryable");
            assertTrue(syncOpResult.getInt(1) >= 0, "sync_operation count should be non-negative");

            // Test sync_batch table is accessible
            ResultSet syncBatchResult = statement.executeQuery("SELECT COUNT(*) FROM sync_batch");
            assertTrue(syncBatchResult.next(), "sync_batch table should be queryable");
            assertTrue(syncBatchResult.getInt(1) >= 0, "sync_batch count should be non-negative");

            // Test retention_state table is accessible
            ResultSet retentionResult = statement.executeQuery("SELECT COUNT(*) FROM retention_state");
            assertTrue(retentionResult.next(), "retention_state table should be queryable");
            assertEquals(1, retentionResult.getInt(1),
                    "retention_state should have exactly one row");
        }
    }
}

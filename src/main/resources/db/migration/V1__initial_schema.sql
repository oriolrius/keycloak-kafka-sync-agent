-- Initial schema for keycloak-kafka-sync-agent
-- This will be populated with actual tables in future migrations

-- Placeholder table to ensure migration runs successfully
CREATE TABLE IF NOT EXISTS schema_info (
    version TEXT NOT NULL,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO schema_info (version) VALUES ('V1__initial_schema');

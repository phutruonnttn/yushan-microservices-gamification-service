-- Create processed_events table for idempotency tracking
-- This table ensures idempotency even when Redis is restarted

CREATE TABLE IF NOT EXISTS processed_events (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    service_name VARCHAR(50) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_data TEXT
);

-- Index for cleanup old records
CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at ON processed_events(processed_at);

-- Index for querying by event type and service
CREATE INDEX IF NOT EXISTS idx_processed_events_event_type_service ON processed_events(event_type, service_name);


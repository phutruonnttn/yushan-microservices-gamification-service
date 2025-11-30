-- Create yuan_reservation table for SAGA pattern vote creation
-- This table holds temporary Yuan reservations during distributed transactions

CREATE TABLE IF NOT EXISTS yuan_reservation (
    id SERIAL PRIMARY KEY,
    reservation_id UUID UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    saga_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'RESERVED', -- RESERVED, CONFIRMED, RELEASED
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP,
    released_at TIMESTAMP
);

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_yuan_reservation_saga_id ON yuan_reservation(saga_id);
CREATE INDEX IF NOT EXISTS idx_yuan_reservation_user_id ON yuan_reservation(user_id);
CREATE INDEX IF NOT EXISTS idx_yuan_reservation_status ON yuan_reservation(status);
CREATE INDEX IF NOT EXISTS idx_yuan_reservation_expires_at ON yuan_reservation(expires_at);

-- Index for cleanup expired reservations
CREATE INDEX IF NOT EXISTS idx_yuan_reservation_status_expires ON yuan_reservation(status, expires_at);



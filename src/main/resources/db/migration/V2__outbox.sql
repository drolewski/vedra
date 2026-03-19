CREATE TABLE IF NOT EXISTS outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(255),
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    error_message TEXT
);

CREATE INDEX idx_outbox_status_created
    ON outbox (status, created_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX idx_outbox_aggregate
    ON outbox (aggregate_id)
    WHERE aggregate_id IS NOT NULL;

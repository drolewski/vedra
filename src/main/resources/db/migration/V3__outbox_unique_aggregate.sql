CREATE UNIQUE INDEX idx_outbox_aggregate_unique
    ON outbox (aggregate_id)
    WHERE aggregate_id IS NOT NULL;

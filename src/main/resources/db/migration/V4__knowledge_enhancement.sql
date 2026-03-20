ALTER TABLE knowledge_entries
    ADD COLUMN answer_source VARCHAR(20) NOT NULL DEFAULT 'USER_VERIFIED',
    ADD COLUMN quality_score FLOAT NOT NULL DEFAULT 0.5,
    ADD COLUMN usage_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_used_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN accepted BOOLEAN NOT NULL DEFAULT true;

CREATE INDEX idx_knowledge_entries_answer_source
    ON knowledge_entries (tenant_id, answer_source);

CREATE INDEX idx_knowledge_entries_quality_score
    ON knowledge_entries (tenant_id, quality_score DESC);

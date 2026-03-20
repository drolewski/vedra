DROP TABLE IF EXISTS feedback_mappings;

CREATE TABLE feedback_mappings (
    id VARCHAR(36) PRIMARY KEY,
    channel VARCHAR(50) NOT NULL,
    message_ts VARCHAR(50) NOT NULL,
    entry_ids JSONB NOT NULL,
    votes JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_feedback_message_ts ON feedback_mappings (message_ts);

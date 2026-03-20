CREATE TABLE IF NOT EXISTS channel_projects (
    channel_id VARCHAR(50) NOT NULL,
    project_id UUID NOT NULL REFERENCES tenant_projects(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (channel_id)
);

CREATE INDEX idx_channel_projects_project ON channel_projects (project_id);

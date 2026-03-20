ALTER TABLE knowledge_entries
    ADD COLUMN project_id UUID REFERENCES tenant_projects(id);

CREATE INDEX idx_knowledge_project ON knowledge_entries (tenant_id, project_id);

-- ============================================
-- Migracja: Normalizacja i cache'owanie LLM
-- ============================================

-- 1. Tabela cache odpowiedzi LLM
CREATE TABLE llm_response_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    project_id UUID REFERENCES tenant_projects(id),
    question_hash VARCHAR(64) NOT NULL,
    normalized_question TEXT NOT NULL,
    original_question TEXT NOT NULL,
    answer TEXT NOT NULL,
    normalized_answer TEXT,
    model_version VARCHAR(100) NOT NULL,
    confidence DOUBLE PRECISION,
    response_source VARCHAR(50) NOT NULL,
    context_entry_ids JSONB DEFAULT '[]'::jsonb,
    usage_count INTEGER DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_llm_cache_lookup ON llm_response_cache(tenant_id, project_id, question_hash);
CREATE INDEX idx_llm_cache_expires ON llm_response_cache(expires_at);
CREATE INDEX idx_llm_cache_tenant ON llm_response_cache(tenant_id);

-- 2. Tabela cache embeddingów
CREATE TABLE embedding_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    text_hash VARCHAR(64) NOT NULL UNIQUE,
    normalized_text TEXT NOT NULL,
    embedding vector(768) NOT NULL,
    model_version VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_embedding_cache_hash ON embedding_cache(text_hash);
CREATE INDEX idx_embedding_cache_expires ON embedding_cache(expires_at);

-- 3. Dodatkowe pola w knowledge_entries
ALTER TABLE knowledge_entries
    ADD COLUMN normalized_content TEXT,
    ADD COLUMN content_hash VARCHAR(64),
    ADD COLUMN reused_count INTEGER DEFAULT 0;

CREATE INDEX idx_knowledge_content_hash ON knowledge_entries(content_hash);

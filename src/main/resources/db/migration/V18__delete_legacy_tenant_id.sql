-- Usuniecie danych zapisanych pod hardcoded legacy tenant (00001101-...).
-- Automatyczne przypisanie do prawdziwych tenantow niemozliwe (UUID projektow globalnie unikalne).
DELETE FROM knowledge_entries WHERE tenant_id = '00001101-0000-1000-8000-00805F9B34FB';
DELETE FROM llm_response_cache WHERE tenant_id = '00001101-0000-1000-8000-00805F9B34FB';

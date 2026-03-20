ALTER TABLE knowledge_entries ADD COLUMN answer TEXT;

UPDATE knowledge_entries
SET answer = metadata ->> 'answer'
WHERE metadata ? 'answer';

UPDATE knowledge_entries
SET metadata = '{}'::jsonb
WHERE metadata ? 'answer';

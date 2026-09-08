ALTER TABLE tenant_projects ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';

UPDATE tenant_projects SET visibility = 'PUBLIC' WHERE is_general = true;
UPDATE tenant_projects SET visibility = 'PRIVATE' WHERE is_general = false OR is_general IS NULL;

ALTER TABLE tenant_projects DROP COLUMN is_general;

CREATE UNIQUE INDEX uniq_public_project_per_tenant
  ON tenant_projects (tenant_id) WHERE visibility = 'PUBLIC';

INSERT INTO tenant_projects (tenant_id, name, description, visibility)
SELECT t.id, 'Baza ogólna', 'Wiedza współdzielona tenanta', 'PUBLIC'
FROM tenants t
WHERE NOT EXISTS (
  SELECT 1 FROM tenant_projects p WHERE p.tenant_id = t.id AND p.visibility = 'PUBLIC'
);

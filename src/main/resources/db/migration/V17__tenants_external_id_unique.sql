CREATE UNIQUE INDEX IF NOT EXISTS uniq_tenants_external_id
  ON tenants (external_id) WHERE external_id IS NOT NULL;

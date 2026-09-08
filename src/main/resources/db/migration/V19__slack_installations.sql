CREATE TABLE IF NOT EXISTS slack_installations (
  team_id TEXT PRIMARY KEY,
  team_name TEXT,
  bot_token TEXT NOT NULL,
  bot_user_id TEXT,
  installer_user_id TEXT,
  scopes TEXT NOT NULL DEFAULT '',
  app_id TEXT,
  installed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at TIMESTAMPTZ NULL,
  active BOOLEAN NOT NULL DEFAULT true
);

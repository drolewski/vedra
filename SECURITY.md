# Security and data handling

Vedra is an unfinished prototype. Use synthetic data in a disposable Slack workspace. Do not expose the entire application to the public internet or treat the `prod` profile as production hardening.

## Credentials

Keep credentials in an ignored `.env` or an external secret manager. `.env.example` contains placeholders only. Never commit database dumps, Slack tokens, OAuth secrets, encryption keys or request logs.

Generate a database password with `openssl rand -hex 24` and a token-encryption key with `openssl rand -base64 32`. Set `SLACK_TOKEN_ENCRYPTION_KEY` before installation: the current converter permits plaintext token storage when the key is absent. It also tolerates invalid key lengths; use exactly 32 random bytes encoded in Base64. Changing this key without migrating existing encrypted rows makes existing tokens unreadable.

If a real secret is ever committed, revoke/rotate it first. Removing the current file does not remove it from Git history, forks or clones. Rewrite affected history only with a coordinated recovery plan.

## Current boundaries

- Slack POST callbacks require a valid signature and a timestamp within five minutes. Missing signatures are rejected, including local Bruno examples.
- `/api/v1/chat`, `/api/v1/chat/knowledge` and `/api/v1/admin/cache/stats` have no application authentication. `X-Tenant-Id` is a selector, not an authorization credential.
- For Slack development, route only `/api/v1/slack/` through an HTTPS ingress with an explicit path allowlist. Keep all other routes private; a tunnel forwarding the entire application is insufficient.
- OAuth state still needs session binding and single-use enforcement. Legacy global bot-token fallback should remain unset.
- PostgreSQL contains original message content, generated summaries, feedback and installation tokens. Local inference does not eliminate the need for access control, retention or deletion policies.
- Tenant/project isolation, private-to-public promotion and caches need independent security testing before handling real organizational knowledge.

## Reporting

Do not put credentials or private workspace content in public issues. If GitHub private vulnerability reporting is enabled, use the repository's Security tab. Otherwise open only a non-sensitive issue requesting a private reporting channel.

## Publication audit

On 2026-11-14, reachable Git history was scanned with Gitleaks and local credential values were checked against historical blobs without printing their values. No secrets were detected. This is a bounded scan, not a guarantee or a dependency vulnerability audit.

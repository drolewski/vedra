# Roadmap

This is a record of unfinished work, not a release commitment. See [project status](docs/PROJECT_STATUS.md) for the current implementation.

## Before using real workspace data

- [ ] Authenticate HTTP API callers and authorize tenant/project access; protect admin endpoints.
- [ ] Bind OAuth state to the initiating session, consume it once and test replay/error cases.
- [ ] Require valid encryption keys, remove plaintext/global-token fallback and define token rotation.
- [ ] Verify cross-tenant and private/public knowledge isolation, including caches and promotion.
- [ ] Define retention, deletion and logging rules for messages and generated knowledge.
- [ ] Run clean-database migrations and complete live Slack/Ollama/PostgreSQL acceptance.

## Core assistant quality

- [ ] Re-enable usefulness filtering after evaluating it against representative conversations.
- [ ] Replace punctuation/literal-mention question detection with robust intent and bot-mention handling.
- [ ] Build an answer-quality evaluation dataset, including unsupported questions and conflicting evidence.
- [ ] Calibrate confidence presentation and feedback weights; evaluate low-confidence alternatives.
- [ ] Validate cache invalidation, feedback propagation and model-version changes.
- [ ] Simplify legacy models and reconcile overlapping specifications.
- [ ] Complete and record the manual regression checklist in `test-plan/`.

## Possible extensions

- [ ] User-language-aware messages and responses.
- [ ] Document ingestion and alternative model adapters.
- [ ] Teams and Google Chat connectors.
- [ ] Subscription limits, billing and an administration dashboard.
- [ ] A more complete installation/onboarding experience.

Earlier subscription counts and pricing ideas were exploratory; no commercial plans or quotas are promised by this repository.

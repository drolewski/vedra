# Project status

Reviewed for public repository preparation on 2026-11-14. This is an implementation review, not a security certification or a completed product acceptance report.

## Problem and approach

Teams repeatedly lose institutional knowledge in Slack history. Vedra experiments with capturing channel context automatically, separating knowledge by workspace and project, and answering repeated questions inside Slack. Local Ollama inference makes the model service configurable and avoids requiring a hosted-model API key for the default setup.

The interesting engineering work is the combination of tenant/project-aware retrieval, durable asynchronous processing, cached inference and a feedback loop. The application follows a domain/services/adapters structure, although some domain services still depend directly on infrastructure types; this is not strict hexagonal architecture.

## Implemented, with limits

| Capability | Evidence / current limit |
| --- | --- |
| Workspace installation | OAuth controller/service and per-workspace token storage exist; live installation acceptance is pending. |
| Channel/project spaces | Provisioning, linking, configuration and public/private project models exist; authorization and isolation need broader verification. |
| Knowledge ingestion | Summarization, embeddings and persistence exist. The return for `useful == false` is commented out, so rejected messages are still ingested. |
| Answers | Reuse, cache, vector retrieval and LLM fallback exist. General fallback can answer without supporting workspace knowledge. |
| Feedback | Vote mapping, quality scoring and knowledge promotion exist; calibration and abuse controls are unfinished. |
| Reliability | Database outbox and retries exist; failure recovery under load has not been established. |
| Automated tests | Unit/MockMvc regression suite covers core paths. External services are mocked. |
| Product acceptance | Manual checklists exist but are not completed; no live Slack/Ollama/PostgreSQL acceptance is claimed. |

## Known gaps

1. **API authorization:** chat accepts a caller-supplied `X-Tenant-Id`; neither this nor CORS authenticates a caller. Cache admin statistics are also unprotected. Keep these endpoints private.
2. **OAuth and token lifecycle:** state is signed and time-limited but is not bound to a browser session or consumed once. Token conversion still permits plaintext when a key is absent and normalizes invalid key lengths. Legacy global bot-token fallback remains.
3. **Question detection:** a trailing `?` or literal `@vedra` triggers a question. Slack's encoded user mentions are not dynamically resolved; the original workspace-specific bot ID was removed during publication cleanup.
4. **Retrieval quality:** confidence is heuristic, not a probability of correctness. No benchmark demonstrates answer accuracy, hallucination resistance or usefulness filtering quality.
5. **Data lifecycle:** stored message metadata includes original content. Retention, deletion, export, consent and cross-workspace isolation require dedicated review. Embedding cache behavior also needs privacy review.
6. **Deployment:** the production profile is only a configuration starting point. There is no supported production deployment, authentication layer, rate limiting or observability setup.
7. **Schema and models:** historical migrations and overlapping design specifications reflect an evolving prototype. Clean-database and upgrade-path acceptance remain required.
8. **Product scope:** subscription plan definitions are preliminary; enforcement, billing, dashboard, document ingestion and other chat connectors are not delivered.

## Publication preparation

- Added a project overview, reproducible local instructions, security notes and a prioritized roadmap.
- Removed tracked IDE metadata and expanded ignore rules for local configuration and key files.
- Replaced hardcoded database credentials with environment configuration and bound the sample database to loopback.
- Aligned production vector dimensions with the existing 768-dimensional schema.
- Made Slack signature verification reject missing/invalid headers and malformed or expired timestamps.
- Escaped values rendered in the OAuth result page and reduced routine message-content logging.
- Removed implicit source formatting from compilation; formatting is an explicit command.
- Scanned reachable Git history with Gitleaks and compared local credential values with historical blobs. No matching secrets were found. Pattern scans cannot establish that every possible secret is absent.

The local `.env` remains ignored and was not uploaded. There was no evidence requiring a history rewrite. Existing design documents are retained as development history, not rewritten as promises of completed features.

## Verification of the prepared revision

- `./gradlew check bootJar` with JDK 24: passed; **185 tests, 0 failures, 0 skipped**, ktlint passed and executable JAR built.
- Docker Compose configuration validation: passed with a temporary synthetic password.
- Gitleaks scan of the staged publication snapshot: no secrets detected.
- Reachable pre-publication Git history: 86 commits scanned, no secrets detected; five local credential values were separately compared against historical blobs with no matches.
- Live Slack installation, database migrations and model inference were not exercised. Existing compiler/JDK deprecation warnings remain.

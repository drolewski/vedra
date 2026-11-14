# Local development

Use synthetic messages in a disposable Slack workspace. Read [SECURITY.md](SECURITY.md) before exposing callback routes.

## 1. Prerequisites

- JDK **24** (`java -version`). On macOS: `export JAVA_HOME=$(/usr/libexec/java_home -v 24)`.
- Docker with Compose, and Ollama running on `http://localhost:11434`.
- A Slack app that you can configure and install in a development workspace.
- An HTTPS ingress for Slack callbacks, restricted to `/api/v1/slack/`.

The Gradle Wrapper is included. A separate Gradle installation is not required.

## 2. Environment

From the repository root:

```sh
cp .env.example .env
openssl rand -hex 24
openssl rand -base64 32
```

Put the first generated value in `POSTGRES_PASSWORD`, the second in `SLACK_TOKEN_ENCRYPTION_KEY`. Keep them private. Use shell-compatible assignments without spaces around `=`.

| Variable | Value |
| --- | --- |
| `POSTGRES_USER` | `vedra` for the sample database |
| `POSTGRES_PASSWORD` | Generated database password |
| `SLACK_SIGNING_SECRET` | Slack app signing secret |
| `SLACK_CLIENT_ID` / `SLACK_CLIENT_SECRET` | Slack app OAuth credentials |
| `SLACK_REDIRECT_URL` | `https://YOUR_HOST/api/v1/slack/oauth/callback` |
| `SLACK_SCOPES` | Start with the scopes in `.env.example` |
| `SLACK_TOKEN_ENCRYPTION_KEY` | Base64-encoded 32 random bytes, generated above |

Leave the deprecated `SLACK_BOT_TOKEN` unset; new installations store a token per workspace. A missing encryption key currently permits plaintext token storage. Do not install the app before setting the key.

## 3. Database and models

```sh
docker compose --env-file .env -f docker/template-docker-compose.yml up -d
docker compose --env-file .env -f docker/template-docker-compose.yml ps
ollama pull nomic-embed-text
ollama pull llama3.2
```

If Ollama is not already running, start it with `ollama serve` in another terminal. The application uses 768-dimensional embeddings, matching the SQL schema. Changing the embedding model may require a schema/data migration.

The database is exposed only on `127.0.0.1:5432`. Its named volume persists data. Changing `.env` does not change the password of an already initialized PostgreSQL volume; update that database explicitly or create a fresh disposable instance.

## 4. Application

```sh
set -a
. ./.env
set +a
./gradlew bootRun --args='--spring.profiles.active=local --server.address=127.0.0.1'
```

Spring does **not** automatically read `.env`; export it as above or set the variables in your IDE run configuration. Flyway migrations run on startup. This setup has not yet passed live clean-database acceptance; report migration/startup failures rather than treating unit tests as proof of a working deployment.

## 5. Restricted Slack ingress

Do not tunnel port 8080 directly: it also serves unauthenticated chat and admin APIs. One local option is Caddy on loopback port 8081 with this configuration:

```caddyfile
http://127.0.0.1:8081 {
    handle /api/v1/slack/* {
        reverse_proxy 127.0.0.1:8080
    }
    handle {
        respond 404
    }
}
```

Start Caddy with that configuration, then tunnel **8081**, for example with `ngrok http 8081`. Set `SLACK_REDIRECT_URL` to the resulting HTTPS hostname plus `/api/v1/slack/oauth/callback`, then restart the application. Verify that the public URL returns 404 for `/api/v1/admin/cache/stats` before installation.

## 6. Slack app configuration

Configure these URLs using the same HTTPS hostname:

| Slack setting | Path |
| --- | --- |
| OAuth redirect URL | `/api/v1/slack/oauth/callback` |
| Events request URL | `/api/v1/slack/events` |
| Slash command `/vedra` request URL | `/api/v1/slack/commands` |
| Interactivity request URL | `/api/v1/slack/interactive` |

Configure bot scopes from `SLACK_SCOPES`. The implemented handlers use message events (`message.channels`, and `message.groups` when testing private channels), `member_joined_channel`, and lifecycle events `app_uninstalled` / `tokens_revoked`. Scope/event needs must be verified against the channels used in the development workspace. The current question handler uses `message` events, not a dedicated `app_mention` handler.

Open `https://YOUR_HOST/api/v1/slack/install`, authorize the app, then invite Vedra to a test channel. Try `/vedra help`, `/vedra status` and `/vedra config`. Post synthetic factual content, allow time for the outbox/model processing, then ask a question ending with `?`.

Use [test-plan/CHECKLIST.md](test-plan/CHECKLIST.md) to record actual acceptance. Do not assume a successful install proves retrieval quality or tenant isolation.

## 7. Tests and HTTP examples

```sh
./gradlew check bootJar
```

No real Slack credentials are needed by the automated tests. Bruno examples under `http-client/` are request templates. Chat requests need `X-Tenant-Id` containing a real local tenant UUID; this header does not authenticate the caller. Slack POST requests require fresh, valid signatures and unsigned examples will return 401. Use Slack itself for live callbacks or the signature-filter tests for synthetic verification.

To stop PostgreSQL while preserving local data:

```sh
docker compose --env-file .env -f docker/template-docker-compose.yml down
```

The `prod` profile requires additional `DATABASE_URL` (JDBC format), `OLLAMA_BASE_URL` and `CORS_ALLOWED_ORIGINS` variables. It is not a supported production deployment; the open items in `SECURITY.md` remain applicable.

# Local App Setup

# env
1. Copy `.env.example` to .env
2. Set variables (operator Vedry, raz — nie klient):
   1. SLACK_SIGNING_SECRET - Signing Secret from Slack App Basic Information
   2. SLACK_CLIENT_ID / SLACK_CLIENT_SECRET - from Basic Information
   3. SLACK_REDIRECT_URL - e.g. `https://<ngrok>.ngrok.io/api/v1/slack/oauth/callback`
   4. SLACK_SCOPES - default z `.env.example`
   5. SLACK_TOKEN_ENCRYPTION_KEY - klucz AES do szyfrowania bot tokenów w DB (Base64, 16/32B; pusty = plaintext `{plain}` tylko na local)
   6. SLACK_BOT_TOKEN - DEPRECATED fallback, tylko do migracji starych workspace'ów

# Docker
1. Copy `template-docker-compose.yml` to `docker-compose.yml`
2. Run `docker-compose up -d`

# Run ollama
1. Install ollama: https://ollama.com/docs/installation
2. Pull ollama models:
   1. `ollama pull nomic-embed-text`
   2. `ollama pull llama3.2`

# Run ngrok
1. Install ngrok: https://ngrok.com/download
2. Run `ngrok http 8080`

# Run the app
1. Run from IntelliJ with local profile
2. Run `./gradlew bootRun --args='--spring.profiles.active=local'` from terminal

# Install na workspace (klient robi tylko to)
1. Otwórz `GET /api/v1/slack/install` → Allow
2. `/invite @Vedra` na kanale → przestrzeń utworzy się sama

# Local App Setup

# env
1. Copy `.env.example` to .env
2. Set variables:
   1. SLACK_BOT_TOKEN - Install App Bot User OAuth Token 

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

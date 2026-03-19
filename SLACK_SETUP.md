# Slack Integration Setup

## Prerequisites

1. Create a Slack App at https://api.slack.com/apps
2. Enable Socket Mode
3. Enable Events API

## Steps

1. Go to **Basic Information** → **App-Level Tokens** → **Generate Token**
   - Scope: `connections:write`
   - Copy the token (starts with `xapp-1-`)

2. Go to **OAuth & Permissions** → **Bot Token Scopes**:
   - `channels:history`
   - `chat:write`
   - `im:history`
   - `im:write`
   - `app_mentions:read`

3. Go to **Event Subscriptions**:
   - Enable Events
   - Subscribe to bot events:
     - `app_mention`
     - `message.channels`
     - `message.im`

4. Go to **OAuth & Permissions** → **Install App**:
   - Install to your workspace
   - Copy the Bot User OAuth Token (starts with `xoxb-`)

## Configuration

Set the following environment variables:

```bash
export SLACK_APP_TOKEN=xapp-1-...
export SLACK_BOT_TOKEN=xoxb-...
```

Or create a `.env` file based on `.env.example`.

## Usage

1. Start the application
2. In Slack, mention the bot in a channel or send a direct message
3. The bot will respond based on the knowledge base

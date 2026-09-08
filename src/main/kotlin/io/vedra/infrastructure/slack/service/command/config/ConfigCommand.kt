package io.vedra.infrastructure.slack.service.command.config

data class ConfigCommand(
    val channelId: String,
    val workspaceId: String,
    val triggerId: String,
)

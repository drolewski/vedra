package io.vedra.infrastructure.slack.service.command.status

data class StatusCommand(
    val channelId: String,
    val workspaceId: String = "",
)

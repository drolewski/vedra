package io.vedra.infrastructure.slack.service.command.link

data class LinkProjectCommand(
    val workspaceId: String,
    val channelId: String,
    val projectName: String,
)

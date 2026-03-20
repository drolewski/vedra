package io.vedra.infrastructure.slack.service.command.create

data class CreateProjectCommand(
    val workspaceId: String,
    val channelId: String,
    val projectName: String,
)

package io.vedra.infrastructure.slack.service.command.create

data class CreateProjectCommand(
    val triggerId: String,
    val channelId: String,
    val workspaceId: String,
)

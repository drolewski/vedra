package io.vedra.infrastructure.slack.service.command.create

data class CreateProjectCommand(
    val teamId: String,
    val channelId: String,
    val projectName: String,
)

package io.vedra.infrastructure.slack.service.command

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.slack.service.command.create.CreateProjectCommand
import io.vedra.infrastructure.slack.service.command.help.HelpCommand
import io.vedra.infrastructure.slack.service.command.link.LinkProjectCommand
import io.vedra.infrastructure.slack.service.command.list.ListProjectsCommand
import io.vedra.infrastructure.slack.service.command.status.StatusCommand
import io.vedra.infrastructure.slack.service.command.unlink.UnlinkProjectCommand
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class SlackCommandParser {
    fun parse(
        text: String,
        channelId: String,
        workspaceId: String,
    ): Any {
        val parts = text.trim().split("\\s+".toRegex(), limit = 2)
        val action = parts.getOrNull(0)?.lowercase()
        val argument = parts.getOrNull(1) ?: ""

        logger.debug { "Parsing command: action=$action argument='$argument'" }

        return when (action) {
            "create" -> CreateProjectCommand(workspaceId, channelId, argument)
            "link" -> LinkProjectCommand(workspaceId, channelId, argument)
            "unlink" -> UnlinkProjectCommand(channelId)
            "list" -> ListProjectsCommand(workspaceId)
            "status" -> StatusCommand(channelId)
            else -> HelpCommand()
        }
    }
}

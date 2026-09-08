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
        triggerId: String,
    ): Any {
        val action =
            text
                .trim()
                .split("\\s+".toRegex(), limit = 2)
                .getOrNull(0)
                ?.lowercase()

        logger.debug { "Parsing command: action=$action" }

        return when (action) {
            "create" -> CreateProjectCommand(triggerId, channelId, workspaceId)
            "link" -> {
                val projectId = text.trim().split("\\s+".toRegex(), limit = 2).getOrNull(1) ?: ""
                LinkProjectCommand(workspaceId, channelId, projectId)
            }
            "unlink" -> UnlinkProjectCommand(channelId)
            "list" -> ListProjectsCommand(workspaceId)
            "status" -> StatusCommand(channelId, workspaceId)
            else -> HelpCommand()
        }
    }
}

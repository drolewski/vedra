package io.vedra.infrastructure.slack.service.command

import io.vedra.infrastructure.slack.service.command.create.CreateProjectCommand
import io.vedra.infrastructure.slack.service.command.help.HelpCommand
import io.vedra.infrastructure.slack.service.command.link.LinkProjectCommand
import io.vedra.infrastructure.slack.service.command.list.ListProjectsCommand
import io.vedra.infrastructure.slack.service.command.status.StatusCommand
import io.vedra.infrastructure.slack.service.command.unlink.UnlinkProjectCommand
import org.springframework.stereotype.Component

@Component
class SlackCommandParser {
    fun parse(
        text: String,
        channelId: String,
        teamId: String,
    ): Any {
        val parts = text.trim().split("\\s+".toRegex(), limit = 2)
        val action = parts.getOrNull(0)?.lowercase()
        val argument = parts.getOrNull(1) ?: ""

        return when (action) {
            "create" -> CreateProjectCommand(teamId, channelId, argument)
            "link" -> LinkProjectCommand(teamId, channelId, argument)
            "unlink" -> UnlinkProjectCommand(channelId)
            "list" -> ListProjectsCommand(teamId)
            "status" -> StatusCommand(channelId)
            else -> HelpCommand()
        }
    }
}

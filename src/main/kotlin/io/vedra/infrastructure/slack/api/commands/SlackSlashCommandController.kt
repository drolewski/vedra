package io.vedra.infrastructure.slack.api.commands

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.slack.service.command.CommandDispatcher
import io.vedra.infrastructure.slack.service.command.create.CreateProjectCommand
import io.vedra.infrastructure.slack.service.command.help.HelpCommand
import io.vedra.infrastructure.slack.service.command.link.LinkProjectCommand
import io.vedra.infrastructure.slack.service.command.list.ListProjectsCommand
import io.vedra.infrastructure.slack.service.command.status.StatusCommand
import io.vedra.infrastructure.slack.service.command.unlink.UnlinkProjectCommand
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/slack/commands")
class SlackSlashCommandController(
    private val commandDispatcher: CommandDispatcher,
) {
    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun handleCommand(
        @RequestParam("command") command: String,
        @RequestParam("text") text: String,
        @RequestParam("channel_id") channelId: String,
        @RequestParam("team_id") teamId: String,
    ): ResponseEntity<Map<String, String>> {
        logger.info { "Received slash command: $command $text in channel=$channelId" }

        if (command != "/vedra") {
            return ResponseEntity.ok(mapOf("text" to "Nieznana komenda"))
        }

        val slackCommand = parseCommand(text, channelId, teamId)
        val response = commandDispatcher.dispatch(slackCommand)

        return ResponseEntity.ok(mapOf("response_type" to "ephemeral", "text" to response))
    }

    private fun parseCommand(
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

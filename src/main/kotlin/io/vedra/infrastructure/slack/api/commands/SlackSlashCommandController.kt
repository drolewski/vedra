package io.vedra.infrastructure.slack.api.commands

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.slack.service.command.CommandDispatcher
import io.vedra.infrastructure.slack.service.command.SlackCommandParser
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
    private val commandParser: SlackCommandParser,
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

        val slackCommand = commandParser.parse(text, channelId, teamId)
        val response = commandDispatcher.dispatch(slackCommand)

        return ResponseEntity.ok(mapOf("response_type" to "ephemeral", "text" to response))
    }
}

package io.vedra.infrastructure.slack.api.events

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.slack.service.SlackChallengeHandler
import io.vedra.infrastructure.slack.service.SlackEventRequestHandler
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/slack/events")
class SlackEventsController(
    private val challengeHandler: SlackChallengeHandler,
    private val eventRequestHandler: SlackEventRequestHandler,
) {
    @PostMapping
    fun slackEvents(
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<Any> {
        logger.debug { "Received Slack events callback" }
        return challengeHandler.handle(body) ?: eventRequestHandler.handle(body)
    }
}

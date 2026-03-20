package io.vedra.infrastructure.slack.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.slack.model.dto.SlackEventPayload
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class SlackEventRequestHandler(
    private val eventProcessor: SlackEventProcessor,
) {
    fun handle(body: Map<String, Any>): ResponseEntity<Any> {
        val eventPayload =
            try {
                SlackEventPayload.fromRawBody(body)
            } catch (e: IllegalArgumentException) {
                logger.warn(e) { "Invalid Slack event payload" }
                return ResponseEntity.ok().build()
            }

        logger.info { "Processing Slack event: type=${eventPayload.type} eventId=${eventPayload.eventId}" }
        eventProcessor.process(eventPayload)
        return ResponseEntity.ok().build()
    }
}

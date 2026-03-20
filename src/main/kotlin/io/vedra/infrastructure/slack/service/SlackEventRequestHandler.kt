package io.vedra.infrastructure.slack.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.slack.model.SlackEvent
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class SlackEventRequestHandler(
    private val eventProcessor: SlackEventProcessor,
) {
    fun handle(body: Map<String, Any>): ResponseEntity<Any> {
        val event =
            try {
                SlackEvent.fromRawBody(body)
            } catch (e: IllegalArgumentException) {
                logger.warn(e) { "Invalid Slack event payload" }
                return ResponseEntity.ok().build()
            }

        logger.info { "Processing Slack event: type=${event.type} eventId=${event.eventId}" }
        eventProcessor.process(event)
        return ResponseEntity.ok().build()
    }
}

package io.vedra.infrastructure.slack.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.slack.model.SlackEvent
import io.vedra.infrastructure.slack.model.SlackEventFilter
import io.vedra.infrastructure.slack.model.SlackEventHandler
import io.vedra.infrastructure.slack.model.dto.SlackEventPayload
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class SlackEventProcessor(
    private val filters: List<SlackEventFilter>,
    private val handlers: List<SlackEventHandler>,
) {
    fun process(event: SlackEventPayload) {
        val slackEvent = event.toDomain()

        if (!passesFilters(slackEvent)) {
            logger.debug { "Event filtered out: type=${event.type} eventId=${event.eventId}" }
            return
        }

        val handler = handlers.firstOrNull { it.supports(slackEvent) }
        if (handler == null) {
            logger.warn { "No handler found for Slack event: type=${event.type} eventId=${event.eventId}" }
            return
        }

        handler.handle(slackEvent)
    }

    private fun passesFilters(event: SlackEvent): Boolean = filters.all { it.shouldProcess(event) }
}

package io.vedra.infrastructure.slack.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.slack.model.SlackEvent
import io.vedra.infrastructure.slack.model.SlackEventFilter
import io.vedra.infrastructure.slack.model.SlackEventHandler
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class SlackEventProcessor(
    private val filters: List<SlackEventFilter>,
    private val handlers: List<SlackEventHandler>,
) {
    fun process(event: SlackEvent) {
        logger.info { "Processing event: type=${event.type} subtype=${event.subtype} channel=${event.channel} threadTs=${event.threadTs}" }
        logger.info { "Available filters: ${filters.size}, handlers: ${handlers.size}" }

        if (!passesFilters(event)) {
            logger.info { "Event filtered out: type=${event.type} eventId=${event.eventId}" }
            return
        }

        val handler = handlers.firstOrNull { it.supports(event) }
        if (handler == null) {
            logger.warn { "No handler found for Slack event: type=${event.type} eventId=${event.eventId}" }
            handlers.forEach { h ->
                logger.info { "Handler ${h::class.simpleName} supports=${h.supports(event)}" }
            }
            return
        }

        logger.info { "Found handler: ${handler::class.simpleName}" }
        handler.handle(event)
    }

    private fun passesFilters(event: SlackEvent): Boolean = filters.all { it.shouldProcess(event) }
}

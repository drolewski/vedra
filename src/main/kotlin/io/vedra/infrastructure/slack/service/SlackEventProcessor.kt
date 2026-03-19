package io.vedra.infrastructure.slack.service

import io.vedra.infrastructure.slack.model.SlackEvent
import io.vedra.infrastructure.slack.model.SlackEventFilter
import io.vedra.infrastructure.slack.model.SlackEventHandler
import org.springframework.stereotype.Service

@Service
class SlackEventProcessor(
    private val filters: List<SlackEventFilter>,
    private val handlers: List<SlackEventHandler>,
) {
    fun process(event: SlackEvent) {
        if (!passesFilters(event)) return

        val handler = handlers.firstOrNull { it.supports(event) } ?: return
        handler.handle(event)
    }

    private fun passesFilters(event: SlackEvent): Boolean = filters.all { it.shouldProcess(event) }
}

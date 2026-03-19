package io.vedra.domain.service

import io.vedra.domain.model.SlackEvent
import io.vedra.domain.model.SlackEventFilter
import io.vedra.domain.model.SlackEventHandler
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

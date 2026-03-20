package io.vedra.infrastructure.slack.service

import io.vedra.infrastructure.slack.model.SlackEvent
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

@Component
class SlackEventRequestHandler(
    private val eventProcessor: SlackEventProcessor,
) {
    fun handle(body: Map<String, Any>): ResponseEntity<Any> {
        val eventPayload = body["event"] as? Map<*, *> ?: return ResponseEntity.ok().build()
        val event = SlackEvent.fromRawPayload(eventPayload)
        eventProcessor.process(event)
        return ResponseEntity.ok().build()
    }
}

package io.vedra.api.controller.slack.events

import io.vedra.domain.model.SlackEvent
import io.vedra.domain.service.SlackEventProcessor
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/slack/events")
class SlackEventsController(
    private val eventProcessor: SlackEventProcessor,
) {
    @PostMapping
    fun slackEvents(
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<Void> {
        val eventPayload = body["event"] as? Map<*, *> ?: return ResponseEntity.ok().build()
        val event = SlackEvent.fromRawPayload(eventPayload)
        eventProcessor.process(event)
        return ResponseEntity.ok().build()
    }
}

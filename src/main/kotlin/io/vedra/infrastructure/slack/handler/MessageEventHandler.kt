package io.vedra.infrastructure.slack.handler

import com.slack.api.model.event.MessageEvent
import io.vedra.infrastructure.outbox.service.OutboxService
import io.vedra.infrastructure.slack.model.SlackEvent
import io.vedra.infrastructure.slack.model.SlackEventHandler
import io.vedra.infrastructure.slack.service.outbox.SlackMessageAnswerHandler
import org.springframework.stereotype.Component

@Component
class MessageEventHandler(
    private val outboxService: OutboxService,
) : SlackEventHandler {
    override fun supports(event: SlackEvent): Boolean = event.type == MessageEvent.TYPE_NAME

    override fun handle(event: SlackEvent) {
        val channel = event.channel ?: return
        val threadTs = event.threadTs ?: return
        val question = event.text ?: return

        outboxService.saveEvent(
            eventType = SlackMessageAnswerHandler.EVENT_TYPE,
            aggregateId = event.eventId,
            payload =
                mapOf(
                    "channel" to channel,
                    "threadTs" to threadTs,
                    "question" to question,
                ),
        )
    }
}

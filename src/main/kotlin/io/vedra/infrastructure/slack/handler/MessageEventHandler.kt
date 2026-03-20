package io.vedra.infrastructure.slack.handler

import com.slack.api.model.event.MessageEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.outbox.service.OutboxService
import io.vedra.infrastructure.slack.model.SlackEvent
import io.vedra.infrastructure.slack.model.SlackEventHandler
import io.vedra.infrastructure.slack.service.outbox.SlackMessageAnswerHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class MessageEventHandler(
    private val outboxService: OutboxService,
) : SlackEventHandler {
    override fun supports(event: SlackEvent): Boolean = event.type == MessageEvent.TYPE_NAME

    override fun handle(event: SlackEvent) {
        val channel = event.channel
        val threadTs = event.threadTs
        val question = event.text

        if (channel == null || threadTs == null || question.isNullOrBlank()) {
            logger.debug { "Skipping incomplete message event: channel=$channel threadTs=$threadTs eventId=${event.eventId}" }
            return
        }

        logger.debug { "Saving message to outbox: channel=$channel threadTs=$threadTs eventId=${event.eventId}" }

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

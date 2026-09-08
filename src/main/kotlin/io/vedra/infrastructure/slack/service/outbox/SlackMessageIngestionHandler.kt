package io.vedra.infrastructure.slack.service.outbox

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.service.MessageIngestionService
import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.outbox.handler.OutboxEventHandler
import org.springframework.stereotype.Component
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Component
class SlackMessageIngestionHandler(
    private val messageIngestionService: MessageIngestionService,
) : OutboxEventHandler {
    override fun supports(eventType: String): Boolean = eventType == EVENT_TYPE

    override fun handle(event: OutboxEvent) {
        val tenantId = event.payload["tenantId"] as? String
        val projectId = event.payload["projectId"] as? String
        val channelId = event.payload["channelId"] as? String
        val messageTs = event.payload["messageTs"] as? String
        val userId = event.payload["userId"] as? String
        val threadTs = event.payload["threadTs"] as? String
        val content = event.payload["content"] as? String

        if (tenantId == null || projectId == null || channelId == null || messageTs == null || userId == null || content == null) {
            logger.warn { "Missing required payload fields in CHANNEL_MESSAGE_INGESTION event: ${event.id}" }
            return
        }

        logger.debug {
            "Processing channel message ingestion: messageTs=$messageTs channelId=$channelId projectId=$projectId"
        }

        messageIngestionService.ingestMessage(
            tenantId = UUID.fromString(tenantId),
            projectId = UUID.fromString(projectId),
            channelId = channelId,
            messageTs = messageTs,
            userId = userId,
            threadTs = threadTs,
            content = content,
        )

        logger.info { "Channel message ingestion completed: messageTs=$messageTs event=${event.id}" }
    }

    companion object {
        const val EVENT_TYPE = "CHANNEL_MESSAGE_INGESTION"
    }
}

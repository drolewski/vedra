package io.vedra.infrastructure.slack.handler

import com.slack.api.model.event.MessageEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.service.TenantProjectService
import io.vedra.infrastructure.outbox.service.OutboxService
import io.vedra.infrastructure.slack.model.SlackEvent
import io.vedra.infrastructure.slack.model.SlackEventHandler
import io.vedra.infrastructure.slack.service.SlackTenantResolver
import io.vedra.infrastructure.slack.service.outbox.SlackMessageAnswerHandler
import io.vedra.infrastructure.slack.service.outbox.SlackMessageIngestionHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class MessageEventHandler(
    private val outboxService: OutboxService,
    private val tenantProjectService: TenantProjectService,
    private val tenantResolver: SlackTenantResolver,
) : SlackEventHandler {
    override fun supports(event: SlackEvent): Boolean = event.type == MessageEvent.TYPE_NAME

    override fun handle(event: SlackEvent) {
        val channel = event.channel
        val text = event.text
        val workspaceId = event.workspaceId
        val userId = event.userId

        logger.info { "MessageEventHandler received: channel=$channel userId=$userId" }

        if (channel == null || text.isNullOrBlank() || workspaceId == null || userId == null) {
            logger.info {
                "Skipping incomplete message event: channel=$channel textBlank=${text.isNullOrBlank()} " +
                    "workspaceId=$workspaceId userId=$userId"
            }
            return
        }

        tenantResolver.withTenantResult(workspaceId) { tenant ->
            val projectIds = tenantProjectService.getProjectIdsForChannel(tenant.id!!, channel)

            if (projectIds.isEmpty()) {
                logger.debug { "No public nor linked project for channel=$channel, skipping" }
                return@withTenantResult
            }

            if (isQuestion(text)) {
                handleQuestion(event, tenant.id!!, workspaceId, channel, text, projectIds)
            } else {
                val specificProjectId = tenantProjectService.getPrivateLinkedProjectId(channel, tenant.id!!)
                if (specificProjectId == null) {
                    logger.debug { "No private project linked to channel=$channel, skipping ingestion" }
                    return@withTenantResult
                }
                handleIngestion(event, tenant.id!!, workspaceId, channel, specificProjectId, text)
            }
        }
    }

    private fun handleQuestion(
        event: SlackEvent,
        tenantId: java.util.UUID,
        teamId: String,
        channel: String,
        question: String,
        projectIds: List<java.util.UUID?>,
    ) {
        val threadTs = event.threadTs

        outboxService.saveEvent(
            eventType = SlackMessageAnswerHandler.EVENT_TYPE,
            aggregateId = event.eventId,
            payload =
                mutableMapOf<String, Any>(
                    "tenantId" to tenantId.toString(),
                    "teamId" to teamId,
                    "channel" to channel,
                    "threadTs" to (threadTs ?: ""),
                    "question" to question,
                    "projectIds" to projectIds.filterNotNull().map { it.toString() },
                ),
        )

        logger.info { "Saved question to outbox: channel=$channel eventId=${event.eventId}" }
    }

    private fun handleIngestion(
        event: SlackEvent,
        tenantId: java.util.UUID,
        teamId: String,
        channel: String,
        projectId: java.util.UUID,
        content: String,
    ) {
        outboxService.saveEvent(
            eventType = SlackMessageIngestionHandler.EVENT_TYPE,
            aggregateId = "ingestion-${event.eventId}",
            payload =
                mutableMapOf<String, Any>(
                    "tenantId" to tenantId.toString(),
                    "teamId" to teamId,
                    "projectId" to projectId.toString(),
                    "channelId" to channel,
                    "messageTs" to (event.eventId ?: ""),
                    "userId" to (event.userId ?: ""),
                    "threadTs" to (event.threadTs ?: ""),
                    "content" to content,
                ),
        )

        logger.info { "Saved channel message for ingestion: channel=$channel eventId=${event.eventId}" }
    }

    private fun isQuestion(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.endsWith("?") || trimmed.lowercase().contains("@vedra")
    }

    companion object {
        private const val INGESTION_AGGREGATE_PREFIX = "ingestion-"
    }
}

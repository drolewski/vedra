package io.vedra.infrastructure.slack.handler

import com.slack.api.model.event.MessageEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.service.TenantProjectService
import io.vedra.domain.service.TenantService
import io.vedra.infrastructure.outbox.service.OutboxService
import io.vedra.infrastructure.slack.model.SlackEvent
import io.vedra.infrastructure.slack.model.SlackEventHandler
import io.vedra.infrastructure.slack.service.outbox.SlackMessageAnswerHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class MessageEventHandler(
    private val outboxService: OutboxService,
    private val tenantProjectService: TenantProjectService,
    private val tenantService: TenantService,
) : SlackEventHandler {
    override fun supports(event: SlackEvent): Boolean = event.type == MessageEvent.TYPE_NAME

    override fun handle(event: SlackEvent) {
        val channel = event.channel
        val threadTs = event.threadTs
        val question = event.text
        val workspaceId = event.workspaceId

        logger.info { "MessageEventHandler received: channel=$channel threadTs=$threadTs text=${question?.take(50)}" }

        if (channel == null || threadTs == null || question.isNullOrBlank()) {
            logger.info { "Skipping incomplete message event: channel=$channel threadTs=$threadTs textBlank=${question.isNullOrBlank()}" }
            return
        }

        val tenant =
            try {
                if (workspaceId != null) {
                    tenantService.resolveTenantByWorkspace(workspaceId)
                } else {
                    tenantService.getDefaultTenant()
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to resolve tenant for workspace=$workspaceId" }
                tenantService.getDefaultTenant()
            }

        val projectIds = tenantProjectService.getProjectIdsForChannel(tenant.id!!, channel)
        logger.info { "Resolved tenant=${tenant.name} projectIds for channel=$channel: $projectIds" }

        outboxService.saveEvent(
            eventType = SlackMessageAnswerHandler.EVENT_TYPE,
            aggregateId = event.eventId,
            payload =
                mutableMapOf<String, Any>(
                    "tenantId" to tenant.id.toString(),
                    "channel" to channel,
                    "threadTs" to threadTs,
                    "question" to question,
                    "projectIds" to projectIds.filterNotNull().map { it.toString() },
                ),
        )

        logger.info { "Saved message to outbox: channel=$channel threadTs=$threadTs eventId=${event.eventId}" }
    }
}

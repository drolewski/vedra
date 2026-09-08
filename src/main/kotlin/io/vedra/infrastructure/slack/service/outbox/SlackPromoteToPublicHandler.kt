package io.vedra.infrastructure.slack.service.outbox

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.repository.feedback.FeedbackMappingRepository
import io.vedra.domain.service.KnowledgePromotionService
import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.outbox.handler.OutboxEventHandler
import io.vedra.infrastructure.slack.service.SlackMessageService
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class SlackPromoteToPublicHandler(
    private val promotionService: KnowledgePromotionService,
    private val feedbackMappingRepository: FeedbackMappingRepository,
    private val slackMessageService: SlackMessageService,
    private val tenantRepository: io.vedra.domain.repository.tenant.TenantRepository,
) : OutboxEventHandler {
    override fun supports(eventType: String): Boolean = eventType == EVENT_TYPE

    override fun handle(event: OutboxEvent) {
        val feedbackId = event.payload["feedbackId"] as? String
        val userId = event.payload["userId"] as? String
        val tenantId = event.payload["tenantId"] as? String
        val channelId = event.payload["channelId"] as? String
        val teamId = event.payload["teamId"] as? String

        if (feedbackId == null || userId == null || tenantId == null) {
            logger.warn { "Missing required payload fields in PROMOTE_TO_PUBLIC event: ${event.id}" }
            return
        }

        val tenantUuid =
            try {
                java.util.UUID.fromString(tenantId)
            } catch (_: IllegalArgumentException) {
                logger.warn { "Invalid tenantId in PROMOTE_TO_PUBLIC event: ${event.id}" }
                return
            }

        val result =
            promotionService.promoteToPublic(
                feedbackId = feedbackId,
                userId = userId,
                tenantId = tenantUuid,
            )

        if (result == KnowledgePromotionService.PromoteResult.PROMOTED && !channelId.isNullOrBlank()) {
            val mapping = feedbackMappingRepository.findById(feedbackId)
            val threadTs = mapping?.messageTs
            if (threadTs != null) {
                try {
                    val resolvedTeamId = teamId ?: tenantRepository.findById(tenantUuid)?.externalId
                    slackMessageService.sendThreadMessage(
                        channel = channelId,
                        threadTs = threadTs,
                        text = "Udostępniono publicznie 🌍",
                        teamId = resolvedTeamId,
                    )
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to send promote confirmation" }
                }
            }
        }

        logger.info { "Promoted entry $feedbackId → public by $userId result=$result" }
    }

    companion object {
        const val EVENT_TYPE = "PROMOTE_TO_PUBLIC"
    }
}

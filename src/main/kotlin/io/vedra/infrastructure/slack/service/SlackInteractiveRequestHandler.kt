package io.vedra.infrastructure.slack.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.outbox.service.OutboxService
import io.vedra.infrastructure.slack.exception.SlackApiException
import io.vedra.infrastructure.slack.exception.SlackPayloadException
import io.vedra.infrastructure.slack.model.dto.SlackInteractionPayload
import io.vedra.infrastructure.slack.service.outbox.SlackFeedbackHandler.Companion.EVENT_TYPE
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class SlackInteractiveRequestHandler(
    private val outboxService: OutboxService,
    private val slackMessageService: SlackMessageService,
) {
    fun handle(payload: Map<String, Any>): ResponseEntity<Any> {
        val interaction =
            try {
                SlackInteractionPayload.fromRawPayload(payload)
            } catch (e: SlackPayloadException) {
                logger.warn(e) { "Invalid Slack interactive payload structure" }
                return ResponseEntity.ok().build()
            }

        logger.info {
            "Processing feedback interaction: feedbackId=${interaction.feedbackId} " +
                "userId=${interaction.userId} vote=${interaction.vote}"
        }

        outboxService.saveEvent(
            eventType = EVENT_TYPE,
            aggregateId = "${interaction.feedbackId}_${interaction.userId}",
            payload =
                mapOf(
                    "feedbackId" to interaction.feedbackId,
                    "userId" to interaction.userId,
                    "vote" to interaction.vote,
                ),
        )

        if (interaction.hasMessageContext) {
            updateMessageButtons(interaction)
        }

        return ResponseEntity.ok().build()
    }

    private fun updateMessageButtons(interaction: SlackInteractionPayload) {
        try {
            slackMessageService.replaceFeedbackButtons(
                channel = interaction.channelId!!,
                messageTs = interaction.messageTs!!,
                originalBlocks = interaction.originalBlocks!!,
                vote = interaction.vote,
            )
        } catch (e: SlackApiException) {
            logger.error(e) {
                "Failed to update feedback buttons for message=${interaction.messageTs} " +
                    "in channel=${interaction.channelId}"
            }
        }
    }
}

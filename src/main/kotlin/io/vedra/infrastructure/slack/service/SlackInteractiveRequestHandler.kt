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
        val interaction = parsePayload(payload) ?: return ResponseEntity.ok().build()
        saveFeedbackEvent(interaction)
        updateMessageButtonsIfNeeded(interaction)
        return ResponseEntity.ok().build()
    }

    private fun parsePayload(payload: Map<String, Any>): SlackInteractionPayload? =
        try {
            SlackInteractionPayload.fromRawPayload(payload).also {
                logger.info {
                    "Processing feedback interaction: feedbackId=${it.feedbackId} " +
                        "userId=${it.userId} vote=${it.vote}"
                }
            }
        } catch (e: SlackPayloadException) {
            logger.warn(e) { "Invalid Slack interactive payload structure" }
            null
        }

    private fun saveFeedbackEvent(interaction: SlackInteractionPayload) {
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
    }

    private fun updateMessageButtonsIfNeeded(interaction: SlackInteractionPayload) {
        if (!interaction.hasMessageContext) return
        updateMessageButtons(interaction)
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

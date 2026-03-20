package io.vedra.infrastructure.slack.api.interactive

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.outbox.service.OutboxService
import io.vedra.infrastructure.slack.exception.SlackApiException
import io.vedra.infrastructure.slack.exception.SlackPayloadException
import io.vedra.infrastructure.slack.model.dto.SlackInteractionPayload
import io.vedra.infrastructure.slack.service.SlackMessageService
import io.vedra.infrastructure.slack.service.outbox.SlackFeedbackHandler.Companion.EVENT_TYPE
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
class SlackInteractiveController(
    private val outboxService: OutboxService,
    private val slackMessageService: SlackMessageService,
) {
    private val objectMapper = jacksonObjectMapper()

    @PostMapping("/api/v1/slack/interactive", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun handleInteractive(
        @RequestParam payload: String,
    ): ResponseEntity<Any> {
        val rawPayload =
            try {
                objectMapper.readValue(payload, Map::class.java) as Map<String, Any>
            } catch (e: Exception) {
                logger.warn(e) { "Failed to parse Slack interactive payload JSON" }
                return ResponseEntity.ok().build()
            }

        val interaction =
            try {
                SlackInteractionPayload.fromRawPayload(rawPayload)
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

package io.vedra.infrastructure.slack.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.model.AnswerResult
import io.vedra.infrastructure.slack.port.SlackClientPort
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class SlackMessageService(
    private val slackClient: SlackClientPort,
    private val messageFormatter: SlackMessageFormatter,
) {
    fun sendThreadMessage(
        channel: String,
        threadTs: String,
        text: String,
    ) {
        logger.debug { "Sending thread message to channel=$channel threadTs=$threadTs" }
        slackClient.postThreadMessage(channel, threadTs, text)
    }

    fun sendChannelMessage(
        channel: String,
        text: String,
    ) {
        logger.debug { "Sending channel message to channel=$channel" }
        slackClient.postChannelMessage(channel, text)
    }

    fun sendThreadMessageWithFeedback(
        channel: String,
        threadTs: String,
        result: AnswerResult,
        feedbackId: String,
    ): String {
        logger.debug { "Sending thread message with feedback to channel=$channel threadTs=$threadTs feedbackId=$feedbackId" }
        val blocks = messageFormatter.buildAnswerBlocks(result, feedbackId)
        val fallbackText = messageFormatter.formatAnswer(result)
        return slackClient.postThreadMessageWithBlocks(channel, threadTs, blocks, fallbackText)
    }

    fun replaceFeedbackButtons(
        channel: String,
        messageTs: String,
        originalBlocks: List<Map<String, Any>>,
        vote: String,
    ) {
        logger.debug { "Replacing feedback buttons for message=$messageTs in channel=$channel vote=$vote" }
        val updatedBlocks = messageFormatter.buildConfirmationBlocks(originalBlocks, vote)
        @Suppress("UNCHECKED_CAST")
        slackClient.updateMessage(channel, messageTs, updatedBlocks as List<Map<String, Any>>)
    }

    fun removePromoteButton(
        channel: String,
        messageTs: String,
        originalBlocks: List<Map<String, Any>>,
    ) {
        logger.debug { "Removing promote button for message=$messageTs in channel=$channel" }
        val updatedBlocks = messageFormatter.buildPromotedBlocks(originalBlocks)
        @Suppress("UNCHECKED_CAST")
        slackClient.updateMessage(channel, messageTs, updatedBlocks as List<Map<String, Any>>)
    }
}

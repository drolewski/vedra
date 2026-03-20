package io.vedra.infrastructure.slack.service.outbox

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.service.FeedbackService
import io.vedra.domain.service.KnowledgeService
import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.outbox.handler.OutboxEventHandler
import io.vedra.infrastructure.slack.service.SlackMessageService
import org.springframework.stereotype.Component
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Component
class SlackMessageAnswerHandler(
    private val knowledgeService: KnowledgeService,
    private val slackMessageService: SlackMessageService,
    private val feedbackService: FeedbackService,
) : OutboxEventHandler {
    override fun supports(eventType: String): Boolean = eventType == EVENT_TYPE

    override fun handle(event: OutboxEvent) {
        val channel = event.payload["channel"] as? String
        val threadTs = event.payload["threadTs"] as? String
        val question = event.payload["question"] as? String

        if (channel == null || threadTs == null || question == null) {
            logger.warn { "Missing required payload fields in SLACK_MESSAGE_ANSWER event: ${event.id}" }
            return
        }

        logger.debug { "Processing answer for question in channel=$channel threadTs=$threadTs" }

        val result = knowledgeService.answerQuestion(question)
        val feedbackId = UUID.randomUUID().toString()

        val messageTs =
            slackMessageService.sendThreadMessageWithFeedback(
                channel = channel,
                threadTs = threadTs,
                result = result,
                feedbackId = feedbackId,
            )

        feedbackService.saveMapping(
            feedbackId = feedbackId,
            channel = channel,
            messageTs = messageTs,
            entryIds = result.entryIds,
        )

        logger.info { "Answer sent for event=${event.id} feedbackId=$feedbackId messageTs=$messageTs" }
    }

    companion object {
        const val EVENT_TYPE = "SLACK_MESSAGE_ANSWER"
    }
}

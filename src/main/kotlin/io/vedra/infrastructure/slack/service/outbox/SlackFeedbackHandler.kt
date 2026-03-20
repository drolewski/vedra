package io.vedra.infrastructure.slack.service.outbox

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.service.FeedbackService
import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.outbox.handler.OutboxEventHandler
import io.vedra.infrastructure.slack.model.dto.SlackInteractionPayload.Companion.VOTE_NEGATIVE
import io.vedra.infrastructure.slack.model.dto.SlackInteractionPayload.Companion.VOTE_POSITIVE
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class SlackFeedbackHandler(
    private val feedbackService: FeedbackService,
) : OutboxEventHandler {
    override fun supports(eventType: String): Boolean = eventType == EVENT_TYPE

    override fun handle(event: OutboxEvent) {
        val feedbackId = event.payload["feedbackId"] as? String
        val userId = event.payload["userId"] as? String
        val vote = event.payload["vote"] as? String

        if (feedbackId == null || userId == null || vote == null) {
            logger.warn { "Missing required payload fields in SLACK_FEEDBACK event: ${event.id}" }
            return
        }

        if (vote !in VALID_VOTES) {
            logger.warn { "Invalid vote value '$vote' in SLACK_FEEDBACK event: ${event.id}" }
            return
        }

        logger.debug { "Applying vote=$vote for feedbackId=$feedbackId userId=$userId" }
        feedbackService.applyVote(feedbackId, userId, vote)
    }

    companion object {
        const val EVENT_TYPE = "SLACK_FEEDBACK"
        private val VALID_VOTES = setOf(VOTE_POSITIVE, VOTE_NEGATIVE)
    }
}

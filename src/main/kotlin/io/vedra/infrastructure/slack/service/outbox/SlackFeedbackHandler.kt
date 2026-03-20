package io.vedra.infrastructure.slack.service.outbox

import io.vedra.domain.service.FeedbackService
import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.outbox.handler.OutboxEventHandler
import org.springframework.stereotype.Component

@Component
class SlackFeedbackHandler(
    private val feedbackService: FeedbackService,
) : OutboxEventHandler {
    override fun supports(eventType: String): Boolean = eventType == "SLACK_FEEDBACK"

    override fun handle(event: OutboxEvent) {
        val feedbackId = event.payload["feedbackId"] as? String ?: return
        val userId = event.payload["userId"] as? String ?: return
        val vote = event.payload["vote"] as? String ?: return

        feedbackService.applyVote(feedbackId, userId, vote)
    }
}

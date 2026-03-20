package io.vedra.infrastructure.slack.handler

import com.slack.api.model.event.ReactionAddedEvent
import io.vedra.domain.service.FeedbackService
import io.vedra.infrastructure.slack.model.SlackEvent
import io.vedra.infrastructure.slack.model.SlackEventHandler
import org.springframework.stereotype.Component

@Component
class ReactionEventHandler(
    private val feedbackService: FeedbackService,
) : SlackEventHandler {
    override fun supports(event: SlackEvent): Boolean = event.type == ReactionAddedEvent.TYPE_NAME

    override fun handle(event: SlackEvent) {
        val reaction = event.reaction ?: return
        val threadTs = event.threadTs ?: return

        when (reaction) {
            "+1", "thumbsup" -> feedbackService.applyPositiveFeedback(threadTs)
            "-1", "thumbsdown" -> feedbackService.applyNegativeFeedback(threadTs)
        }
    }
}

package io.vedra.infrastructure.slack.formatter

import io.vedra.infrastructure.slack.model.dto.SlackInteractionPayload.Companion.ACTION_NEGATIVE
import io.vedra.infrastructure.slack.model.dto.SlackInteractionPayload.Companion.ACTION_POSITIVE

class FeedbackBlockBuilder(
    private val wrapped: SlackBlockBuilder,
    private val feedbackId: String,
) : SlackBlockBuilder {
    override fun build(): List<Map<String, Any>> =
        wrapped.build() +
            listOf(
                mapOf(
                    "type" to "actions",
                    "block_id" to BLOCK_ACTIONS,
                    "elements" to
                        listOf(
                            mapOf(
                                "type" to "button",
                                "text" to mapOf("type" to "plain_text", "text" to LABEL_HELPFUL),
                                "action_id" to ACTION_POSITIVE,
                                "value" to feedbackId,
                                "style" to "primary",
                            ),
                            mapOf(
                                "type" to "button",
                                "text" to mapOf("type" to "plain_text", "text" to LABEL_NOT_HELPFUL),
                                "action_id" to ACTION_NEGATIVE,
                                "value" to feedbackId,
                                "style" to "danger",
                            ),
                        ),
                ),
            )

    companion object {
        const val BLOCK_ACTIONS = "feedback_actions"
        const val LABEL_HELPFUL = "Pomogło :+1:"
        const val LABEL_NOT_HELPFUL = "Nie pomogło :-1:"
    }
}

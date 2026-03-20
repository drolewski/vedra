package io.vedra.infrastructure.slack.service

import io.vedra.domain.model.AnswerResult
import io.vedra.domain.model.ResponseSource
import io.vedra.infrastructure.slack.model.dto.SlackInteractionPayload.Companion.ACTION_NEGATIVE
import io.vedra.infrastructure.slack.model.dto.SlackInteractionPayload.Companion.ACTION_POSITIVE
import org.springframework.stereotype.Component

@Component
class SlackMessageFormatter {
    fun buildAnswerBlocks(
        text: String,
        feedbackId: String,
    ): List<Map<String, Any>> =
        listOf(
            mapOf(
                "type" to "section",
                "text" to mapOf("type" to "mrkdwn", "text" to text),
            ),
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

    fun buildConfirmationBlocks(
        originalBlocks: List<Map<String, Any>>,
        vote: String,
    ): List<Any> {
        val label = resolveVoteLabel(vote)
        val confirmationBlock =
            mapOf(
                "type" to "context",
                "elements" to
                    listOf(
                        mapOf("type" to "mrkdwn", "text" to "_Oddano głos: $label._"),
                    ),
            )

        return originalBlocks.filter { it["block_id"] != BLOCK_ACTIONS } + confirmationBlock
    }

    fun formatAnswer(result: AnswerResult): String {
        val sourceLabel = resolveSourceLabel(result.source)
        return "${result.answer}\n\n$sourceLabel"
    }

    private fun resolveVoteLabel(vote: String): String =
        when (vote) {
            "positive" -> ":thumbsup: Pomogło"
            "negative" -> ":thumbsdown: Nie pomogło"
            else -> vote
        }

    private fun resolveSourceLabel(source: ResponseSource): String =
        when (source) {
            ResponseSource.KNOWLEDGE_BASE -> "_Odpowiedź z bazy wiedzy._"
            ResponseSource.AI_WITH_CONTEXT -> "_Wygenerowane przez AI na podstawie bazy wiedzy._"
            ResponseSource.AI_GENERAL -> "_Wygenerowane przez AI. Brak informacji w bazie wiedzy._"
        }

    companion object {
        const val BLOCK_ACTIONS = "feedback_actions"
        const val LABEL_HELPFUL = "Pomogło :+1:"
        const val LABEL_NOT_HELPFUL = "Nie pomogło :-1:"
    }
}

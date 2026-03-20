package io.vedra.infrastructure.slack.service

import io.vedra.domain.model.AnswerResult
import io.vedra.infrastructure.slack.formatter.AnswerBlockBuilder
import io.vedra.infrastructure.slack.formatter.ConfidenceBlockBuilder
import io.vedra.infrastructure.slack.formatter.FeedbackBlockBuilder
import io.vedra.infrastructure.slack.formatter.HeaderBlockBuilder
import io.vedra.infrastructure.slack.formatter.SourceBlockBuilder
import org.springframework.stereotype.Component

@Component
class SlackMessageFormatter {
    fun buildAnswerBlocks(
        result: AnswerResult,
        feedbackId: String,
    ): List<Map<String, Any>> = buildDecoratedBlocks(result, feedbackId)

    private fun buildDecoratedBlocks(
        result: AnswerResult,
        feedbackId: String,
    ): List<Map<String, Any>> {
        val builder =
            FeedbackBlockBuilder(
                ConfidenceBlockBuilder(
                    SourceBlockBuilder(
                        HeaderBlockBuilder(
                            AnswerBlockBuilder(result),
                        ),
                        result.source,
                    ),
                    result.confidence,
                ),
                feedbackId,
            )
        return builder.build()
    }

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

        return originalBlocks.filter { it["block_id"] != FeedbackBlockBuilder.BLOCK_ACTIONS } + confirmationBlock
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

    private fun resolveSourceLabel(source: io.vedra.domain.model.ResponseSource): String =
        when (source) {
            io.vedra.domain.model.ResponseSource.KNOWLEDGE_BASE -> "_Odpowiedź z bazy wiedzy._"
            io.vedra.domain.model.ResponseSource.AI_WITH_CONTEXT -> "_Wygenerowane przez AI na podstawie bazy wiedzy._"
            io.vedra.domain.model.ResponseSource.AI_GENERAL -> "_Wygenerowane przez AI. Brak informacji w bazie wiedzy._"
        }
}

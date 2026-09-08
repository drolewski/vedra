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

        val baseBlocks = originalBlocks.filter { it["block_id"] != FeedbackBlockBuilder.BLOCK_ACTIONS }
        val promoteButton = extractPromoteButton(originalBlocks)
        if (promoteButton == null) return baseBlocks + confirmationBlock
        val promoteBlock =
            mapOf(
                "type" to "actions",
                "block_id" to FeedbackBlockBuilder.BLOCK_ACTIONS,
                "elements" to listOf(promoteButton),
            )
        return baseBlocks + promoteBlock + confirmationBlock
    }

    fun buildPromotedBlocks(originalBlocks: List<Map<String, Any>>): List<Any> {
        val confirmationBlock =
            mapOf(
                "type" to "context",
                "elements" to
                    listOf(
                        mapOf("type" to "mrkdwn", "text" to "_Udostępniono publicznie 🌍._"),
                    ),
            )

        val baseBlocks = originalBlocks.filter { it["block_id"] != FeedbackBlockBuilder.BLOCK_ACTIONS }
        val actionsBlock =
            originalBlocks.firstOrNull { it["block_id"] == FeedbackBlockBuilder.BLOCK_ACTIONS }

        @Suppress("UNCHECKED_CAST")
        val elements = actionsBlock?.get("elements") as? List<Map<String, Any>> ?: emptyList()
        val remaining = elements.filter { it["action_id"] != ACTION_PROMOTE }
        if (remaining.isEmpty()) return baseBlocks + confirmationBlock
        val updatedActions =
            mapOf(
                "type" to "actions",
                "block_id" to FeedbackBlockBuilder.BLOCK_ACTIONS,
                "elements" to remaining,
            )
        return baseBlocks + updatedActions + confirmationBlock
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractPromoteButton(blocks: List<Map<String, Any>>): Map<String, Any>? {
        val actionsBlock = blocks.firstOrNull { it["block_id"] == FeedbackBlockBuilder.BLOCK_ACTIONS }
        val elements = actionsBlock?.get("elements") as? List<Map<String, Any>> ?: return null
        return elements.firstOrNull { it["action_id"] == ACTION_PROMOTE }
    }

    fun formatAnswer(result: AnswerResult): String {
        val sourceLabel = resolveSourceLabel(result.source)
        return "${result.answer}\n\n$sourceLabel"
    }

    private companion object {
        const val ACTION_PROMOTE = "promote_public"
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

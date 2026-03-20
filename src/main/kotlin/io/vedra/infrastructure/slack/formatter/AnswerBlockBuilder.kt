package io.vedra.infrastructure.slack.formatter

import io.vedra.domain.model.AnswerResult

class AnswerBlockBuilder(
    private val result: AnswerResult,
) : SlackBlockBuilder {
    override fun build(): List<Map<String, Any>> =
        listOf(
            mapOf(
                "type" to "section",
                "text" to mapOf("type" to "mrkdwn", "text" to result.answer),
            ),
        )
}

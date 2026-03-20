package io.vedra.infrastructure.slack.formatter

import io.vedra.domain.model.AnswerResult

class AnswerBlockBuilder(
    private val result: AnswerResult,
) : SlackBlockBuilder {
    override fun build(): List<Map<String, Any>> {
        val formattedText = convertToSlackMrkdwn(result.answer)
        return splitIntoBlocks(formattedText)
    }

    private fun convertToSlackMrkdwn(text: String): String =
        text
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "*$1*")
            .replace(Regex("^- ", RegexOption.MULTILINE), "• ")
            .replace(Regex("^  - ", RegexOption.MULTILINE), "  ◦ ")
            .replace(Regex("^    - ", RegexOption.MULTILINE), "    ▸ ")

    private fun splitIntoBlocks(text: String): List<Map<String, Any>> {
        val sections = text.split(Regex("\n+"))
        return sections.map { section ->
            mapOf(
                "type" to "section",
                "text" to mapOf("type" to "mrkdwn", "text" to section.trim()),
            )
        }
    }
}

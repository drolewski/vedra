package io.vedra.infrastructure.slack.formatter

import io.vedra.domain.model.ResponseSource

class SourceBlockBuilder(
    private val wrapped: SlackBlockBuilder,
    private val source: ResponseSource,
) : SlackBlockBuilder {
    override fun build(): List<Map<String, Any>> =
        wrapped.build() +
            listOf(
                mapOf("type" to "divider"),
                mapOf(
                    "type" to "context",
                    "elements" to
                        listOf(
                            mapOf("type" to "mrkdwn", "text" to "*Źródło:* ${resolveSourceLabel(source)}"),
                        ),
                ),
            )

    private fun resolveSourceLabel(source: ResponseSource): String =
        when (source) {
            ResponseSource.KNOWLEDGE_BASE -> "Baza wiedzy"
            ResponseSource.AI_WITH_CONTEXT -> "AI (kontekst bazy wiedzy)"
            ResponseSource.AI_GENERAL -> "AI (ogólna wiedza)"
        }
}

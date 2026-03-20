package io.vedra.infrastructure.slack.formatter

class HeaderBlockBuilder(
    private val wrapped: SlackBlockBuilder,
    private val header: String = "Odpowiedź",
) : SlackBlockBuilder {
    override fun build(): List<Map<String, Any>> =
        listOf(
            mapOf(
                "type" to "header",
                "text" to mapOf("type" to "plain_text", "text" to header),
            ),
            mapOf("type" to "divider"),
        ) + wrapped.build()
}

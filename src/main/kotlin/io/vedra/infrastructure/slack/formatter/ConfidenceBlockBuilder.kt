package io.vedra.infrastructure.slack.formatter

class ConfidenceBlockBuilder(
    private val wrapped: SlackBlockBuilder,
    private val confidence: Double,
) : SlackBlockBuilder {
    override fun build(): List<Map<String, Any>> =
        wrapped.build() +
            listOf(
                mapOf(
                    "type" to "context",
                    "elements" to
                        listOf(
                            mapOf("type" to "mrkdwn", "text" to "*Pewność:* ${formatConfidence(confidence)}"),
                        ),
                ),
            )

    private fun formatConfidence(confidence: Double): String {
        val percentage = (confidence * 100).toInt()
        val bar = buildProgressBar(percentage)
        return "$bar $percentage%"
    }

    private fun buildProgressBar(percentage: Int): String {
        val filled = percentage / 10
        val empty = 10 - filled
        return "█".repeat(filled) + "░".repeat(empty)
    }
}

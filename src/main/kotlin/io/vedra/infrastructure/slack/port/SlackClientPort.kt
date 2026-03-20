package io.vedra.infrastructure.slack.port

interface SlackClientPort {
    fun postThreadMessage(
        channel: String,
        threadTs: String,
        text: String,
    )

    fun postThreadMessageWithBlocks(
        channel: String,
        threadTs: String,
        blocks: List<Map<String, Any>>,
        fallbackText: String,
    ): String

    fun updateMessage(
        channel: String,
        messageTs: String,
        blocks: List<Map<String, Any>>,
    )
}

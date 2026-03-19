package io.vedra.infrastructure.slack.model

data class SlackEvent(
    val type: String,
    val subtype: String?,
    val botId: String?,
    val text: String?,
    val channel: String?,
    val threadTs: String?,
) {
    val isBotMessage: Boolean
        get() = subtype == "bot_message" || botId != null

    companion object {
        fun fromRawPayload(payload: Map<*, *>): SlackEvent =
            SlackEvent(
                type = payload["type"] as? String ?: "",
                subtype = payload["subtype"] as? String,
                botId = payload["bot_id"] as? String,
                text = payload["text"] as? String,
                channel = payload["channel"] as? String,
                threadTs = payload["ts"] as? String,
            )
    }
}

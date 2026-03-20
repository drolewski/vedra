package io.vedra.infrastructure.slack.model

data class SlackEvent(
    val eventId: String?,
    val type: String,
    val subtype: String?,
    val botId: String?,
    val text: String?,
    val channel: String?,
    val threadTs: String?,
    val reaction: String? = null,
) {
    val isBotMessage: Boolean
        get() = subtype == "bot_message" || botId != null

    companion object {
        fun fromRawPayload(
            payload: Map<*, *>,
            eventId: String? = null,
        ): SlackEvent {
            val item = payload["item"] as? Map<*, *>
            return SlackEvent(
                eventId = eventId,
                type = payload["type"] as? String ?: "",
                subtype = payload["subtype"] as? String,
                botId = payload["bot_id"] as? String,
                text = payload["text"] as? String,
                channel = (payload["channel"] ?: item?.get("channel")) as? String,
                threadTs =
                    payload["thread_ts"] as? String
                        ?: item?.get("ts") as? String
                        ?: payload["ts"] as? String,
                reaction = payload["reaction"] as? String,
            )
        }
    }
}

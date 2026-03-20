package io.vedra.infrastructure.slack.model.dto

import io.vedra.infrastructure.slack.model.SlackEvent

data class SlackEventPayload(
    val eventId: String?,
    val type: String,
    val subtype: String?,
    val botId: String?,
    val text: String?,
    val channel: String?,
    val threadTs: String?,
) {
    val isBotMessage: Boolean
        get() = subtype == "bot_message" || botId != null

    fun toDomain(): SlackEvent =
        SlackEvent(
            eventId = eventId,
            type = type,
            subtype = subtype,
            botId = botId,
            text = text,
            channel = channel,
            threadTs = threadTs,
        )

    companion object {
        fun fromRawBody(body: Map<String, Any>): SlackEventPayload {
            val eventId = body["event_id"] as? String
            val event =
                body["event"] as? Map<*, *>
                    ?: throw IllegalArgumentException("Missing 'event' field in Slack event body")

            return SlackEventPayload(
                eventId = eventId,
                type = event["type"] as? String ?: "",
                subtype = event["subtype"] as? String,
                botId = event["bot_id"] as? String,
                text = event["text"] as? String,
                channel = event["channel"] as? String,
                threadTs = event["thread_ts"] as? String ?: event["ts"] as? String,
            )
        }
    }
}

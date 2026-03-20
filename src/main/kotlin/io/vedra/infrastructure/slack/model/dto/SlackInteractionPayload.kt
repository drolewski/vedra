package io.vedra.infrastructure.slack.model.dto

import io.vedra.infrastructure.slack.exception.SlackPayloadException

data class SlackInteractionPayload(
    val actionId: String,
    val feedbackId: String,
    val userId: String,
    val channelId: String?,
    val messageTs: String?,
    val originalBlocks: List<Map<String, Any>>?,
) {
    val vote: String
        get() =
            when (actionId) {
                ACTION_POSITIVE -> VOTE_POSITIVE
                ACTION_NEGATIVE -> VOTE_NEGATIVE
                else -> throw SlackPayloadException("Unknown action_id: $actionId")
            }

    val hasMessageContext: Boolean
        get() = channelId != null && messageTs != null && originalBlocks != null

    companion object {
        const val ACTION_POSITIVE = "feedback_positive"
        const val ACTION_NEGATIVE = "feedback_negative"
        const val VOTE_POSITIVE = "positive"
        const val VOTE_NEGATIVE = "negative"

        fun fromRawPayload(payload: Map<String, Any>): SlackInteractionPayload {
            val actions =
                payload["actions"] as? List<*>
                    ?: throw SlackPayloadException("Missing 'actions' field")
            val action =
                actions.firstOrNull() as? Map<*, *>
                    ?: throw SlackPayloadException("Empty 'actions' array")

            val actionId =
                action["action_id"] as? String
                    ?: throw SlackPayloadException("Missing 'action_id' in action")
            val feedbackId =
                action["value"] as? String
                    ?: throw SlackPayloadException("Missing 'value' in action")

            val user =
                payload["user"] as? Map<*, *>
                    ?: throw SlackPayloadException("Missing 'user' field")
            val userId =
                user["id"] as? String
                    ?: throw SlackPayloadException("Missing 'user.id' field")

            val message = payload["message"] as? Map<*, *>
            val container = payload["container"] as? Map<*, *>
            val channelId =
                container?.get("channel_id") as? String
                    ?: message?.get("channel") as? String
            val messageTs = message?.get("ts") as? String

            @Suppress("UNCHECKED_CAST")
            val originalBlocks = message?.get("blocks") as? List<Map<String, Any>>

            return SlackInteractionPayload(
                actionId = actionId,
                feedbackId = feedbackId,
                userId = userId,
                channelId = channelId,
                messageTs = messageTs,
                originalBlocks = originalBlocks,
            )
        }
    }
}

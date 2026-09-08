package io.vedra.infrastructure.slack.port

import com.slack.api.methods.request.views.ViewsOpenRequest

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

    fun openModal(request: ViewsOpenRequest)

    fun getTeamName(teamId: String): String?

    fun getChannelName(channelId: String): String?

    fun postChannelMessage(
        channel: String,
        text: String,
    )
}

package io.vedra.infrastructure.slack.port

import com.slack.api.methods.request.views.ViewsOpenRequest

interface SlackClientPort {
    fun postThreadMessage(
        channel: String,
        threadTs: String,
        text: String,
        teamId: String? = null,
    )

    fun postThreadMessageWithBlocks(
        channel: String,
        threadTs: String,
        blocks: List<Map<String, Any>>,
        fallbackText: String,
        teamId: String? = null,
    ): String

    fun updateMessage(
        channel: String,
        messageTs: String,
        blocks: List<Map<String, Any>>,
        teamId: String? = null,
    )

    fun openModal(
        request: ViewsOpenRequest,
        teamId: String? = null,
    )

    fun getTeamName(teamId: String): String?

    fun getChannelName(
        channelId: String,
        teamId: String? = null,
    ): String?

    fun postChannelMessage(
        channel: String,
        text: String,
        teamId: String? = null,
    )
}

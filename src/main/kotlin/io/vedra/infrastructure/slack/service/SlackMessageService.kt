package io.vedra.infrastructure.slack.service

import com.slack.api.bolt.App
import org.springframework.stereotype.Service

@Service
class SlackMessageService(
    private val slackApp: App,
) {
    fun sendThreadMessage(
        channel: String,
        threadTs: String,
        text: String,
    ) {
        slackApp.client().chatPostMessage { req ->
            req
                .channel(channel)
                .threadTs(threadTs)
                .text(text)
        }
    }
}

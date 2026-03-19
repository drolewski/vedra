package io.vedra.infrastructure.slack.filter

import io.vedra.infrastructure.slack.model.SlackEvent
import io.vedra.infrastructure.slack.model.SlackEventFilter
import org.springframework.stereotype.Component

@Component
class BotMessageFilter : SlackEventFilter {
    override fun shouldProcess(event: SlackEvent): Boolean = !event.isBotMessage
}

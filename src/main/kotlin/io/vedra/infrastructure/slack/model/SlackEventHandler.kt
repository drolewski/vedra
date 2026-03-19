package io.vedra.infrastructure.slack.model

interface SlackEventHandler {
    fun supports(event: SlackEvent): Boolean

    fun handle(event: SlackEvent)
}

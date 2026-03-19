package io.vedra.domain.model

interface SlackEventHandler {
    fun supports(event: SlackEvent): Boolean
    fun handle(event: SlackEvent)
}

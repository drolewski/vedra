package io.vedra.infrastructure.slack.model

fun interface SlackEventFilter {
    fun shouldProcess(event: SlackEvent): Boolean
}

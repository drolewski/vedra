package io.vedra.domain.model

fun interface SlackEventFilter {
    fun shouldProcess(event: SlackEvent): Boolean
}

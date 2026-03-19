package io.vedra.infrastructure.outbox.handler

import io.vedra.infrastructure.database.outbox.OutboxEvent

interface OutboxEventHandler {
    fun supports(eventType: String): Boolean

    fun handle(event: OutboxEvent)
}

package io.vedra.infrastructure.outbox.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.outbox.handler.OutboxEventHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Component
class OutboxProcessor(
    private val outboxService: OutboxService,
    private val handlers: List<OutboxEventHandler>,
    @Value($$"${outbox.cleanup-retention-days:7}") private val cleanupRetentionDays: Long,
) {
    @Scheduled(fixedDelayString = $$"${outbox.poll-interval:5000}")
    fun processPendingEvents() {
        val pendingEvents = outboxService.getPendingEvents(limit = 10)

        if (pendingEvents.isEmpty()) {
            return
        }

        logger.info { "Processing ${pendingEvents.size} pending outbox events" }

        for (event in pendingEvents) {
            processEvent(event)
        }
    }

    @Scheduled(cron = "\${outbox.cleanup-cron:0 0 3 * * *}")
    @Transactional
    fun cleanupCompletedEvents() {
        val olderThan = Instant.now().minus(Duration.ofDays(cleanupRetentionDays))
        val deletedCount = outboxService.deleteCompletedOlderThan(olderThan)

        if (deletedCount > 0) {
            logger.info { "Cleaned up $deletedCount completed outbox events older than $cleanupRetentionDays days" }
        }
    }

    private fun processEvent(event: OutboxEvent) {
        try {
            outboxService.markAsProcessing(event)

            val handler = handlers.firstOrNull { it.supports(event.eventType) }

            if (handler == null) {
                logger.warn { "No handler found for event type: ${event.eventType}" }
                outboxService.markAsFailed(event, "No handler found for event type: ${event.eventType}")
                return
            }

            handler.handle(event)
            outboxService.markAsCompleted(event)

            logger.info { "Successfully processed outbox event: ${event.id}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to process outbox event: ${event.id}" }
            outboxService.markAsFailed(event, e.message ?: "Unknown error")
        }
    }
}

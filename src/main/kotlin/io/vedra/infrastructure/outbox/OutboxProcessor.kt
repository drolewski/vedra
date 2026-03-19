package io.vedra.infrastructure.outbox

import io.vedra.domain.service.OutboxService
import io.vedra.infrastructure.database.outbox.OutboxEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OutboxProcessor(
    private val outboxService: OutboxService,
    private val handlers: List<OutboxEventHandler>,
) {
    private val logger = LoggerFactory.getLogger(OutboxProcessor::class.java)

    @Scheduled(fixedDelayString = "\${outbox.poll-interval:5000}")
    @Transactional
    fun processPendingEvents() {
        val pendingEvents = outboxService.getPendingEvents(limit = 10)

        if (pendingEvents.isEmpty()) {
            return
        }

        logger.info("Processing {} pending outbox events", pendingEvents.size)

        for (event in pendingEvents) {
            processEvent(event)
        }
    }

    private fun processEvent(event: OutboxEvent) {
        try {
            outboxService.markAsProcessing(event)

            val handler = handlers.firstOrNull { it.supports(event.eventType) }

            if (handler == null) {
                logger.warn("No handler found for event type: {}", event.eventType)
                outboxService.markAsFailed(event, "No handler found for event type: ${event.eventType}")
                return
            }

            handler.handle(event)
            outboxService.markAsCompleted(event)

            logger.info("Successfully processed outbox event: {}", event.id)
        } catch (e: Exception) {
            logger.error("Failed to process outbox event: {}", event.id, e)
            outboxService.markAsFailed(event, e.message ?: "Unknown error")
        }
    }
}

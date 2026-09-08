package io.vedra.infrastructure.outbox.service

import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.database.outbox.OutboxStatus
import io.vedra.infrastructure.outbox.repository.OutboxEventRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class OutboxService(
    private val outboxEventRepository: OutboxEventRepository,
) {
    @Transactional
    fun saveEvent(
        eventType: String,
        aggregateId: String?,
        payload: Map<String, Any>,
    ): OutboxEvent {
        if (aggregateId != null) {
            findByAggregateId(aggregateId)?.let { return it }
        }

        val event = OutboxEvent(eventType = eventType, aggregateId = aggregateId, payload = payload)

        return if (aggregateId != null) saveWithDedup(event, aggregateId) else outboxEventRepository.save(event)
    }

    @Transactional
    fun getPendingEvents(limit: Int = 10): List<OutboxEvent> = outboxEventRepository.findPendingEvents(limit)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markAsProcessing(event: OutboxEvent) = updateStatus(event) { markAsProcessing() }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markAsCompleted(event: OutboxEvent) = updateStatus(event) { markAsCompleted() }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markAsFailed(
        event: OutboxEvent,
        error: String,
    ) = updateStatus(event) { markAsFailed(error) }

    @Transactional
    fun deleteCompletedOlderThan(olderThan: Instant): Int = outboxEventRepository.deleteCompletedOlderThan(olderThan)

    private fun findByAggregateId(aggregateId: String): OutboxEvent? =
        outboxEventRepository
            .findByAggregateId(aggregateId)
            .firstOrNull()
            ?: outboxEventRepository
                .findByAggregateIdAndStatus(aggregateId, OutboxStatus.PENDING)
                .firstOrNull()

    private fun saveWithDedup(
        event: OutboxEvent,
        aggregateId: String,
    ): OutboxEvent =
        try {
            val saved = outboxEventRepository.save(event)
            outboxEventRepository.flush()
            saved
        } catch (e: DataIntegrityViolationException) {
            findByAggregateId(aggregateId) ?: throw e
        }

    private fun updateStatus(
        event: OutboxEvent,
        transition: OutboxEvent.() -> Unit,
    ) {
        event.transition()
        outboxEventRepository.save(event)
    }
}

package io.vedra.infrastructure.outbox.service

import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.database.outbox.OutboxStatus
import io.vedra.infrastructure.outbox.repository.OutboxEventRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
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
        val existingEvent =
            aggregateId?.let {
                outboxEventRepository
                    .findByAggregateIdAndStatus(it, OutboxStatus.PENDING)
                    .firstOrNull()
            }

        if (existingEvent != null) {
            return existingEvent
        }

        val event =
            OutboxEvent(
                eventType = eventType,
                aggregateId = aggregateId,
                payload = payload,
            )

        return try {
            outboxEventRepository.save(event)
        } catch (e: DataIntegrityViolationException) {
            aggregateId?.let {
                outboxEventRepository
                    .findByAggregateIdAndStatus(it, OutboxStatus.PENDING)
                    .firstOrNull()
            } ?: throw e
        }
    }

    @Transactional(readOnly = true)
    fun getPendingEvents(limit: Int = 10): List<OutboxEvent> = outboxEventRepository.findPendingEvents(limit)

    @Transactional
    fun markAsProcessing(event: OutboxEvent) {
        event.markAsProcessing()
        outboxEventRepository.save(event)
    }

    @Transactional
    fun markAsCompleted(event: OutboxEvent) {
        event.markAsCompleted()
        outboxEventRepository.save(event)
    }

    @Transactional
    fun markAsFailed(
        event: OutboxEvent,
        error: String,
    ) {
        event.markAsFailed(error)
        outboxEventRepository.save(event)
    }

    @Transactional
    fun deleteCompletedOlderThan(olderThan: Instant): Int = outboxEventRepository.deleteCompletedOlderThan(olderThan)
}

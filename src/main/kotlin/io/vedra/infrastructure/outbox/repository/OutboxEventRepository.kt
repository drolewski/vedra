package io.vedra.infrastructure.outbox.repository

import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.database.outbox.OutboxStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface OutboxEventRepository : JpaRepository<OutboxEvent, UUID> {
    fun findByAggregateIdAndStatus(
        aggregateId: String,
        status: OutboxStatus,
    ): List<OutboxEvent>

    fun findByAggregateId(aggregateId: String): List<OutboxEvent>

    @Query(
        value = """
            SELECT * FROM outbox
            WHERE status IN ('PENDING', 'FAILED')
              AND retry_count < max_retries
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun findPendingEvents(limit: Int = 10): List<OutboxEvent>

    @Modifying
    @Query(
        value = """
            DELETE FROM outbox
            WHERE status = 'COMPLETED'
              AND processed_at < :olderThan
        """,
        nativeQuery = true,
    )
    fun deleteCompletedOlderThan(olderThan: Instant): Int
}

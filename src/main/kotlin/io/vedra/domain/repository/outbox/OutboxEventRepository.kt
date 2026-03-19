package io.vedra.domain.repository.outbox

import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.database.outbox.OutboxStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface OutboxEventRepository : JpaRepository<OutboxEvent, UUID> {
    fun findByStatusOrderByCreatedAtAsc(status: OutboxStatus): List<OutboxEvent>

    fun findByAggregateIdAndStatus(
        aggregateId: String,
        status: OutboxStatus,
    ): List<OutboxEvent>

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
}

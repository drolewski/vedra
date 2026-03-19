package io.vedra.infrastructure.database.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "outbox",
    indexes = [
        Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
        Index(name = "idx_outbox_aggregate", columnList = "aggregate_id"),
    ],
)
data class OutboxEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    val id: UUID? = null,
    @Column(name = "event_type", nullable = false, length = 100)
    val eventType: String,
    @Column(name = "aggregate_id")
    val aggregateId: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    val payload: Map<String, Any>,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OutboxStatus = OutboxStatus.PENDING,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "processed_at")
    var processedAt: Instant? = null,
    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,
    @Column(name = "max_retries", nullable = false)
    val maxRetries: Int = 3,
    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,
) {
    constructor() : this(
        eventType = "",
        payload = emptyMap(),
    )

    fun markAsProcessing() {
        status = OutboxStatus.PROCESSING
    }

    fun markAsCompleted() {
        status = OutboxStatus.COMPLETED
        processedAt = Instant.now()
    }

    fun markAsFailed(error: String) {
        status = OutboxStatus.FAILED
        errorMessage = error
        retryCount++
    }

    fun canRetry(): Boolean = retryCount < maxRetries

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OutboxEvent) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}

enum class OutboxStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
}

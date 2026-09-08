package io.vedra.infrastructure.database.cache

import jakarta.persistence.Column
import jakarta.persistence.Entity
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
    name = "llm_response_cache",
    indexes = [
        Index(name = "idx_llm_cache_lookup", columnList = "tenant_id, project_id, question_hash"),
        Index(name = "idx_llm_cache_expires", columnList = "expires_at"),
        Index(name = "idx_llm_cache_tenant", columnList = "tenant_id"),
    ],
)
data class LlmResponseCacheEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    val id: UUID? = null,
    @Column(name = "tenant_id", nullable = false, updatable = false)
    val tenantId: UUID,
    @Column(name = "project_id", updatable = false)
    val projectId: UUID? = null,
    @Column(name = "question_hash", nullable = false, length = 64)
    val questionHash: String,
    @Column(name = "normalized_question", nullable = false, columnDefinition = "TEXT")
    val normalizedQuestion: String,
    @Column(name = "original_question", nullable = false, columnDefinition = "TEXT")
    val originalQuestion: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val answer: String,
    @Column(name = "normalized_answer", columnDefinition = "TEXT")
    val normalizedAnswer: String? = null,
    @Column(name = "model_version", nullable = false, length = 100)
    val modelVersion: String,
    @Column(name = "confidence")
    val confidence: Double? = null,
    @Column(name = "response_source", nullable = false, length = 50)
    val responseSource: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_entry_ids", columnDefinition = "jsonb", nullable = false)
    val contextEntryIds: List<UUID> = emptyList(),
    @Column(name = "usage_count", nullable = false)
    var usageCount: Int = 0,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    constructor() : this(
        tenantId = UUID.randomUUID(),
        questionHash = "",
        normalizedQuestion = "",
        originalQuestion = "",
        answer = "",
        modelVersion = "",
        responseSource = "",
        expiresAt = Instant.now(),
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LlmResponseCacheEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}

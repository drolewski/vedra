package io.vedra.infrastructure.database.knowledge

import com.pgvector.PGvector
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
import org.hibernate.annotations.TypeRegistration
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "knowledge_entries",
    indexes = [
        Index(name = "idx_knowledge_entries_tenant_id", columnList = "tenant_id"),
        Index(name = "idx_knowledge_entries_source", columnList = "tenant_id, source"),
        Index(name = "idx_knowledge_project", columnList = "tenant_id, project_id"),
    ],
)
@TypeRegistration(basicClass = PGvector::class, userType = PGvectorUserType::class)
data class KnowledgeEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    val id: UUID? = null,
    @Column(name = "tenant_id", nullable = false, updatable = false)
    val tenantId: UUID,
    @Column(name = "project_id", updatable = false)
    val projectId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val source: KnowledgeSourceType,
    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,
    @Column(columnDefinition = "TEXT")
    val answer: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    val metadata: Map<String, Any> = emptyMap(),
    @Column(columnDefinition = "vector(768)", nullable = false)
    var embedding: PGvector,
    @Enumerated(EnumType.STRING)
    @Column(name = "answer_source", nullable = false, length = 20)
    val answerSource: AnswerSource = AnswerSource.USER_VERIFIED,
    @Column(name = "quality_score", nullable = false)
    var qualityScore: Float = 0.5f,
    @Column(name = "usage_count", nullable = false)
    var usageCount: Int = 0,
    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null,
    @Column(nullable = false)
    var accepted: Boolean = true,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "normalized_content", columnDefinition = "TEXT")
    val normalizedContent: String? = null,
    @Column(name = "content_hash", length = 64)
    val contentHash: String? = null,
    @Column(name = "reused_count", nullable = false)
    var reusedCount: Int = 0,
) {
    constructor() : this(
        tenantId = UUID.randomUUID(),
        source = KnowledgeSourceType.FAQ,
        content = "",
        embedding = PGvector(FloatArray(768)),
    )

    fun markUsed() {
        usageCount++
        lastUsedAt = Instant.now()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KnowledgeEntry) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String =
        "KnowledgeEntry(id=$id, tenantId=$tenantId, source=$source, answerSource=$answerSource, createdAt=$createdAt)"
}

enum class KnowledgeSourceType {
    FAQ,
    DOCUMENT_TEXT,
    DOCUMENT_IMAGE,
    DOCUMENT_FILE,
    CHANNEL_MESSAGE,
}

enum class AnswerSource {
    USER_VERIFIED,
    AI_GENERATED,
}

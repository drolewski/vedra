package io.vedra.infrastructure.database.cache

import com.pgvector.PGvector
import io.vedra.infrastructure.database.knowledge.PGvectorUserType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.TypeRegistration
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "embedding_cache",
    indexes = [
        Index(name = "idx_embedding_cache_hash", columnList = "text_hash"),
        Index(name = "idx_embedding_cache_expires", columnList = "expires_at"),
    ],
)
@TypeRegistration(basicClass = PGvector::class, userType = PGvectorUserType::class)
data class EmbeddingCacheEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    val id: UUID? = null,
    @Column(name = "text_hash", nullable = false, unique = true, length = 64)
    val textHash: String,
    @Column(name = "normalized_text", nullable = false, columnDefinition = "TEXT")
    val normalizedText: String,
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(768)")
    val embedding: PGvector,
    @Column(name = "model_version", nullable = false, length = 100)
    val modelVersion: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    constructor() : this(
        textHash = "",
        normalizedText = "",
        embedding = PGvector(FloatArray(768)),
        modelVersion = "",
        expiresAt = Instant.now(),
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingCacheEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}

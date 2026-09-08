package io.vedra.domain.model

import com.pgvector.PGvector
import java.time.Instant
import java.util.UUID

data class EmbeddingCacheEntry(
    val id: UUID? = null,
    val textHash: String,
    val normalizedText: String,
    val embedding: PGvector,
    val modelVersion: String,
    val expiresAt: Instant,
    val createdAt: Instant = Instant.now(),
)

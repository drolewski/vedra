package io.vedra.domain.model

import java.time.Instant
import java.util.UUID

data class LlmResponseCacheEntry(
    val id: UUID? = null,
    val tenantId: UUID,
    val projectId: UUID? = null,
    val questionHash: String,
    val normalizedQuestion: String,
    val originalQuestion: String,
    val answer: String,
    val normalizedAnswer: String? = null,
    val modelVersion: String,
    val confidence: Double? = null,
    val responseSource: ResponseSource,
    val contextEntryIds: List<UUID> = emptyList(),
    val usageCount: Int = 0,
    val expiresAt: Instant,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

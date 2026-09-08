package io.vedra.domain.port

import io.vedra.domain.model.LlmResponseCacheEntry
import java.time.Instant
import java.util.UUID

interface LlmResponseCachePort {
    fun findByHash(
        tenantId: UUID,
        projectId: UUID?,
        questionHash: String,
    ): LlmResponseCacheEntry?

    fun save(entry: LlmResponseCacheEntry): LlmResponseCacheEntry

    fun incrementUsage(id: UUID)

    fun deleteByModelVersion(modelVersion: String)

    fun deleteExpired(now: Instant = Instant.now())

    fun countHits(): Long

    fun countMisses(): Long
}

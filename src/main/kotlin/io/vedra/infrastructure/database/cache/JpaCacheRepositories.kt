package io.vedra.infrastructure.database.cache

import io.vedra.domain.model.EmbeddingCacheEntry
import io.vedra.domain.model.LlmResponseCacheEntry
import io.vedra.domain.port.EmbeddingCachePort
import io.vedra.domain.port.LlmResponseCachePort
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class JpaLlmResponseCacheRepository(
    private val springDataRepo: SpringDataLlmResponseCacheRepository,
) : LlmResponseCachePort {
    override fun findByHash(
        tenantId: UUID,
        projectId: UUID?,
        questionHash: String,
    ): LlmResponseCacheEntry? = springDataRepo.findByHash(tenantId, projectId, questionHash)?.toDomain()

    override fun save(entry: LlmResponseCacheEntry): LlmResponseCacheEntry = springDataRepo.save(entry.toEntity()).toDomain()

    override fun incrementUsage(id: UUID) {
        springDataRepo.incrementUsage(id)
    }

    override fun deleteByModelVersion(modelVersion: String) {
        springDataRepo.deleteByModelVersion(modelVersion)
    }

    override fun deleteExpired(now: Instant) {
        springDataRepo.deleteExpired(now)
    }

    override fun countHits(): Long = springDataRepo.countHits()

    override fun countMisses(): Long = springDataRepo.countMisses()
}

@Repository
class JpaEmbeddingCacheRepository(
    private val springDataRepo: SpringDataEmbeddingCacheRepository,
) : EmbeddingCachePort {
    override fun findByHash(textHash: String): EmbeddingCacheEntry? = springDataRepo.findByHash(textHash)?.toDomain()

    override fun save(entry: EmbeddingCacheEntry): EmbeddingCacheEntry = springDataRepo.save(entry.toEntity()).toDomain()

    override fun deleteByModelVersion(modelVersion: String) {
        springDataRepo.deleteByModelVersion(modelVersion)
    }

    override fun countHits(): Long = springDataRepo.countHits()

    override fun countMisses(): Long {
        val total = springDataRepo.countTotal()
        val hits = springDataRepo.countHits()
        return total - hits
    }
}

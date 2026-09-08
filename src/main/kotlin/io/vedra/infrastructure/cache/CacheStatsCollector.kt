package io.vedra.infrastructure.cache

import io.vedra.domain.model.CacheStats
import io.vedra.domain.port.EmbeddingCachePort
import io.vedra.domain.port.LlmResponseCachePort
import io.vedra.domain.service.EmbeddingCacheService
import io.vedra.domain.service.LlmCacheService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CacheStatsCollector(
    private val llmResponseCachePort: LlmResponseCachePort,
    private val embeddingCachePort: EmbeddingCachePort,
    private val llmCacheService: LlmCacheService,
    private val embeddingCacheService: EmbeddingCacheService,
) {
    @Volatile
    private var cachedHits: Long = 0

    @Volatile
    private var cachedMisses: Long = 0

    fun getStats(): CacheStats {
        val llmHits = llmResponseCachePort.countHits()
        val llmMisses = llmResponseCachePort.countMisses()
        val embeddingHits = embeddingCachePort.countHits()
        val embeddingMisses = embeddingCachePort.countMisses()

        val totalHits = llmHits + embeddingHits
        val totalMisses = llmMisses + embeddingMisses
        val total = totalHits + totalMisses

        return CacheStats(
            cacheHits = totalHits,
            cacheMisses = totalMisses,
            cacheHitRatio = if (total > 0) totalHits.toDouble() / total else 0.0,
            llmCallsSaved = llmHits,
            embeddingCallsSaved = embeddingHits,
        )
    }

    @Scheduled(fixedRate = 300000) // every 5 minutes
    fun logStats() {
        val stats = getStats()
        cachedHits = stats.cacheHits
        cachedMisses = stats.cacheMisses
    }

    @Scheduled(cron = "0 0 4 * * *") // daily at 4 AM
    fun cleanupExpiredCache() {
        llmCacheService.cleanupExpired()
    }
}

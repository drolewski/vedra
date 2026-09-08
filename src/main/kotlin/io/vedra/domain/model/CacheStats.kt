package io.vedra.domain.model

data class CacheStats(
    val cacheHits: Long,
    val cacheMisses: Long,
    val cacheHitRatio: Double,
    val llmCallsSaved: Long,
    val embeddingCallsSaved: Long,
)

package io.vedra.domain.port

import io.vedra.domain.model.EmbeddingCacheEntry

interface EmbeddingCachePort {
    fun findByHash(textHash: String): EmbeddingCacheEntry?

    fun save(entry: EmbeddingCacheEntry): EmbeddingCacheEntry

    fun deleteByModelVersion(modelVersion: String)

    fun countHits(): Long

    fun countMisses(): Long
}

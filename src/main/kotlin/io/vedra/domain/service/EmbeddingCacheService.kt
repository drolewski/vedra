package io.vedra.domain.service

import com.pgvector.PGvector
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.model.EmbeddingCacheEntry
import io.vedra.domain.port.EmbeddingCachePort
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

private val log = KotlinLogging.logger {}

@Service
class EmbeddingCacheService(
    private val embeddingCachePort: EmbeddingCachePort,
    @Value("\${vedra.cache.embedding.ttl-days:90}") private val ttlDays: Long = 90,
    @Value("\${vedra.cache.embedding.enabled:true}") private val enabled: Boolean = true,
    @Value("\${spring.ai.ollama.embedding.options.model:nomic-embed-text}") private val modelVersion: String = "nomic-embed-text",
) {
    fun lookup(text: String): PGvector? {
        if (!enabled) return null
        val hash = sha256(text.trim().lowercase())
        return try {
            embeddingCachePort.findByHash(hash)?.embedding
        } catch (e: Exception) {
            log.warn(e) { "Embedding cache lookup failed for hash=$hash" }
            null
        }
    }

    @Async
    fun save(
        text: String,
        embedding: PGvector,
    ) {
        if (!enabled) return
        val normalized = text.trim().lowercase()
        val hash = sha256(normalized)
        try {
            val entry =
                EmbeddingCacheEntry(
                    textHash = hash,
                    normalizedText = normalized,
                    embedding = embedding,
                    modelVersion = modelVersion,
                    expiresAt = Instant.now().plus(ttlDays, ChronoUnit.DAYS),
                )
            embeddingCachePort.save(entry)
            log.debug { "Embedding cached for hash=$hash" }
        } catch (e: Exception) {
            log.warn(e) { "Failed to cache embedding for hash=$hash" }
        }
    }

    fun invalidateByModel(modelVersion: String) {
        try {
            embeddingCachePort.deleteByModelVersion(modelVersion)
            log.info { "Embedding cache invalidated for model=$modelVersion" }
        } catch (e: Exception) {
            log.warn(e) { "Failed to invalidate embedding cache for model=$modelVersion" }
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

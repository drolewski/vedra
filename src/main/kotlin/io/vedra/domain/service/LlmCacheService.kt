package io.vedra.domain.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.model.LlmResponseCacheEntry
import io.vedra.domain.model.ResponseSource
import io.vedra.domain.port.LlmResponseCachePort
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class LlmCacheService(
    private val llmResponseCachePort: LlmResponseCachePort,
    private val answerNormalizer: AnswerNormalizer,
    @Value("\${vedra.cache.llm.ttl-days:30}") private val ttlDays: Long = 30,
    @Value("\${vedra.cache.llm.enabled:true}") private val enabled: Boolean = true,
) {
    fun lookup(
        tenantId: UUID,
        projectId: UUID?,
        questionHash: String,
    ): LlmResponseCacheEntry? {
        if (!enabled) return null
        return try {
            llmResponseCachePort
                .findByHash(tenantId, projectId, questionHash)
                ?.also {
                    llmResponseCachePort.incrementUsage(it.id!!)
                    log.debug { "LLM cache hit for hash=$questionHash, projectId=$projectId" }
                }
        } catch (e: Exception) {
            log.warn(e) { "LLM cache lookup failed for hash=$questionHash" }
            null
        }
    }

    @Async
    fun save(
        tenantId: UUID,
        projectId: UUID?,
        questionHash: String,
        normalizedQuestion: String,
        originalQuestion: String,
        answer: String,
        modelVersion: String,
        confidence: Double?,
        responseSource: ResponseSource,
        contextEntryIds: List<UUID>,
    ) {
        if (!enabled) return
        try {
            val normalized = answerNormalizer.normalize(answer)
            val entry =
                LlmResponseCacheEntry(
                    tenantId = tenantId,
                    projectId = projectId,
                    questionHash = questionHash,
                    normalizedQuestion = normalizedQuestion,
                    originalQuestion = originalQuestion,
                    answer = normalized.normalized,
                    normalizedAnswer = normalized.normalized,
                    modelVersion = modelVersion,
                    confidence = confidence,
                    responseSource = responseSource,
                    contextEntryIds = contextEntryIds,
                    expiresAt = Instant.now().plus(ttlDays, ChronoUnit.DAYS),
                )
            llmResponseCachePort.save(entry)
            log.debug { "LLM response cached for hash=$questionHash" }
        } catch (e: Exception) {
            log.warn(e) { "Failed to cache LLM response for hash=$questionHash" }
        }
    }

    fun invalidateByModel(modelVersion: String) {
        try {
            llmResponseCachePort.deleteByModelVersion(modelVersion)
            log.info { "LLM cache invalidated for model=$modelVersion" }
        } catch (e: Exception) {
            log.warn(e) { "Failed to invalidate LLM cache for model=$modelVersion" }
        }
    }

    fun cleanupExpired() {
        try {
            llmResponseCachePort.deleteExpired()
            log.info { "Expired LLM cache entries cleaned up" }
        } catch (e: Exception) {
            log.warn(e) { "Failed to cleanup expired LLM cache" }
        }
    }
}

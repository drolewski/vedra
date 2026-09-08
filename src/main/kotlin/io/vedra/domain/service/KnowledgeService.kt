package io.vedra.domain.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.model.AnswerResult
import io.vedra.domain.model.AnswerSource
import io.vedra.domain.model.KnowledgeEntry
import io.vedra.domain.model.KnowledgeSourceType
import io.vedra.domain.model.NormalizedQuestion
import io.vedra.domain.model.ResponseSource
import io.vedra.domain.prompt.Prompts
import io.vedra.domain.repository.knowledge.KnowledgeRepository
import io.vedra.domain.repository.knowledge.SimilarityResultProjection
import io.vedra.infrastructure.chat.LLMPort
import io.vedra.infrastructure.config.HybridScoringProperties
import io.vedra.infrastructure.embedding.EmbeddingPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class KnowledgeService(
    private val knowledgeRepository: KnowledgeRepository,
    private val embeddingPort: EmbeddingPort,
    private val llmPort: LLMPort,
    private val questionNormalizer: QuestionNormalizer,
    private val answerNormalizer: AnswerNormalizer,
    private val llmCacheService: LlmCacheService,
    private val embeddingCacheService: EmbeddingCacheService,
    private val hybridScoringService: HybridScoringService,
    private val hybridScoringProperties: HybridScoringProperties,
    @Value("\${spring.ai.ollama.chat.options.model:llama3.2}") private val modelVersion: String = "llama3.2",
) {
    @Transactional
    fun answerQuestion(
        question: String,
        projectIds: List<UUID?>,
        tenantId: UUID,
    ): AnswerResult {
        val projectId = projectIds.firstOrNull()
        val normalized = questionNormalizer.normalize(question, projectId)

        // Priority 1: Check for reusable AI answer in knowledge_entries
        findReusableAiAnswer(normalized, tenantId)?.let { reusable ->
            log.debug { "Reusing existing AI answer for hash=${normalized.hash}" }
            incrementReusedCount(reusable)
            return AnswerResult(
                answer = reusable.answer ?: FALLBACK_ANSWER,
                source = ResponseSource.KNOWLEDGE_BASE,
                confidence =
                    hybridScoringService.finalScore(
                        EXACT_MATCH_SCORE,
                        reusable.qualityScore.toDouble(),
                        reusable.lastUsedAt,
                        reusable.createdAt,
                        true,
                        hybridScoringProperties.toWeights(),
                    ),
                entryIds = listOf(reusable.id!!),
            )
        }

        // Priority 2: Check LLM cache
        val cachedAnswer = llmCacheService.lookup(tenantId, projectId, normalized.hash)
        if (cachedAnswer != null) {
            log.debug { "LLM cache hit for hash=${normalized.hash}" }
            return AnswerResult(
                answer = cachedAnswer.answer,
                source = resolveCachedSource(cachedAnswer),
                confidence = resolveCachedConfidence(cachedAnswer),
                entryIds = cachedAnswer.contextEntryIds,
            )
        }

        // Priority 3 & 4: Embedding + similarity search + LLM
        val embedding = getOrEmbed(question)
        val embeddingString = embedding.toArray().joinToString(",", "[", "]")

        val weights = hybridScoringProperties.toWeights()
        val similarEntries =
            if (hybridScoringProperties.enabled) {
                knowledgeRepository
                    .findSimilarWithHybridScore(
                        tenantId = tenantId,
                        projectIds = projectIds,
                        embedding = embeddingString,
                        limit = 3,
                        minScore = MIN_CONFIDENCE,
                        weights = weights,
                    ).also { results ->
                        log.debug {
                            "Hybrid scoring weights=$weights results=" +
                                results.joinToString {
                                    "${it.id}: final=${it.finalScore} semantic=${it.semanticScore} quality=${it.qualityScore}"
                                }
                        }
                    }
            } else {
                knowledgeRepository.findSimilarWithScore(
                    tenantId = tenantId,
                    projectIds = projectIds,
                    embedding = embeddingString,
                    limit = 3,
                    minScore = MIN_CONFIDENCE,
                )
            }

        if (similarEntries.isNotEmpty()) {
            val acceptedEntries = similarEntries.filter { it.accepted }

            if (acceptedEntries.isNotEmpty() && acceptedEntries.first().score >= HIGH_CONFIDENCE) {
                return answerFromKnowledgeBase(acceptedEntries)
            }

            if (similarEntries.first().score >= MEDIUM_CONFIDENCE) {
                return answerWithContext(normalized, similarEntries, tenantId)
            }
        }

        return answerWithoutContext(normalized, tenantId)
    }

    @Transactional
    fun addKnowledge(
        question: String,
        answer: String,
        projectId: UUID?,
        tenantId: UUID,
    ): KnowledgeEntry {
        val normalized = questionNormalizer.normalize(question, projectId)
        val normalizedAnswer = answerNormalizer.normalize(answer)
        val entry =
            KnowledgeEntry(
                tenantId = tenantId,
                projectId = projectId,
                source = KnowledgeSourceType.FAQ,
                content = question,
                answer = normalizedAnswer.normalized,
                answerSource = AnswerSource.USER_VERIFIED,
                normalizedContent = normalized.normalized,
                contentHash = normalized.hash,
            )
        return knowledgeRepository.save(entry)
    }

    private fun resolveCachedConfidence(cached: io.vedra.domain.model.LlmResponseCacheEntry): Double {
        val base = cached.confidence ?: 0.0
        if (!hybridScoringProperties.enabled || cached.contextEntryIds.isEmpty()) return base
        return try {
            val weights = hybridScoringProperties.toWeights()
            val recomputed =
                knowledgeRepository
                    .findByIds(cached.contextEntryIds)
                    .filter { it.accepted && it.answerSource == AnswerSource.AI_GENERATED }
                    .maxOfOrNull {
                        hybridScoringService.finalScore(
                            EXACT_MATCH_SCORE,
                            it.qualityScore.toDouble(),
                            it.lastUsedAt,
                            it.createdAt,
                            true,
                            weights,
                        )
                    }
            maxOf(base, recomputed ?: base)
        } catch (e: Exception) {
            log.warn(e) { "Failed to resolve cached confidence, returning stored value" }
            base
        }
    }

    private fun resolveCachedSource(cached: io.vedra.domain.model.LlmResponseCacheEntry): ResponseSource {
        if (cached.responseSource == ResponseSource.KNOWLEDGE_BASE) return cached.responseSource
        if (!hybridScoringProperties.enabled) return cached.responseSource
        return try {
            val verified =
                knowledgeRepository.findByIds(cached.contextEntryIds).any {
                    it.accepted && it.answerSource == AnswerSource.AI_GENERATED
                }
            if (verified) ResponseSource.KNOWLEDGE_BASE else cached.responseSource
        } catch (e: Exception) {
            log.warn(e) { "Failed to resolve cached source, returning stored value" }
            cached.responseSource
        }
    }

    private fun findReusableAiAnswer(
        normalized: NormalizedQuestion,
        tenantId: UUID,
    ): KnowledgeEntry? =
        try {
            knowledgeRepository.findReusableAiAnswer(tenantId, normalized.projectId, normalized.hash)
        } catch (e: Exception) {
            log.warn(e) { "Failed to find reusable AI answer" }
            null
        }

    private fun incrementReusedCount(entry: KnowledgeEntry) {
        try {
            val updated = entry.copy(reusedCount = entry.reusedCount + 1)
            knowledgeRepository.save(updated)
        } catch (e: Exception) {
            log.warn(e) { "Failed to increment reused count for entry ${entry.id}" }
        }
    }

    private fun getOrEmbed(text: String): com.pgvector.PGvector {
        val cached = embeddingCacheService.lookup(text)
        if (cached != null) return cached

        val embedding = embeddingPort.embed(text)
        embeddingCacheService.save(text, embedding)
        return embedding
    }

    private fun answerFromKnowledgeBase(entries: List<SimilarityResultProjection>): AnswerResult {
        val best = entries.first()

        knowledgeRepository.findById(best.id)?.let { entry ->
            val updated = entry.copy(usageCount = entry.usageCount + 1)
            knowledgeRepository.save(updated)
        }

        return AnswerResult(
            answer = best.answer ?: FALLBACK_ANSWER,
            source = ResponseSource.KNOWLEDGE_BASE,
            confidence = best.score,
            entryIds = entries.map { it.id },
        )
    }

    private fun answerWithContext(
        normalized: NormalizedQuestion,
        entries: List<SimilarityResultProjection>,
        tenantId: UUID,
    ): AnswerResult {
        val context = buildContext(entries)

        val llmAnswer =
            llmPort.generate(
                Prompts.KnowledgeBase.system,
                Prompts.KnowledgeBase.userPrompt(context, normalized.original),
            )

        if (llmAnswer == null || isNoInfo(llmAnswer)) {
            val fallbackAnswer =
                entries.firstNotNullOfOrNull { it.answer }
                    ?: return answerWithoutContext(normalized, tenantId)

            return AnswerResult(
                answer = fallbackAnswer,
                source = ResponseSource.AI_WITH_CONTEXT,
                confidence = entries.first().score,
                entryIds = entries.map { it.id },
            )
        }

        val normalizedAnswer = answerNormalizer.normalize(llmAnswer)
        val savedEntry = saveAiAnswer(normalized, normalizedAnswer.normalized, tenantId)

        llmCacheService.save(
            tenantId = tenantId,
            projectId = normalized.projectId,
            questionHash = normalized.hash,
            normalizedQuestion = normalized.normalized,
            originalQuestion = normalized.original,
            answer = llmAnswer,
            modelVersion = modelVersion,
            confidence = entries.first().score,
            responseSource = ResponseSource.AI_WITH_CONTEXT,
            contextEntryIds = entries.map { it.id } + listOfNotNull(savedEntry?.id),
        )

        return AnswerResult(
            answer = normalizedAnswer.normalized,
            source = ResponseSource.AI_WITH_CONTEXT,
            confidence = entries.first().score,
            entryIds = entries.map { it.id } + listOfNotNull(savedEntry?.id),
        )
    }

    private fun answerWithoutContext(
        normalized: NormalizedQuestion,
        tenantId: UUID,
    ): AnswerResult {
        val llmAnswer =
            llmPort.generate(
                Prompts.General.system,
                Prompts.General.userPrompt(normalized.original),
            ) ?: FALLBACK_ANSWER

        val normalizedAnswer = answerNormalizer.normalize(llmAnswer)
        val savedEntry = saveAiAnswer(normalized, normalizedAnswer.normalized, tenantId)

        llmCacheService.save(
            tenantId = tenantId,
            projectId = normalized.projectId,
            questionHash = normalized.hash,
            normalizedQuestion = normalized.normalized,
            originalQuestion = normalized.original,
            answer = llmAnswer,
            modelVersion = modelVersion,
            confidence = 0.0,
            responseSource = ResponseSource.AI_GENERAL,
            contextEntryIds = listOfNotNull(savedEntry?.id),
        )

        return AnswerResult(
            answer = normalizedAnswer.normalized,
            source = ResponseSource.AI_GENERAL,
            confidence = 0.0,
            entryIds = listOfNotNull(savedEntry?.id),
        )
    }

    private fun buildContext(entries: List<SimilarityResultProjection>): String =
        entries
            .mapIndexed { index, entry ->
                val answerPart = entry.answer?.let { "\nOdpowiedź: $it" } ?: ""
                "[Źródło ${index + 1}, typ: ${entry.source}, pewność: ${String.format("%.2f", entry.score)}] ${entry.content}$answerPart"
            }.joinToString("\n---\n")

    private fun isNoInfo(answer: String): Boolean {
        val lower = answer.lowercase()
        return NO_INFO_PATTERNS.any { lower.contains(it.lowercase()) }
    }

    private fun saveAiAnswer(
        normalized: NormalizedQuestion,
        answer: String,
        tenantId: UUID,
    ): KnowledgeEntry? =
        try {
            val entry =
                KnowledgeEntry(
                    tenantId = tenantId,
                    projectId = normalized.projectId,
                    source = KnowledgeSourceType.FAQ,
                    content = normalized.original,
                    answer = answer,
                    answerSource = AnswerSource.AI_GENERATED,
                    accepted = false,
                    normalizedContent = normalized.normalized,
                    contentHash = normalized.hash,
                )
            knowledgeRepository.save(entry)
        } catch (_: Exception) {
            null
        }

    companion object {
        private const val HIGH_CONFIDENCE = 0.85
        private const val MEDIUM_CONFIDENCE = 0.65
        private const val MIN_CONFIDENCE = MEDIUM_CONFIDENCE
        private const val EXACT_MATCH_SCORE = 1.0

        private const val FALLBACK_ANSWER = "Nie udało się wygenerować odpowiedzi."

        private val NO_INFO_PATTERNS =
            listOf(
                "brak informacji",
                "nie mam informacji",
                "nie posiadam informacji",
                "nie zawiera informacji",
                "baza wiedzy nie zawiera",
                "nie udało się",
            )
    }
}

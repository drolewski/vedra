package io.vedra.domain.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.model.AnswerSource
import io.vedra.domain.model.FeedbackMapping
import io.vedra.domain.repository.feedback.FeedbackMappingRepository
import io.vedra.domain.repository.knowledge.KnowledgeRepository
import io.vedra.infrastructure.config.HybridScoringProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class FeedbackService(
    private val knowledgeRepository: KnowledgeRepository,
    private val feedbackMappingRepository: FeedbackMappingRepository,
    private val llmCacheService: LlmCacheService,
    private val hybridScoringService: HybridScoringService,
    private val hybridScoringProperties: HybridScoringProperties,
    @Value("\${vedra.rag.quality.auto-promote-threshold:0.8}") private val promoteThreshold: Double = 0.8,
    @Value("\${vedra.rag.quality.auto-promote-min-votes:5}") private val promoteMinVotes: Int = 5,
    @Value("\${vedra.rag.quality.demotion-threshold:0.3}") private val demotionThreshold: Double = 0.3,
) {
    @Transactional
    fun saveMapping(
        feedbackId: String,
        channel: String,
        messageTs: String,
        entryIds: List<UUID>,
    ) {
        if (entryIds.isEmpty()) return
        val mapping =
            FeedbackMapping(
                id = feedbackId,
                channel = channel,
                messageTs = messageTs,
                entryIds = entryIds.map { it.toString() },
            )
        feedbackMappingRepository.save(mapping)
    }

    @Transactional
    fun applyVote(
        feedbackId: String,
        userId: String,
        vote: String,
    ): VoteResult {
        val mapping = feedbackMappingRepository.findById(feedbackId) ?: return VoteResult.NOT_FOUND

        if (mapping.votes.containsKey(userId)) return VoteResult.NO_CHANGE

        val updatedMapping = mapping.copy(votes = mapping.votes + (userId to vote))
        feedbackMappingRepository.save(updatedMapping)

        val entries = knowledgeRepository.findByIds(mapping.entryIds.map { UUID.fromString(it) })
        val newScore = calculateScore(updatedMapping.votes)
        val totalVotes = updatedMapping.votes.size
        val weights = hybridScoringProperties.toWeights()
        entries.forEach { entry ->
            var updatedEntry = entry.copy(qualityScore = newScore)
            if (entry.answerSource == AnswerSource.AI_GENERATED) {
                if (newScore < demotionThreshold) {
                    updatedEntry = updatedEntry.copy(accepted = false)
                } else if (newScore > promoteThreshold && totalVotes >= promoteMinVotes) {
                    updatedEntry = updatedEntry.copy(accepted = true)
                }
            }
            knowledgeRepository.save(updatedEntry)
            log.info { "quality_score ${entry.id}: ${entry.qualityScore} -> $newScore accepted=${updatedEntry.accepted}" }
            val hybridConfidence =
                hybridScoringService.finalScore(
                    EXACT_MATCH_SCORE,
                    newScore.toDouble(),
                    updatedEntry.lastUsedAt,
                    updatedEntry.createdAt,
                    updatedEntry.accepted,
                    weights,
                )
            if (entry.id != null) {
                if (vote == VOTE_POSITIVE) {
                    llmCacheService.refreshConfidenceForEntries(listOf(entry.id), hybridConfidence, LlmCacheService.RefreshMode.RAISE_ONLY)
                } else {
                    llmCacheService.refreshConfidenceForEntries(listOf(entry.id), hybridConfidence, LlmCacheService.RefreshMode.LOWER_ONLY)
                }
            }
        }

        return VoteResult.NEW
    }

    enum class VoteResult {
        NEW,
        NO_CHANGE,
        NOT_FOUND,
    }

    private fun calculateScore(votes: Map<String, String>): Float {
        val total = votes.size
        if (total == 0) return PRIOR.toFloat()
        val positives = votes.count { it.value == "positive" }
        val netVotes = positives - (total - positives)
        return ((CONFIDENCE * PRIOR + netVotes) / (CONFIDENCE + total))
            .coerceIn(0.0, 1.0)
            .toFloat()
    }

    companion object {
        private const val CONFIDENCE = 10.0
        private const val PRIOR = 0.5
        private const val EXACT_MATCH_SCORE = 1.0
        const val VOTE_POSITIVE = "positive"
        const val VOTE_NEGATIVE = "negative"
    }
}

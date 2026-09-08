package io.vedra.domain.service

import io.vedra.domain.model.AnswerSource
import io.vedra.domain.model.FeedbackMapping
import io.vedra.domain.repository.feedback.FeedbackMappingRepository
import io.vedra.domain.repository.knowledge.KnowledgeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FeedbackService(
    private val knowledgeRepository: KnowledgeRepository,
    private val feedbackMappingRepository: FeedbackMappingRepository,
    private val llmCacheService: LlmCacheService,
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
        entries.forEach { entry ->
            var updatedEntry = entry.copy(qualityScore = newScore)
            if (entry.answerSource == AnswerSource.AI_GENERATED) {
                updatedEntry = updatedEntry.copy(accepted = vote == VOTE_POSITIVE)
            }
            knowledgeRepository.save(updatedEntry)
        }

        val entryIds = entries.mapNotNull { it.id }
        if (vote == VOTE_POSITIVE) {
            llmCacheService.refreshConfidenceForEntries(entryIds, VERIFIED_CONFIDENCE, LlmCacheService.RefreshMode.RAISE_ONLY)
        } else {
            llmCacheService.refreshConfidenceForEntries(entryIds, newScore.toDouble(), LlmCacheService.RefreshMode.LOWER_ONLY)
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
        private const val VERIFIED_CONFIDENCE = 1.0
        const val VOTE_POSITIVE = "positive"
        const val VOTE_NEGATIVE = "negative"
    }
}

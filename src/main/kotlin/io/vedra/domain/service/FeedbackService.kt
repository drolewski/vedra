package io.vedra.domain.service

import io.vedra.domain.repository.feedback.FeedbackMappingRepository
import io.vedra.domain.repository.knowledge.KnowledgeRepository
import io.vedra.infrastructure.database.feedback.FeedbackMapping
import io.vedra.infrastructure.database.knowledge.AnswerSource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FeedbackService(
    private val knowledgeRepository: KnowledgeRepository,
    private val feedbackMappingRepository: FeedbackMappingRepository,
) {
    @Transactional
    fun saveMapping(
        threadTs: String,
        entryIds: List<UUID>,
        answerSource: String,
    ) {
        if (entryIds.isEmpty()) return
        val mapping = FeedbackMapping(threadTs = threadTs, entryIds = entryIds, answerSource = answerSource)
        feedbackMappingRepository.save(mapping)
    }

    @Transactional
    fun applyPositiveFeedback(threadTs: String) {
        val mapping = feedbackMappingRepository.findById(threadTs).orElse(null) ?: return
        val entries = knowledgeRepository.findByIds(mapping.entryIds)

        entries.forEach { entry ->
            entry.qualityScore = (entry.qualityScore + POSITIVE_DELTA).coerceAtMost(1.0f)
            if (entry.answerSource == AnswerSource.AI_GENERATED && !entry.accepted) {
                entry.accepted = true
            }
            knowledgeRepository.save(entry)
        }
    }

    @Transactional
    fun applyNegativeFeedback(threadTs: String) {
        val mapping = feedbackMappingRepository.findById(threadTs).orElse(null) ?: return
        val entries = knowledgeRepository.findByIds(mapping.entryIds)

        entries.forEach { entry ->
            entry.qualityScore = (entry.qualityScore + NEGATIVE_DELTA).coerceAtLeast(0.0f)
            knowledgeRepository.save(entry)
        }
    }

    companion object {
        private const val POSITIVE_DELTA = 0.1f
        private const val NEGATIVE_DELTA = -0.2f
    }
}

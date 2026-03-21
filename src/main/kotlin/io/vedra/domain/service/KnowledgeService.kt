package io.vedra.domain.service

import io.vedra.domain.model.AnswerResult
import io.vedra.domain.model.AnswerSource
import io.vedra.domain.model.KnowledgeEntry
import io.vedra.domain.model.KnowledgeSourceType
import io.vedra.domain.model.ResponseSource
import io.vedra.domain.prompt.Prompts
import io.vedra.domain.repository.knowledge.KnowledgeRepository
import io.vedra.domain.repository.knowledge.SimilarityResultProjection
import io.vedra.infrastructure.chat.LLMPort
import io.vedra.infrastructure.embedding.EmbeddingPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class KnowledgeService(
    private val knowledgeRepository: KnowledgeRepository,
    private val embeddingPort: EmbeddingPort,
    private val llmPort: LLMPort,
) {
    @Transactional
    fun answerQuestion(question: String): AnswerResult = answerQuestion(question, listOf(null))

    @Transactional
    fun answerQuestion(
        question: String,
        projectIds: List<UUID?>,
    ): AnswerResult {
        val embedding = embeddingPort.embed(question)
        val embeddingString = embedding.toArray().joinToString(",", "[", "]")

        val similarEntries =
            knowledgeRepository.findSimilarWithScore(
                tenantId = TENANT_ID,
                projectIds = projectIds,
                embedding = embeddingString,
                limit = 3,
                minScore = MIN_CONFIDENCE,
            )

        if (similarEntries.isNotEmpty()) {
            val acceptedEntries = similarEntries.filter { it.accepted }

            if (acceptedEntries.isNotEmpty() && acceptedEntries.first().score >= HIGH_CONFIDENCE) {
                return answerFromKnowledgeBase(acceptedEntries)
            }

            if (similarEntries.first().score >= MEDIUM_CONFIDENCE) {
                return answerWithContext(question, similarEntries)
            }
        }

        return answerWithoutContext(question)
    }

    @Transactional
    fun addKnowledge(
        question: String,
        answer: String,
    ): KnowledgeEntry = addKnowledge(question, answer, null)

    @Transactional
    fun addKnowledge(
        question: String,
        answer: String,
        projectId: UUID?,
    ): KnowledgeEntry {
        val entry =
            KnowledgeEntry(
                tenantId = TENANT_ID,
                projectId = projectId,
                source = KnowledgeSourceType.FAQ,
                content = question,
                answer = answer,
                answerSource = AnswerSource.USER_VERIFIED,
            )
        return knowledgeRepository.save(entry)
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
        question: String,
        entries: List<SimilarityResultProjection>,
    ): AnswerResult {
        val context = buildContext(entries)

        val llmAnswer =
            llmPort.generate(
                Prompts.KnowledgeBase.system,
                Prompts.KnowledgeBase.userPrompt(context, question),
            )

        if (llmAnswer == null || isNoInfo(llmAnswer)) {
            val fallbackAnswer =
                entries.firstNotNullOfOrNull { it.answer }
                    ?: return answerWithoutContext(question)

            return AnswerResult(
                answer = fallbackAnswer,
                source = ResponseSource.AI_WITH_CONTEXT,
                confidence = entries.first().score,
                entryIds = entries.map { it.id },
            )
        }

        val savedEntry = saveAiAnswer(question, llmAnswer)

        return AnswerResult(
            answer = llmAnswer,
            source = ResponseSource.AI_WITH_CONTEXT,
            confidence = entries.first().score,
            entryIds = entries.map { it.id } + listOfNotNull(savedEntry?.id),
        )
    }

    private fun answerWithoutContext(question: String): AnswerResult {
        val llmAnswer =
            llmPort.generate(
                Prompts.General.system,
                Prompts.General.userPrompt(question),
            ) ?: FALLBACK_ANSWER

        val savedEntry = saveAiAnswer(question, llmAnswer)

        return AnswerResult(
            answer = llmAnswer,
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
        question: String,
        answer: String,
        projectId: UUID? = null,
    ): KnowledgeEntry? =
        try {
            val entry =
                KnowledgeEntry(
                    tenantId = TENANT_ID,
                    projectId = projectId,
                    source = KnowledgeSourceType.FAQ,
                    content = question,
                    answer = answer,
                    answerSource = AnswerSource.AI_GENERATED,
                    accepted = false,
                )
            knowledgeRepository.save(entry)
        } catch (_: Exception) {
            null
        }

    companion object {
        val TENANT_ID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private const val HIGH_CONFIDENCE = 0.85
        private const val MEDIUM_CONFIDENCE = 0.65
        private const val MIN_CONFIDENCE = MEDIUM_CONFIDENCE

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

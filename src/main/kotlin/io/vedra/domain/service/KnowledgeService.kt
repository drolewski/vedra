package io.vedra.domain.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.pgvector.PGvector
import io.vedra.domain.prompt.Prompts
import io.vedra.domain.repository.knowledge.KnowledgeRepository
import io.vedra.infrastructure.chat.LLMPort
import io.vedra.infrastructure.database.knowledge.KnowledgeEntry
import io.vedra.infrastructure.database.knowledge.KnowledgeSourceType
import io.vedra.infrastructure.embedding.EmbeddingPort
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class KnowledgeService(
    private val knowledgeRepository: KnowledgeRepository,
    private val embeddingPort: EmbeddingPort,
    private val llmPort: LLMPort,
) {
    private val objectMapper = jacksonObjectMapper()

    fun answerQuestion(question: String): String {
        val embedding = embeddingPort.embed(question)
        val embeddingString = embedding.toArray().joinToString(",", "[", "]")

        val similarEntries =
            knowledgeRepository.findSimilarWithScore(
                tenantId = TENANT_ID,
                embedding = embeddingString,
                limit = 3,
                minScore = 0.7,
            )

        val context =
            similarEntries
                .mapNotNull { entry ->
                    val metadata =
                        entry.metadata?.let {
                            objectMapper.readValue(it, object : TypeReference<Map<String, Any>>() {})
                        } ?: emptyMap()
                    metadata["answer"]?.toString()?.takeIf { it.isNotBlank() }
                }.joinToString("\n---\n")

        if (context.isBlank()) {
            return llmPort.generate(
                Prompts.General.system,
                Prompts.General.userPrompt(question),
            ) ?: "Nie udało się wygenerować odpowiedzi."
        }

        return llmPort.generate(
            Prompts.KnowledgeBase.system(context),
            Prompts.KnowledgeBase.userPrompt(context, question),
        ) ?: "Nie udało się wygenerować odpowiedzi."
    }

    fun addKnowledge(
        question: String,
        answer: String,
    ): KnowledgeEntry {
        val embedding = embeddingPort.embed(question)
        val entry = toEntity(embedding, question, answer)
        return knowledgeRepository.save(entry)
    }

    private fun toEntity(
        embedding: PGvector,
        question: String,
        answer: String,
    ) = KnowledgeEntry(
        tenantId = TENANT_ID,
        source = KnowledgeSourceType.FAQ,
        content = question,
        metadata = mapOf("answer" to answer),
        embedding = embedding,
    )

    companion object {
        val TENANT_ID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

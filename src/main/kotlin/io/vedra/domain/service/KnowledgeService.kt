package io.vedra.domain.service

import com.pgvector.PGvector
import io.vedra.domain.repository.knowledge.KnowledgeRepository
import io.vedra.infrastructure.chat.LLMPort
import io.vedra.infrastructure.database.knowledge.KnowledgeEntry
import io.vedra.infrastructure.database.knowledge.KnowledgeSourceType
import io.vedra.infrastructure.embedding.EmbeddingPort
import org.springframework.stereotype.Service
import java.util.*

@Service
class KnowledgeService(
    private val knowledgeRepository: KnowledgeRepository,
    private val embeddingPort: EmbeddingPort,
    private val LLMPort: LLMPort,
) {
    fun answerQuestion(question: String): String {
        val embedding = embeddingPort.embed(question)
        val context = knowledgeRepository.findSimilarWithScore(TENANT_ID, embedding.toString(), 1)
            .take(1)
            .mapNotNull { it.metadata["answer"] }
            .joinToString("\n---\n")

        if (context.isBlank()) {
            val branch = "budowlanej"
            val generateRandomResponse = """
                Jesteś asystentem AI odpowiedzialnym za knowledgebase w firmie z branży $branch.
                Odpowiadaj TYLKO korzystając z faktów i wiedzy, którą posiadasz. Jeśli pytanie jest zbyt trudne powiedz, że nei wiesz.
                Odpowiadaj w języku polskim. Jeżeli nie dajesz odpowiedzi to powiedz dlaczego.
            """.trimIndent()

            val userQuestion = """
                Pytanie: $question
            """.trimIndent()
            return LLMPort.generate(generateRandomResponse, userQuestion) ?: "Nie udało się wygenerować odpowiedzi."
        }

        val systemPrompt = """
            Jesteś asystentem AI. Odpowiadaj TYLKO na podstawie dostarczonego kontekstu.
            Jeśli kontekst nie zawiera odpowiedzi na pytanie, powiedz że nie wiesz.
            Odpowiadaj w języku polskim.
        """.trimIndent()

        val userPrompt = """
            Kontekst:
            $context
            
            Pytanie: $question
        """.trimIndent()

        return LLMPort.generate(systemPrompt, userPrompt) ?: "Nie udało się wygenerować odpowiedzi."
    }

    fun addKnowledge(
        question: String,
        answer: String,
    ): KnowledgeEntry {
        val embedding = embeddingPort.embed(question)
        val entry = toEntity(embedding, question, answer)
        return knowledgeRepository.save(entry)
    }

    private fun toEntity(embedding: PGvector, question: String, answer: String) = KnowledgeEntry(
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

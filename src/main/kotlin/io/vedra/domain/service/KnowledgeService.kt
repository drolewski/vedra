package io.vedra.domain.service

import com.pgvector.PGvector
import io.vedra.domain.repository.knowledge.KnowledgeRepository
import io.vedra.infrastructure.database.knowledge.KnowledgeEntry
import io.vedra.infrastructure.database.knowledge.KnowledgeSourceType
import io.vedra.infrastructure.embedding.EmbeddingPort
import org.springframework.stereotype.Service
import java.util.*

@Service
class KnowledgeService(
    private val knowledgeRepository: KnowledgeRepository,
    private val embeddingPort: EmbeddingPort,
) {
    fun answerQuestion(question: String): String {
        val embedding = embeddingPort.embed(question)
        return knowledgeRepository.findSimilar(TENANT_ID, embedding.toString())
            .firstOrNull()
            ?.let { return it.answer() ?: "" } ?: ""
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

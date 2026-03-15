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
//        val results = knowledgeRepository.findSimilarByEmbedding(question, 3)

//        return if (results.isNotEmpty()) {
//            results.joinToString("\n---\n") { it.content }
//        } else {
//            "Brak wyników w bazie wiedzy"
//        }
        return ""
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
        val TENANT_ID = UUID.randomUUID()
    }
}

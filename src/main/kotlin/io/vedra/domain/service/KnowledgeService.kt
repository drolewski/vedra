package io.vedra.domain.service

import com.pgvector.PGvector
import io.vedra.domain.repository.knowledge.KnowledgeRepository
import io.vedra.infrastructure.database.knowledge.KnowledgeEntry
import io.vedra.infrastructure.database.knowledge.KnowledgeSourceType
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class KnowledgeService(
    private val knowledgeRepository: KnowledgeRepository,
    private val embeddingModel: EmbeddingModel,
) {
    fun answerQuestion(question: String): String {
        val results = knowledgeRepository.findSimilarByEmbedding(pgVector(question), 3)

        return if (results.isNotEmpty()) {
            results.joinToString("\n---\n") { it.content }
        } else {
            "Brak wyników w bazie wiedzy"
        }
    }

    fun addKnowledge(
        question: String,
        answer: String,
    ) {
        val entry =
            KnowledgeEntry(
                content = question,
                embedding = pgVector(question),
                tenantId = TENANT_ID,
                source = KnowledgeSourceType.FAQ,
                metadata = mapOf("answer" to answer),
            )
        knowledgeRepository.save(entry)
    }

    private fun pgVector(question: String): PGvector = PGvector(embeddingModel.embed(question))

    companion object {
        val TENANT_ID = UUID.randomUUID()
    }
}

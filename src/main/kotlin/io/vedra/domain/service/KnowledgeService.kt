package io.vedra.domain.service

import io.vedra.domain.repository.knowledge.KnowledgeRepository
import io.vedra.infrastructure.database.knowledge.KnowledgeEntry
import io.vedra.infrastructure.database.knowledge.KnowledgeSourceType
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class KnowledgeService(
    private val knowledgeRepository: KnowledgeRepository,
) {

    fun answerQuestion(question: String): String {
        return "Odpowiedź na: $question"
    }

    fun addKnowledge(question: String, answer: String) {
        knowledgeRepository.save(
            KnowledgeEntry(
                tenantId = TENANT_ID,
                source = KnowledgeSourceType.CONVERSATION,
                content = "Q: $question\nA: $answer",
            )
        )
    }

    companion object {
        val TENANT_ID = UUID.randomUUID()
    }
}
package io.vedra.domain.model

import com.pgvector.PGvector
import java.time.Instant
import java.util.UUID

data class KnowledgeEntry(
    val id: UUID? = null,
    val tenantId: UUID,
    val projectId: UUID? = null,
    val source: KnowledgeSourceType,
    val content: String,
    val answer: String? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val embedding: PGvector? = null,
    val answerSource: AnswerSource = AnswerSource.USER_VERIFIED,
    var qualityScore: Float = 0.5f,
    var usageCount: Int = 0,
    var lastUsedAt: Instant? = null,
    var accepted: Boolean = true,
    val createdAt: Instant = Instant.now(),
) {
    fun markUsed() {
        usageCount++
        lastUsedAt = Instant.now()
    }
}

enum class KnowledgeSourceType {
    FAQ,
    DOCUMENT_TEXT,
    DOCUMENT_IMAGE,
    DOCUMENT_FILE,
    CHANNEL_MESSAGE,
}

enum class AnswerSource {
    USER_VERIFIED,
    AI_GENERATED,
}

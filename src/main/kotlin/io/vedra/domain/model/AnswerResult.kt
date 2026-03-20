package io.vedra.domain.model

import java.util.UUID

data class AnswerResult(
    val answer: String,
    val source: ResponseSource,
    val confidence: Double,
    val entryIds: List<UUID> = emptyList(),
) {
    val isFromKnowledgeBase: Boolean get() = source == ResponseSource.KNOWLEDGE_BASE
    val isAiGenerated: Boolean get() = source != ResponseSource.KNOWLEDGE_BASE
}

enum class ResponseSource {
    KNOWLEDGE_BASE,
    AI_WITH_CONTEXT,
    AI_GENERAL,
}

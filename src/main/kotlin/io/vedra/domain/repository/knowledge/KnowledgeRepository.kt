package io.vedra.domain.repository.knowledge

import io.vedra.domain.model.KnowledgeEntry
import java.util.UUID

interface KnowledgeRepository {
    fun findByIds(ids: List<UUID>): List<KnowledgeEntry>

    fun findById(id: UUID): KnowledgeEntry?

    fun save(entry: KnowledgeEntry): KnowledgeEntry

    fun findSimilarWithScore(
        tenantId: UUID,
        projectIds: List<UUID?>,
        embedding: String,
        limit: Int = 5,
        minScore: Double = 0.9,
    ): List<SimilarityResultProjection>

    fun findSimilarGeneralOnly(
        tenantId: UUID,
        embedding: String,
        limit: Int = 5,
        minScore: Double = 0.9,
    ): List<SimilarityResultProjection>
}

data class SimilarityResultProjection(
    val id: UUID,
    val content: String,
    val answer: String?,
    val source: String,
    val accepted: Boolean,
    val score: Double,
)

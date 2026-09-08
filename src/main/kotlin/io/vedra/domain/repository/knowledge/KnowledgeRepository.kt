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

    fun findByContentHash(
        tenantId: UUID,
        projectId: UUID?,
        contentHash: String,
    ): KnowledgeEntry?

    fun findBySourceEntryId(
        tenantId: UUID,
        projectId: UUID,
        sourceEntryId: String,
    ): KnowledgeEntry?

    fun findReusableAiAnswer(
        tenantId: UUID,
        projectId: UUID?,
        contentHash: String,
    ): KnowledgeEntry?
}

data class SimilarityResultProjection(
    val id: UUID,
    val content: String,
    val answer: String?,
    val source: String,
    val accepted: Boolean,
    val score: Double,
)

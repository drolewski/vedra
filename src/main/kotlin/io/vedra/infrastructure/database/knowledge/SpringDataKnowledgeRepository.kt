package io.vedra.infrastructure.database.knowledge

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SpringDataKnowledgeRepository : JpaRepository<KnowledgeEntry, UUID> {
    @Query(
        value = """
            SELECT * FROM knowledge_entries
            WHERE id IN (:ids)
        """,
        nativeQuery = true,
    )
    fun findByIds(ids: List<UUID>): List<KnowledgeEntry>

    @Query(
        value = """
            SELECT   id,
                     content,
                     answer,
                     source,
                     accepted,
                     1 - (embedding <=> CAST(:embedding AS vector)) AS score
            FROM     knowledge_entries
            WHERE    tenant_id  = :tenantId
              AND    answer IS NOT NULL
              AND    (project_id IS NULL OR project_id IN (:projectIds))
              AND    1 - (embedding <=> CAST(:embedding AS vector)) >= :minScore
            ORDER BY score DESC
            LIMIT    :limit
        """,
        nativeQuery = true,
    )
    fun findSimilarWithScore(
        tenantId: UUID,
        projectIds: List<UUID?>,
        embedding: String,
        limit: Int = 5,
        minScore: Double = 0.9,
    ): List<SimilarityResultProjection>

    @Query(
        value = """
            SELECT   id,
                     content,
                     answer,
                     source,
                     accepted,
                     1 - (embedding <=> CAST(:embedding AS vector)) AS score
            FROM     knowledge_entries
            WHERE    tenant_id  = :tenantId
              AND    answer IS NOT NULL
              AND    project_id IS NULL
              AND    1 - (embedding <=> CAST(:embedding AS vector)) >= :minScore
            ORDER BY score DESC
            LIMIT    :limit
        """,
        nativeQuery = true,
    )
    fun findSimilarGeneralOnly(
        tenantId: UUID,
        embedding: String,
        limit: Int = 5,
        minScore: Double = 0.9,
    ): List<SimilarityResultProjection>

    @Query(
        value = """
            SELECT * FROM knowledge_entries
            WHERE tenant_id = :tenantId
              AND content_hash = :contentHash
              AND (project_id = :projectId OR (project_id IS NULL AND :projectId IS NULL))
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findByContentHash(
        tenantId: UUID,
        projectId: UUID?,
        contentHash: String,
    ): KnowledgeEntry?

    @Query(
        value = """
            SELECT * FROM knowledge_entries
            WHERE tenant_id = :tenantId
              AND content_hash = :contentHash
              AND (project_id = :projectId OR (project_id IS NULL AND :projectId IS NULL))
              AND answer_source = 'AI_GENERATED'
              AND quality_score >= 0.7
            ORDER BY quality_score DESC
            LIMIT 1
        """,
        nativeQuery = true,
    )
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

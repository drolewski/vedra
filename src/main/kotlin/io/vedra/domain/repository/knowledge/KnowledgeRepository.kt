package io.vedra.domain.repository.knowledge

import io.vedra.infrastructure.database.knowledge.KnowledgeEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface KnowledgeRepository : JpaRepository<KnowledgeEntry, UUID> {
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
              AND    1 - (embedding <=> CAST(:embedding AS vector)) >= :minScore
            ORDER BY score DESC
            LIMIT    :limit
        """,
        nativeQuery = true,
    )
    fun findSimilarWithScore(
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

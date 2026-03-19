package io.vedra.domain.repository.knowledge

import io.vedra.infrastructure.database.knowledge.KnowledgeEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface KnowledgeRepository : JpaRepository<KnowledgeEntry, UUID> {
    @Query(
        value = """
            SELECT * FROM knowledge_entries
            WHERE  tenant_id = :tenantId
            ORDER  BY embedding <=> CAST(:embedding AS vector)
            LIMIT  :limit
        """,
        nativeQuery = true,
    )
    fun findSimilar(
        tenantId: UUID,
        embedding: String,
        limit: Int = 5,
    ): List<KnowledgeEntry>

    @Query(
        value = """
            SELECT   id,
                     content,
                     metadata,
                     source,
                     1 - (embedding <=> CAST(:embedding AS vector)) AS score
            FROM     knowledge_entries
            WHERE    tenant_id  = :tenantId
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
    val metadata: String?,
    val source: String,
    val score: Double,
)

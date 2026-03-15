package io.vedra.domain.repository.knowledge

import com.pgvector.PGvector
import io.vedra.infrastructure.database.knowledge.KnowledgeEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface KnowledgeRepository : JpaRepository<KnowledgeEntry, UUID> {
    @Query(
        value = """
            SELECT * FROM knowledge_entries
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findSimilarByEmbedding(
        @Param("embedding") embedding: PGvector,
        @Param("limit") limit: Int,
    ): List<KnowledgeEntry>
}

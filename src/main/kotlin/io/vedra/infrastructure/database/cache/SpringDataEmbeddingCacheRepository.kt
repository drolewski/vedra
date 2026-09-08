package io.vedra.infrastructure.database.cache

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface SpringDataEmbeddingCacheRepository : JpaRepository<EmbeddingCacheEntity, UUID> {
    @Query(
        value = """
            SELECT * FROM embedding_cache
            WHERE text_hash = :textHash
              AND expires_at > :now
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findByHash(
        textHash: String,
        now: Instant = Instant.now(),
    ): EmbeddingCacheEntity?

    @Modifying
    @Transactional
    @Query(
        value = """
            DELETE FROM embedding_cache
            WHERE model_version = :modelVersion
        """,
        nativeQuery = true,
    )
    fun deleteByModelVersion(modelVersion: String)

    @Query(
        value = """
            SELECT COUNT(*) FROM embedding_cache
            WHERE text_hash IN (
                SELECT text_hash FROM embedding_cache GROUP BY text_hash HAVING COUNT(*) > 1
            )
        """,
        nativeQuery = true,
    )
    fun countHits(): Long

    @Query(
        value = """
            SELECT COUNT(*) FROM embedding_cache
        """,
        nativeQuery = true,
    )
    fun countTotal(): Long
}

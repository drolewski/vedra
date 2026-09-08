package io.vedra.infrastructure.database.cache

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface SpringDataLlmResponseCacheRepository : JpaRepository<LlmResponseCacheEntity, UUID> {
    @Query(
        value = """
            SELECT * FROM llm_response_cache
            WHERE tenant_id = :tenantId
              AND (project_id = :projectId OR (project_id IS NULL AND :projectId IS NULL))
              AND question_hash = :questionHash
              AND expires_at > :now
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findByHash(
        tenantId: UUID,
        projectId: UUID?,
        questionHash: String,
        now: Instant = Instant.now(),
    ): LlmResponseCacheEntity?

    @Modifying
    @Transactional
    @Query(
        value = """
            UPDATE llm_response_cache
            SET usage_count = usage_count + 1, updated_at = NOW()
            WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun incrementUsage(id: UUID)

    @Modifying
    @Transactional
    @Query(
        value = """
            DELETE FROM llm_response_cache
            WHERE model_version = :modelVersion
        """,
        nativeQuery = true,
    )
    fun deleteByModelVersion(modelVersion: String)

    @Modifying
    @Transactional
    @Query(
        value = """
            DELETE FROM llm_response_cache
            WHERE expires_at < :now
        """,
        nativeQuery = true,
    )
    fun deleteExpired(now: Instant = Instant.now())

    @Query(
        value = """
            SELECT COUNT(*) FROM llm_response_cache
            WHERE usage_count > 0
        """,
        nativeQuery = true,
    )
    fun countHits(): Long

    @Query(
        value = """
            SELECT COUNT(*) FROM llm_response_cache
            WHERE usage_count = 0
        """,
        nativeQuery = true,
    )
    fun countMisses(): Long
}

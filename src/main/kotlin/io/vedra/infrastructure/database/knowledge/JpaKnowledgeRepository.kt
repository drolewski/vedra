package io.vedra.infrastructure.database.knowledge

import io.vedra.domain.model.HybridWeights
import io.vedra.domain.repository.knowledge.KnowledgeRepository
import org.springframework.stereotype.Repository
import java.util.UUID
import io.vedra.domain.model.KnowledgeEntry as DomainKnowledgeEntry
import io.vedra.domain.repository.knowledge.SimilarityResultProjection as DomainSimilarityResultProjection

@Repository
class JpaKnowledgeRepository(
    private val springDataRepo: SpringDataKnowledgeRepository,
) : KnowledgeRepository {
    override fun findByIds(ids: List<UUID>): List<DomainKnowledgeEntry> = springDataRepo.findByIds(ids).map { it.toDomain() }

    override fun findById(id: UUID): DomainKnowledgeEntry? = springDataRepo.findById(id).orElse(null)?.toDomain()

    override fun save(entry: DomainKnowledgeEntry): DomainKnowledgeEntry = springDataRepo.save(entry.toEntity()).toDomain()

    override fun findSimilarWithScore(
        tenantId: UUID,
        projectIds: List<UUID?>,
        embedding: String,
        limit: Int,
        minScore: Double,
    ): List<DomainSimilarityResultProjection> =
        springDataRepo.findSimilarWithScore(tenantId, projectIds, embedding, limit, minScore).map { it.toDomain() }

    override fun findSimilarWithHybridScore(
        tenantId: UUID,
        projectIds: List<UUID?>,
        embedding: String,
        limit: Int,
        minScore: Double,
        weights: HybridWeights,
    ): List<DomainSimilarityResultProjection> =
        springDataRepo
            .findSimilarWithHybridScore(
                tenantId,
                projectIds,
                embedding,
                limit,
                minScore,
                weights.semantic,
                weights.quality,
                weights.freshness,
                weights.acceptance,
                weights.decayDays,
            ).map { it.toDomain() }

    override fun findSimilarGeneralOnly(
        tenantId: UUID,
        embedding: String,
        limit: Int,
        minScore: Double,
    ): List<DomainSimilarityResultProjection> =
        springDataRepo.findSimilarGeneralOnly(tenantId, embedding, limit, minScore).map { it.toDomain() }

    override fun findByContentHash(
        tenantId: UUID,
        projectId: UUID?,
        contentHash: String,
    ): DomainKnowledgeEntry? = springDataRepo.findByContentHash(tenantId, projectId, contentHash)?.toDomain()

    override fun findBySourceEntryId(
        tenantId: UUID,
        projectId: UUID,
        sourceEntryId: String,
    ): DomainKnowledgeEntry? = springDataRepo.findBySourceEntryId(tenantId, projectId, sourceEntryId)?.toDomain()

    override fun findReusableAiAnswer(
        tenantId: UUID,
        projectId: UUID?,
        contentHash: String,
    ): DomainKnowledgeEntry? = springDataRepo.findReusableAiAnswer(tenantId, projectId, contentHash)?.toDomain()
}

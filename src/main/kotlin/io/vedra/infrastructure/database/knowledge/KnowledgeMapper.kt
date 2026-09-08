package io.vedra.infrastructure.database.knowledge

import com.pgvector.PGvector
import io.vedra.domain.model.KnowledgeEntry as DomainKnowledgeEntry
import io.vedra.domain.repository.knowledge.SimilarityResultProjection as DomainSimilarityResultProjection

fun KnowledgeEntry.toDomain(): DomainKnowledgeEntry =
    DomainKnowledgeEntry(
        id = this.id,
        tenantId = this.tenantId,
        projectId = this.projectId,
        source =
            io.vedra.domain.model.KnowledgeSourceType
                .valueOf(this.source.name),
        content = this.content,
        answer = this.answer,
        metadata = this.metadata,
        embedding = this.embedding,
        answerSource =
            io.vedra.domain.model.AnswerSource
                .valueOf(this.answerSource.name),
        qualityScore = this.qualityScore,
        usageCount = this.usageCount,
        lastUsedAt = this.lastUsedAt,
        accepted = this.accepted,
        createdAt = this.createdAt,
        normalizedContent = this.normalizedContent,
        contentHash = this.contentHash,
        reusedCount = this.reusedCount,
    )

fun DomainKnowledgeEntry.toEntity(): KnowledgeEntry =
    KnowledgeEntry(
        id = this.id,
        tenantId = this.tenantId,
        projectId = this.projectId,
        source = KnowledgeSourceType.valueOf(this.source.name),
        content = this.content,
        answer = this.answer,
        metadata = this.metadata,
        embedding = this.embedding ?: PGvector(FloatArray(768)),
        answerSource = AnswerSource.valueOf(this.answerSource.name),
        qualityScore = this.qualityScore,
        usageCount = this.usageCount,
        lastUsedAt = this.lastUsedAt,
        accepted = this.accepted,
        normalizedContent = this.normalizedContent,
        contentHash = this.contentHash,
        reusedCount = this.reusedCount,
    )

fun SimilarityResultProjection.toDomain(): DomainSimilarityResultProjection =
    DomainSimilarityResultProjection(
        id = this.id,
        content = this.content,
        answer = this.answer,
        source = this.source,
        accepted = this.accepted,
        score = this.score,
    )

fun HybridSimilarityResultProjection.toDomain(): DomainSimilarityResultProjection =
    DomainSimilarityResultProjection(
        id = this.id,
        content = this.content,
        answer = this.answer,
        source = this.source,
        accepted = this.accepted,
        score = this.finalScore,
        semanticScore = this.semanticScore,
        qualityScore = this.qualityScore,
        finalScore = this.finalScore,
        lastUsedAt = this.lastUsedAt,
    )

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
        answerSource =
            io.vedra.domain.model.AnswerSource
                .valueOf(this.answerSource.name),
        qualityScore = this.qualityScore,
        usageCount = this.usageCount,
        lastUsedAt = this.lastUsedAt,
        accepted = this.accepted,
        createdAt = this.createdAt,
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
        embedding = PGvector(FloatArray(768)),
        answerSource = AnswerSource.valueOf(this.answerSource.name),
        qualityScore = this.qualityScore,
        usageCount = this.usageCount,
        lastUsedAt = this.lastUsedAt,
        accepted = this.accepted,
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

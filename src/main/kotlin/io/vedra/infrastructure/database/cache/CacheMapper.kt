package io.vedra.infrastructure.database.cache

import io.vedra.domain.model.EmbeddingCacheEntry
import io.vedra.domain.model.LlmResponseCacheEntry
import io.vedra.domain.model.ResponseSource

fun LlmResponseCacheEntity.toDomain(): LlmResponseCacheEntry =
    LlmResponseCacheEntry(
        id = this.id,
        tenantId = this.tenantId,
        projectId = this.projectId,
        questionHash = this.questionHash,
        normalizedQuestion = this.normalizedQuestion,
        originalQuestion = this.originalQuestion,
        answer = this.answer,
        normalizedAnswer = this.normalizedAnswer,
        modelVersion = this.modelVersion,
        confidence = this.confidence,
        responseSource = ResponseSource.valueOf(this.responseSource),
        contextEntryIds = this.contextEntryIds,
        usageCount = this.usageCount,
        expiresAt = this.expiresAt,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun LlmResponseCacheEntry.toEntity(): LlmResponseCacheEntity =
    LlmResponseCacheEntity(
        id = this.id,
        tenantId = this.tenantId,
        projectId = this.projectId,
        questionHash = this.questionHash,
        normalizedQuestion = this.normalizedQuestion,
        originalQuestion = this.originalQuestion,
        answer = this.answer,
        normalizedAnswer = this.normalizedAnswer,
        modelVersion = this.modelVersion,
        confidence = this.confidence,
        responseSource = this.responseSource.name,
        contextEntryIds = this.contextEntryIds,
        usageCount = this.usageCount,
        expiresAt = this.expiresAt,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun EmbeddingCacheEntity.toDomain(): EmbeddingCacheEntry =
    EmbeddingCacheEntry(
        id = this.id,
        textHash = this.textHash,
        normalizedText = this.normalizedText,
        embedding = this.embedding,
        modelVersion = this.modelVersion,
        expiresAt = this.expiresAt,
        createdAt = this.createdAt,
    )

fun EmbeddingCacheEntry.toEntity(): EmbeddingCacheEntity =
    EmbeddingCacheEntity(
        id = this.id,
        textHash = this.textHash,
        normalizedText = this.normalizedText,
        embedding = this.embedding,
        modelVersion = this.modelVersion,
        expiresAt = this.expiresAt,
        createdAt = this.createdAt,
    )

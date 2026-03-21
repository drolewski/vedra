package io.vedra.domain.repository.feedback

import io.vedra.domain.model.FeedbackMapping

interface FeedbackMappingRepository {
    fun findById(id: String): FeedbackMapping?

    fun save(feedbackMapping: FeedbackMapping): FeedbackMapping
}

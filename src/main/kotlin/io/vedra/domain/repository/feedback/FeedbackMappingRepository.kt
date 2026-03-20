package io.vedra.domain.repository.feedback

import io.vedra.infrastructure.database.feedback.FeedbackMapping
import org.springframework.data.jpa.repository.JpaRepository

interface FeedbackMappingRepository : JpaRepository<FeedbackMapping, String>

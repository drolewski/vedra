package io.vedra.infrastructure.database.feedback

import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataFeedbackMappingRepository : JpaRepository<FeedbackMapping, String>

package io.vedra.infrastructure.database.feedback

import io.vedra.domain.repository.feedback.FeedbackMappingRepository
import org.springframework.stereotype.Repository
import io.vedra.domain.model.FeedbackMapping as DomainFeedbackMapping

@Repository
class JpaFeedbackMappingRepository(
    private val springDataRepo: SpringDataFeedbackMappingRepository,
) : FeedbackMappingRepository {
    override fun findById(id: String): DomainFeedbackMapping? = springDataRepo.findById(id).orElse(null)?.toDomain()

    override fun save(feedbackMapping: DomainFeedbackMapping): DomainFeedbackMapping =
        springDataRepo.save(feedbackMapping.toEntity()).toDomain()
}

package io.vedra.domain.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.model.AnswerSource
import io.vedra.domain.model.KnowledgeEntry
import io.vedra.domain.repository.feedback.FeedbackMappingRepository
import io.vedra.domain.repository.knowledge.KnowledgeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class KnowledgePromotionService(
    private val feedbackMappingRepository: FeedbackMappingRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val tenantProjectService: TenantProjectService,
) {
    @Transactional
    fun promoteToPublic(
        feedbackId: String,
        userId: String,
        tenantId: UUID,
    ): PromoteResult {
        val mapping = feedbackMappingRepository.findById(feedbackId) ?: return PromoteResult.NOT_FOUND
        if (mapping.entryIds.isEmpty()) return PromoteResult.NOTHING_TO_PROMOTE

        val publicProject = tenantProjectService.ensurePublicProject(tenantId)
        val publicId = publicProject.id!!

        val entries = knowledgeRepository.findByIds(mapping.entryIds.map { UUID.fromString(it) })
        if (entries.isEmpty()) return PromoteResult.NOTHING_TO_PROMOTE

        var copied = 0
        entries.filter { it.accepted }.forEach { entry ->
            if (entry.tenantId != tenantId) {
                logger.warn { "Skipping entry ${entry.id} from other tenant" }
                return@forEach
            }
            if (entry.projectId == publicId) return@forEach
            if (knowledgeRepository.findBySourceEntryId(tenantId, publicId, entry.id.toString()) != null) {
                return@forEach
            }
            if (entry.contentHash != null &&
                knowledgeRepository.findByContentHash(tenantId, publicId, entry.contentHash) != null
            ) {
                return@forEach
            }
            val copy =
                KnowledgeEntry(
                    tenantId = tenantId,
                    projectId = publicId,
                    source = entry.source,
                    content = entry.content,
                    answer = entry.answer,
                    metadata =
                        entry.metadata +
                            mapOf(
                                "source_entry_id" to entry.id.toString(),
                                "source_project_id" to (entry.projectId?.toString() ?: ""),
                                "source_channel_id" to mapping.channel,
                                "promoted_by" to userId,
                                "promoted_at" to Instant.now().toString(),
                            ),
                    embedding = entry.embedding,
                    answerSource = AnswerSource.USER_VERIFIED,
                    qualityScore = maxOf(entry.qualityScore, 0.8f),
                    accepted = true,
                    normalizedContent = entry.normalizedContent,
                    contentHash = entry.contentHash,
                )
            knowledgeRepository.save(copy)
            copied++
        }

        logger.info { "Promoted $copied entries to public by $userId feedbackId=$feedbackId" }
        return if (copied > 0) PromoteResult.PROMOTED else PromoteResult.NOTHING_TO_PROMOTE
    }

    enum class PromoteResult {
        PROMOTED,
        NOT_FOUND,
        NOTHING_TO_PROMOTE,
    }
}

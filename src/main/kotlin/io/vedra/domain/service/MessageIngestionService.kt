package io.vedra.domain.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.model.KnowledgeEntry
import io.vedra.domain.model.KnowledgeSourceType
import io.vedra.domain.port.MessageUsefulnessPort
import io.vedra.domain.repository.knowledge.KnowledgeRepository
import io.vedra.domain.repository.project.TenantProjectRepository
import io.vedra.infrastructure.embedding.EmbeddingPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class MessageIngestionService(
    private val knowledgeRepository: KnowledgeRepository,
    private val embeddingPort: EmbeddingPort,
    private val messageUsefulnessPort: MessageUsefulnessPort,
    private val tenantProjectRepository: TenantProjectRepository,
) {
    @Transactional
    fun ingestMessage(
        tenantId: UUID,
        projectId: UUID,
        channelId: String,
        messageTs: String,
        userId: String,
        threadTs: String?,
        content: String,
    ) {
        val project =
            tenantProjectRepository.findByTenantIdAndId(tenantId, projectId.toString())
                ?: run {
                    logger.warn { "Project not found: projectId=$projectId for tenant=$tenantId" }
                    return
                }

        val usefulness = messageUsefulnessPort.evaluate(content, project, userId)

        if (usefulness == null) {
            logger.warn { "Failed to evaluate message usefulness: messageTs=$messageTs" }
            return
        }

        if (!usefulness.useful) {
            logger.debug {
                "Message not useful, skipping ingestion: messageTs=$messageTs reason=${usefulness.reason}"
            }
            // return // fix this usefulness
        }

        val textToEmbed = usefulness.summary.ifBlank { content }
        val embedding = embeddingPort.embed(textToEmbed)

        val entry =
            KnowledgeEntry(
                tenantId = tenantId,
                projectId = projectId,
                source = KnowledgeSourceType.CHANNEL_MESSAGE,
                content = textToEmbed,
                metadata =
                    mapOf(
                        "slack_message_ts" to messageTs,
                        "slack_channel_id" to channelId,
                        "slack_user_id" to userId,
                        "thread_ts" to (threadTs ?: ""),
                        "usefulness_reason" to usefulness.reason,
                        "original_content" to content,
                    ),
                embedding = embedding,
                qualityScore = INITIAL_QUALITY_SCORE,
                accepted = true,
            )

        knowledgeRepository.save(entry)

        logger.info {
            "Ingested channel message to knowledge base: messageTs=$messageTs projectId=$projectId"
        }
    }

    companion object {
        private const val INITIAL_QUALITY_SCORE = 0.7f
    }
}

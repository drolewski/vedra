package io.vedra.domain.service

import com.pgvector.PGvector
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.domain.model.AnswerSource
import io.vedra.domain.model.FeedbackMapping
import io.vedra.domain.model.KnowledgeEntry
import io.vedra.domain.model.KnowledgeSourceType
import io.vedra.domain.model.ProjectVisibility
import io.vedra.domain.model.TenantProject
import io.vedra.domain.repository.feedback.FeedbackMappingRepository
import io.vedra.domain.repository.knowledge.KnowledgeRepository
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class KnowledgePromotionServiceTest {
    private val feedbackMappingRepository: FeedbackMappingRepository = mockk()
    private val knowledgeRepository: KnowledgeRepository = mockk()
    private val tenantProjectService: TenantProjectService = mockk()
    private val service =
        KnowledgePromotionService(feedbackMappingRepository, knowledgeRepository, tenantProjectService)

    @Test
    fun `should copy accepted entry to public project`() {
        val tenantId = UUID.randomUUID()
        val privateId = UUID.randomUUID()
        val publicId = UUID.randomUUID()
        val entryId = UUID.randomUUID()

        every { feedbackMappingRepository.findById("fb-1") } returns
            FeedbackMapping(id = "fb-1", channel = "C1", messageTs = "1.0", entryIds = listOf(entryId.toString()))
        every { tenantProjectService.ensurePublicProject(tenantId) } returns
            TenantProject(id = publicId, tenantId = tenantId, name = "Baza ogólna", visibility = ProjectVisibility.PUBLIC)
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns
            listOf(
                KnowledgeEntry(
                    id = entryId,
                    tenantId = tenantId,
                    projectId = privateId,
                    source = KnowledgeSourceType.CHANNEL_MESSAGE,
                    content = "fakt",
                    answer = "odp",
                    embedding = PGvector(FloatArray(768)),
                    answerSource = AnswerSource.USER_VERIFIED,
                    contentHash = "h1",
                ),
            )
        every { knowledgeRepository.findBySourceEntryId(tenantId, publicId, entryId.toString()) } returns null
        every { knowledgeRepository.findByContentHash(tenantId, publicId, "h1") } returns null
        every { knowledgeRepository.save(any()) } answers { firstArg() }

        val result = service.promoteToPublic("fb-1", "U1", tenantId)

        assertEquals(KnowledgePromotionService.PromoteResult.PROMOTED, result)
        verify {
            knowledgeRepository.save(
                match {
                    it.projectId == publicId &&
                        it.metadata["source_entry_id"] == entryId.toString() &&
                        it.metadata["promoted_by"] == "U1"
                },
            )
        }
    }

    @Test
    fun `should return NOT_FOUND when mapping missing`() {
        every { feedbackMappingRepository.findById("missing") } returns null

        val result = service.promoteToPublic("missing", "U1", UUID.randomUUID())

        assertEquals(KnowledgePromotionService.PromoteResult.NOT_FOUND, result)
    }

    @Test
    fun `should be idempotent when copy with source_entry_id already exists`() {
        val tenantId = UUID.randomUUID()
        val privateId = UUID.randomUUID()
        val publicId = UUID.randomUUID()
        val entryId = UUID.randomUUID()

        every { feedbackMappingRepository.findById("fb-2") } returns
            FeedbackMapping(id = "fb-2", channel = "C1", messageTs = "1.0", entryIds = listOf(entryId.toString()))
        every { tenantProjectService.ensurePublicProject(tenantId) } returns
            TenantProject(id = publicId, tenantId = tenantId, name = "Baza ogólna", visibility = ProjectVisibility.PUBLIC)
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns
            listOf(
                KnowledgeEntry(
                    id = entryId,
                    tenantId = tenantId,
                    projectId = privateId,
                    source = KnowledgeSourceType.CHANNEL_MESSAGE,
                    content = "fakt",
                    answer = "odp",
                    embedding = PGvector(FloatArray(768)),
                    answerSource = AnswerSource.USER_VERIFIED,
                    contentHash = null,
                ),
            )
        every { knowledgeRepository.findBySourceEntryId(tenantId, publicId, entryId.toString()) } returns
            KnowledgeEntry(
                id = UUID.randomUUID(),
                tenantId = tenantId,
                projectId = publicId,
                source = KnowledgeSourceType.CHANNEL_MESSAGE,
                content = "fakt",
                answer = "odp",
                embedding = PGvector(FloatArray(768)),
            )

        val result = service.promoteToPublic("fb-2", "U1", tenantId)

        assertEquals(KnowledgePromotionService.PromoteResult.NOTHING_TO_PROMOTE, result)
        verify(exactly = 0) { knowledgeRepository.save(any()) }
    }

    @Test
    fun `should skip non-accepted entries`() {
        val tenantId = UUID.randomUUID()
        val privateId = UUID.randomUUID()
        val publicId = UUID.randomUUID()
        val entryId = UUID.randomUUID()

        every { feedbackMappingRepository.findById("fb-3") } returns
            FeedbackMapping(id = "fb-3", channel = "C1", messageTs = "1.0", entryIds = listOf(entryId.toString()))
        every { tenantProjectService.ensurePublicProject(tenantId) } returns
            TenantProject(id = publicId, tenantId = tenantId, name = "Baza ogólna", visibility = ProjectVisibility.PUBLIC)
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns
            listOf(
                KnowledgeEntry(
                    id = entryId,
                    tenantId = tenantId,
                    projectId = privateId,
                    source = KnowledgeSourceType.CHANNEL_MESSAGE,
                    content = "fakt",
                    answer = "odp",
                    embedding = PGvector(FloatArray(768)),
                    accepted = false,
                ),
            )

        val result = service.promoteToPublic("fb-3", "U1", tenantId)

        assertEquals(KnowledgePromotionService.PromoteResult.NOTHING_TO_PROMOTE, result)
        verify(exactly = 0) { knowledgeRepository.save(any()) }
    }

    @Test
    fun `should skip entries from other tenant`() {
        val tenantId = UUID.randomUUID()
        val otherTenant = UUID.randomUUID()
        val privateId = UUID.randomUUID()
        val publicId = UUID.randomUUID()
        val entryId = UUID.randomUUID()

        every { feedbackMappingRepository.findById("fb-4") } returns
            FeedbackMapping(id = "fb-4", channel = "C1", messageTs = "1.0", entryIds = listOf(entryId.toString()))
        every { tenantProjectService.ensurePublicProject(tenantId) } returns
            TenantProject(id = publicId, tenantId = tenantId, name = "Baza ogólna", visibility = ProjectVisibility.PUBLIC)
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns
            listOf(
                KnowledgeEntry(
                    id = entryId,
                    tenantId = otherTenant,
                    projectId = privateId,
                    source = KnowledgeSourceType.CHANNEL_MESSAGE,
                    content = "fakt",
                    answer = "odp",
                    embedding = PGvector(FloatArray(768)),
                ),
            )

        val result = service.promoteToPublic("fb-4", "U1", tenantId)

        assertEquals(KnowledgePromotionService.PromoteResult.NOTHING_TO_PROMOTE, result)
        verify(exactly = 0) { knowledgeRepository.save(any()) }
    }
}

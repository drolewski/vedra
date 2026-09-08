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
import io.vedra.infrastructure.embedding.EmbeddingPort
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KnowledgePromotionServiceTest {
    private val feedbackMappingRepository: FeedbackMappingRepository = mockk()
    private val knowledgeRepository: KnowledgeRepository = mockk()
    private val tenantProjectService: TenantProjectService = mockk()
    private val embeddingPort: EmbeddingPort = mockk()
    private val service =
        KnowledgePromotionService(feedbackMappingRepository, knowledgeRepository, tenantProjectService, embeddingPort)

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
                    embedding = PGvector(FloatArray(768) { 0.5f }),
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
                    embedding = PGvector(FloatArray(768) { 0.5f }),
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
                embedding = PGvector(FloatArray(768) { 0.5f }),
            )

        val result = service.promoteToPublic("fb-2", "U1", tenantId)

        assertEquals(KnowledgePromotionService.PromoteResult.NOTHING_TO_PROMOTE, result)
        verify(exactly = 0) { knowledgeRepository.save(any()) }
    }

    @Test
    fun `should promote non-accepted entries as user verified`() {
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
                    embedding = PGvector(FloatArray(768) { 0.5f }),
                    accepted = false,
                ),
            )

        every { knowledgeRepository.findBySourceEntryId(tenantId, publicId, entryId.toString()) } returns null
        every { knowledgeRepository.findByContentHash(any(), any(), any()) } returns null
        every { knowledgeRepository.save(any()) } answers { firstArg() }

        val result = service.promoteToPublic("fb-3", "U1", tenantId)

        assertEquals(KnowledgePromotionService.PromoteResult.PROMOTED, result)
        verify { knowledgeRepository.save(match { it.accepted && it.projectId == publicId }) }
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
                    embedding = PGvector(FloatArray(768) { 0.5f }),
                ),
            )

        val result = service.promoteToPublic("fb-4", "U1", tenantId)

        assertEquals(KnowledgePromotionService.PromoteResult.NOTHING_TO_PROMOTE, result)
        verify(exactly = 0) { knowledgeRepository.save(any()) }
    }

    @Test
    fun `should embed copy when source embedding is missing`() {
        val tenantId = UUID.randomUUID()
        val privateId = UUID.randomUUID()
        val publicId = UUID.randomUUID()
        val entryId = UUID.randomUUID()
        val freshEmbedding = PGvector(FloatArray(768) { 0.7f })

        every { feedbackMappingRepository.findById("fb-5") } returns
            FeedbackMapping(id = "fb-5", channel = "C1", messageTs = "1.0", entryIds = listOf(entryId.toString()))
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
                    embedding = null,
                ),
            )
        every { knowledgeRepository.findBySourceEntryId(tenantId, publicId, entryId.toString()) } returns null
        every { knowledgeRepository.findByContentHash(any(), any(), any()) } returns null
        every { embeddingPort.embed(any()) } returns freshEmbedding
        every { knowledgeRepository.save(any()) } answers { firstArg() }

        val result = service.promoteToPublic("fb-5", "U1", tenantId)

        assertEquals(KnowledgePromotionService.PromoteResult.PROMOTED, result)
        verify { embeddingPort.embed(any()) }
        verify {
            knowledgeRepository.save(
                match {
                    it.projectId == publicId && it.embedding?.toArray()?.all { v -> v == 0.7f } == true
                },
            )
        }
    }

    @Test
    fun `should skip entry when embedding fails`() {
        val tenantId = UUID.randomUUID()
        val privateId = UUID.randomUUID()
        val publicId = UUID.randomUUID()
        val entryId = UUID.randomUUID()

        every { feedbackMappingRepository.findById("fb-6") } returns
            FeedbackMapping(id = "fb-6", channel = "C1", messageTs = "1.0", entryIds = listOf(entryId.toString()))
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
                ),
            )
        every { knowledgeRepository.findBySourceEntryId(tenantId, publicId, entryId.toString()) } returns null
        every { knowledgeRepository.findByContentHash(any(), any(), any()) } returns null
        every { embeddingPort.embed(any()) } throws RuntimeException("ollama down")

        val result = service.promoteToPublic("fb-6", "U1", tenantId)

        assertEquals(KnowledgePromotionService.PromoteResult.NOTHING_TO_PROMOTE, result)
        verify(exactly = 0) { knowledgeRepository.save(any()) }
    }

    @Test
    fun `should keep real source embedding without re-embedding`() {
        val tenantId = UUID.randomUUID()
        val privateId = UUID.randomUUID()
        val publicId = UUID.randomUUID()
        val entryId = UUID.randomUUID()
        val sourceEmbedding = PGvector(FloatArray(768) { 0.5f })

        every { feedbackMappingRepository.findById("fb-7") } returns
            FeedbackMapping(id = "fb-7", channel = "C1", messageTs = "1.0", entryIds = listOf(entryId.toString()))
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
                    embedding = sourceEmbedding,
                    contentHash = "h7",
                ),
            )
        every { knowledgeRepository.findBySourceEntryId(tenantId, publicId, entryId.toString()) } returns null
        every { knowledgeRepository.findByContentHash(tenantId, publicId, "h7") } returns null
        every { knowledgeRepository.save(any()) } answers { firstArg() }

        val result = service.promoteToPublic("fb-7", "U1", tenantId)

        assertEquals(KnowledgePromotionService.PromoteResult.PROMOTED, result)
        verify(exactly = 0) { embeddingPort.embed(any()) }
        verify {
            knowledgeRepository.save(
                match {
                    assertNotNull(it.embedding)
                    it.embedding === sourceEmbedding
                },
            )
        }
    }
}

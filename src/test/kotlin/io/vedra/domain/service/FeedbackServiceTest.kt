package io.vedra.domain.service

import com.pgvector.PGvector
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.domain.repository.feedback.FeedbackMappingRepository
import io.vedra.domain.repository.knowledge.KnowledgeRepository
import io.vedra.infrastructure.database.feedback.FeedbackMapping
import io.vedra.infrastructure.database.knowledge.AnswerSource
import io.vedra.infrastructure.database.knowledge.KnowledgeEntry
import io.vedra.infrastructure.database.knowledge.KnowledgeSourceType
import java.util.Optional
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedbackServiceTest {
    private val knowledgeRepository: KnowledgeRepository = mockk(relaxed = true)
    private val feedbackMappingRepository: FeedbackMappingRepository = mockk(relaxed = true)
    private val service = FeedbackService(knowledgeRepository, feedbackMappingRepository)

    private val testEmbedding = PGvector(FloatArray(768) { 0.1f })

    init {
        every { knowledgeRepository.save<KnowledgeEntry>(any()) } answers { firstArg() }
        every { feedbackMappingRepository.save<FeedbackMapping>(any()) } answers { firstArg() }
    }

    @Test
    fun `positive feedback should increase quality score and accept AI entry`() {
        val entryId = UUID.randomUUID()
        val entry = aiEntry(entryId, accepted = false, qualityScore = 0.5f)
        val mapping = FeedbackMapping(threadTs = "123.0", entryIds = listOf(entryId), answerSource = "AI_WITH_CONTEXT")

        every { feedbackMappingRepository.findById("123.0") } returns Optional.of(mapping)
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        service.applyPositiveFeedback("123.0")

        assertEquals(0.6f, entry.qualityScore)
        assertTrue(entry.accepted)
        verify { knowledgeRepository.save(entry) }
    }

    @Test
    fun `positive feedback should not re-accept already accepted entry`() {
        val entryId = UUID.randomUUID()
        val entry = aiEntry(entryId, accepted = true, qualityScore = 0.8f)
        val mapping = FeedbackMapping(threadTs = "123.0", entryIds = listOf(entryId), answerSource = "AI_WITH_CONTEXT")

        every { feedbackMappingRepository.findById("123.0") } returns Optional.of(mapping)
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        service.applyPositiveFeedback("123.0")

        assertEquals(0.9f, entry.qualityScore, 0.001f)
        assertTrue(entry.accepted)
    }

    @Test
    fun `positive feedback should not exceed max quality score`() {
        val entryId = UUID.randomUUID()
        val entry = aiEntry(entryId, accepted = true, qualityScore = 0.95f)
        val mapping = FeedbackMapping(threadTs = "123.0", entryIds = listOf(entryId), answerSource = "KNOWLEDGE_BASE")

        every { feedbackMappingRepository.findById("123.0") } returns Optional.of(mapping)
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        service.applyPositiveFeedback("123.0")

        assertEquals(1.0f, entry.qualityScore)
    }

    @Test
    fun `negative feedback should decrease quality score`() {
        val entryId = UUID.randomUUID()
        val entry = aiEntry(entryId, accepted = true, qualityScore = 0.5f)
        val mapping = FeedbackMapping(threadTs = "123.0", entryIds = listOf(entryId), answerSource = "AI_WITH_CONTEXT")

        every { feedbackMappingRepository.findById("123.0") } returns Optional.of(mapping)
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        service.applyNegativeFeedback("123.0")

        assertEquals(0.3f, entry.qualityScore)
        verify { knowledgeRepository.save(entry) }
    }

    @Test
    fun `negative feedback should not go below min quality score`() {
        val entryId = UUID.randomUUID()
        val entry = aiEntry(entryId, accepted = true, qualityScore = 0.1f)
        val mapping = FeedbackMapping(threadTs = "123.0", entryIds = listOf(entryId), answerSource = "AI_WITH_CONTEXT")

        every { feedbackMappingRepository.findById("123.0") } returns Optional.of(mapping)
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        service.applyNegativeFeedback("123.0")

        assertEquals(0.0f, entry.qualityScore)
    }

    @Test
    fun `should do nothing when mapping not found`() {
        every { feedbackMappingRepository.findById("999.0") } returns Optional.empty()

        service.applyPositiveFeedback("999.0")

        verify(exactly = 0) { knowledgeRepository.findByIds(any()) }
        verify(exactly = 0) { knowledgeRepository.save(any()) }
    }

    @Test
    fun `saveMapping should do nothing when entryIds is empty`() {
        service.saveMapping("123.0", emptyList(), "AI_GENERAL")

        verify(exactly = 0) { feedbackMappingRepository.save(any()) }
    }

    @Test
    fun `saveMapping should save mapping when entryIds is not empty`() {
        val entryId = UUID.randomUUID()

        service.saveMapping("123.0", listOf(entryId), "AI_WITH_CONTEXT")

        verify {
            feedbackMappingRepository.save(
                match {
                    it.threadTs == "123.0" && it.entryIds == listOf(entryId) && it.answerSource == "AI_WITH_CONTEXT"
                },
            )
        }
    }

    @Test
    fun `positive feedback should accept AI generated entries`() {
        val entryId = UUID.randomUUID()
        val entry = aiEntry(entryId, accepted = false, qualityScore = 0.5f)
        val mapping = FeedbackMapping(threadTs = "123.0", entryIds = listOf(entryId), answerSource = "AI_GENERAL")

        every { feedbackMappingRepository.findById("123.0") } returns Optional.of(mapping)
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        service.applyPositiveFeedback("123.0")

        assertTrue(entry.accepted)
        assertEquals(AnswerSource.AI_GENERATED, entry.answerSource)
    }

    private fun aiEntry(
        id: UUID,
        accepted: Boolean,
        qualityScore: Float,
    ) = KnowledgeEntry(
        id = id,
        tenantId = UUID.randomUUID(),
        source = KnowledgeSourceType.FAQ,
        content = "Test",
        answer = "Answer",
        embedding = testEmbedding,
        answerSource = AnswerSource.AI_GENERATED,
        qualityScore = qualityScore,
        accepted = accepted,
    )
}

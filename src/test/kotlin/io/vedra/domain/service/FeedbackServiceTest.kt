package io.vedra.domain.service

import com.pgvector.PGvector
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.domain.repository.feedback.FeedbackMappingRepository
import io.vedra.domain.repository.knowledge.KnowledgeRepository
import io.vedra.infrastructure.database.feedback.FeedbackMapping
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
    fun `positive vote should increase quality score`() {
        val entryId = UUID.randomUUID()
        val entry = knowledgeEntry(entryId, qualityScore = 0.5f)
        val mapping = FeedbackMapping(id = "fb-1", channel = "C123", messageTs = "222.0", entryIds = listOf(entryId.toString()))

        every { feedbackMappingRepository.findById("fb-1") } returns Optional.of(mapping)
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        val result = service.applyVote("fb-1", "U001", "positive")

        assertEquals(FeedbackService.VoteResult.NEW, result)
        assertTrue(entry.qualityScore > 0.5f)
        verify { knowledgeRepository.save(entry) }
    }

    @Test
    fun `negative vote should decrease quality score`() {
        val entryId = UUID.randomUUID()
        val entry = knowledgeEntry(entryId, qualityScore = 0.5f)
        val mapping = FeedbackMapping(id = "fb-1", channel = "C123", messageTs = "222.0", entryIds = listOf(entryId.toString()))

        every { feedbackMappingRepository.findById("fb-1") } returns Optional.of(mapping)
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        val result = service.applyVote("fb-1", "U001", "negative")

        assertEquals(FeedbackService.VoteResult.NEW, result)
        assertTrue(entry.qualityScore < 0.5f)
        verify { knowledgeRepository.save(entry) }
    }

    @Test
    fun `already voted user should return NO_CHANGE`() {
        val entryId = UUID.randomUUID()
        val mapping =
            FeedbackMapping(
                id = "fb-1",
                channel = "C123",
                messageTs = "222.0",
                entryIds = listOf(entryId.toString()),
                votes = mapOf("U001" to "positive"),
            )

        every { feedbackMappingRepository.findById("fb-1") } returns Optional.of(mapping)

        val result = service.applyVote("fb-1", "U001", "negative")

        assertEquals(FeedbackService.VoteResult.NO_CHANGE, result)
        verify(exactly = 0) { knowledgeRepository.save(any()) }
    }

    @Test
    fun `should do nothing when mapping not found`() {
        every { feedbackMappingRepository.findById("fb-999") } returns Optional.empty()

        service.applyVote("fb-999", "U001", "positive")

        verify(exactly = 0) { knowledgeRepository.findByIds(any()) }
        verify(exactly = 0) { knowledgeRepository.save(any()) }
    }

    @Test
    fun `bayesian score should approach prior with zero votes`() {
        val entryId = UUID.randomUUID()
        val entry = knowledgeEntry(entryId, qualityScore = 0.5f)
        val mapping = FeedbackMapping(id = "fb-1", channel = "C123", messageTs = "222.0", entryIds = listOf(entryId.toString()))

        every { feedbackMappingRepository.findById("fb-1") } returns Optional.of(mapping)
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        service.applyVote("fb-1", "U001", "positive")

        // 1 positive out of 1: (10*0.5 + 1) / (10 + 1) = 6/11 ≈ 0.545
        assertEquals(0.545f, entry.qualityScore, 0.01f)
    }

    @Test
    fun `bayesian score should approach 1 with many positive votes`() {
        val entryId = UUID.randomUUID()
        val entry = knowledgeEntry(entryId, qualityScore = 0.5f)
        val votes = (1..50).associate { "U$it" to "positive" }
        val mapping =
            FeedbackMapping(
                id = "fb-1",
                channel = "C123",
                messageTs = "222.0",
                entryIds = listOf(entryId.toString()),
                votes = votes,
            )

        every { feedbackMappingRepository.findById("fb-1") } returns Optional.of(mapping)
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        service.applyVote("fb-1", "U51", "positive")

        // 51 positive out of 51: (10*0.5 + 51) / (10 + 51) = 56/61 ≈ 0.918
        assertEquals(0.918f, entry.qualityScore, 0.01f)
    }

    @Test
    fun `bayesian score with mixed votes`() {
        val entryId = UUID.randomUUID()
        val entry = knowledgeEntry(entryId, qualityScore = 0.5f)
        val votes = (1..20).associate { "U$it" to if (it <= 15) "positive" else "negative" }
        val mapping =
            FeedbackMapping(
                id = "fb-1",
                channel = "C123",
                messageTs = "222.0",
                entryIds = listOf(entryId.toString()),
                votes = votes,
            )

        every { feedbackMappingRepository.findById("fb-1") } returns Optional.of(mapping)
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        service.applyVote("fb-1", "U21", "positive")

        // 16 positive, 5 negative, 21 total
        // netVotes = 16 - 5 = 11
        // (10*0.5 + 11) / (10 + 21) = 16/31 ≈ 0.516
        assertEquals(0.516f, entry.qualityScore, 0.01f)
    }

    @Test
    fun `saveMapping should save mapping with generated id`() {
        val entryId = UUID.randomUUID()

        service.saveMapping("fb-1", "C123", "222.0", listOf(entryId))

        verify {
            feedbackMappingRepository.save(
                match {
                    it.id == "fb-1" && it.channel == "C123" && it.messageTs == "222.0"
                },
            )
        }
    }

    @Test
    fun `saveMapping should do nothing when entryIds is empty`() {
        service.saveMapping("fb-1", "C123", "222.0", emptyList())

        verify(exactly = 0) { feedbackMappingRepository.save(any()) }
    }

    private fun knowledgeEntry(
        id: UUID,
        qualityScore: Float,
    ) = KnowledgeEntry(
        id = id,
        tenantId = UUID.randomUUID(),
        source = KnowledgeSourceType.FAQ,
        content = "Test",
        answer = "Answer",
        embedding = testEmbedding,
        qualityScore = qualityScore,
    )
}

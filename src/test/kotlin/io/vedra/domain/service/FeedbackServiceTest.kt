package io.vedra.domain.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.domain.model.AnswerSource
import io.vedra.domain.model.FeedbackMapping
import io.vedra.domain.model.KnowledgeEntry
import io.vedra.domain.model.KnowledgeSourceType
import io.vedra.domain.repository.feedback.FeedbackMappingRepository
import io.vedra.domain.repository.knowledge.KnowledgeRepository
import io.vedra.infrastructure.config.HybridScoringProperties
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedbackServiceTest {
    private val knowledgeRepository: KnowledgeRepository = mockk(relaxed = true)
    private val feedbackMappingRepository: FeedbackMappingRepository = mockk(relaxed = true)
    private val llmCacheService: LlmCacheService = mockk(relaxed = true)
    private val service =
        FeedbackService(
            knowledgeRepository,
            feedbackMappingRepository,
            llmCacheService,
            HybridScoringService(),
            HybridScoringProperties(),
        )

    init {
        every { knowledgeRepository.save(any()) } answers { firstArg() }
        every { feedbackMappingRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `positive vote should increase quality score`() {
        val entryId = UUID.randomUUID()
        val entry = knowledgeEntry(entryId, qualityScore = 0.5f)
        val mapping = FeedbackMapping(id = "fb-1", channel = "C123", messageTs = "222.0", entryIds = listOf(entryId.toString()))

        every { feedbackMappingRepository.findById("fb-1") } returns mapping
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        val result = service.applyVote("fb-1", "U001", "positive")

        assertEquals(FeedbackService.VoteResult.NEW, result)
        verify { knowledgeRepository.save(match { it.qualityScore > 0.5f }) }
    }

    @Test
    fun `negative vote should decrease quality score`() {
        val entryId = UUID.randomUUID()
        val entry = knowledgeEntry(entryId, qualityScore = 0.5f)
        val mapping = FeedbackMapping(id = "fb-1", channel = "C123", messageTs = "222.0", entryIds = listOf(entryId.toString()))

        every { feedbackMappingRepository.findById("fb-1") } returns mapping
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        val result = service.applyVote("fb-1", "U001", "negative")

        assertEquals(FeedbackService.VoteResult.NEW, result)
        verify { knowledgeRepository.save(match { it.qualityScore < 0.5f }) }
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

        every { feedbackMappingRepository.findById("fb-1") } returns mapping

        val result = service.applyVote("fb-1", "U001", "negative")

        assertEquals(FeedbackService.VoteResult.NO_CHANGE, result)
        verify(exactly = 0) { knowledgeRepository.save(any()) }
    }

    @Test
    fun `should do nothing when mapping not found`() {
        every { feedbackMappingRepository.findById("fb-999") } returns null

        service.applyVote("fb-999", "U001", "positive")

        verify(exactly = 0) { knowledgeRepository.findByIds(any()) }
        verify(exactly = 0) { knowledgeRepository.save(any()) }
    }

    @Test
    fun `bayesian score should approach prior with zero votes`() {
        val entryId = UUID.randomUUID()
        val entry = knowledgeEntry(entryId, qualityScore = 0.5f)
        val mapping = FeedbackMapping(id = "fb-1", channel = "C123", messageTs = "222.0", entryIds = listOf(entryId.toString()))

        every { feedbackMappingRepository.findById("fb-1") } returns mapping
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        service.applyVote("fb-1", "U001", "positive")

        verify { knowledgeRepository.save(match { kotlin.math.abs(it.qualityScore - 0.545f) < 0.01f }) }
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

        every { feedbackMappingRepository.findById("fb-1") } returns mapping
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        service.applyVote("fb-1", "U51", "positive")

        verify { knowledgeRepository.save(match { kotlin.math.abs(it.qualityScore - 0.918f) < 0.01f }) }
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

        every { feedbackMappingRepository.findById("fb-1") } returns mapping
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        service.applyVote("fb-1", "U21", "positive")

        verify { knowledgeRepository.save(match { kotlin.math.abs(it.qualityScore - 0.516f) < 0.01f }) }
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

    @Test
    fun `single positive vote should raise cached confidence to hybrid score without flipping accepted`() {
        val entryId = UUID.randomUUID()
        val entry = knowledgeEntry(entryId, qualityScore = 0.5f, answerSource = AnswerSource.AI_GENERATED, accepted = false)
        val mapping = FeedbackMapping(id = "fb-1", channel = "C123", messageTs = "222.0", entryIds = listOf(entryId.toString()))

        every { feedbackMappingRepository.findById("fb-1") } returns mapping
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        service.applyVote("fb-1", "U001", "positive")

        verify { knowledgeRepository.save(match { !it.accepted }) }
        verify {
            llmCacheService.refreshConfidenceForEntries(
                listOf(entryId),
                match { kotlin.math.abs(it - 0.851) < 0.01 },
                LlmCacheService.RefreshMode.RAISE_ONLY,
            )
        }
    }

    @Test
    fun `many positive votes should promote AI entry to accepted`() {
        val entryId = UUID.randomUUID()
        val entry = knowledgeEntry(entryId, qualityScore = 0.5f, answerSource = AnswerSource.AI_GENERATED, accepted = false)
        val votes = (1..15).associate { "U$it" to "positive" }
        val mapping =
            FeedbackMapping(
                id = "fb-1",
                channel = "C123",
                messageTs = "222.0",
                entryIds = listOf(entryId.toString()),
                votes = votes,
            )

        every { feedbackMappingRepository.findById("fb-1") } returns mapping
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        service.applyVote("fb-1", "U16", "positive")

        verify { knowledgeRepository.save(match { it.accepted && it.qualityScore > 0.8f }) }
    }

    @Test
    fun `many negative votes should demote AI entry to unaccepted`() {
        val entryId = UUID.randomUUID()
        val entry = knowledgeEntry(entryId, qualityScore = 0.5f, answerSource = AnswerSource.AI_GENERATED, accepted = true)
        val votes = (1..10).associate { "U$it" to "negative" }
        val mapping =
            FeedbackMapping(
                id = "fb-1",
                channel = "C123",
                messageTs = "222.0",
                entryIds = listOf(entryId.toString()),
                votes = votes,
            )

        every { feedbackMappingRepository.findById("fb-1") } returns mapping
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        service.applyVote("fb-1", "U11", "negative")

        verify { knowledgeRepository.save(match { !it.accepted && it.qualityScore < 0.3f }) }
    }

    @Test
    fun `vote should not change accepted flag of user verified entry`() {
        val entryId = UUID.randomUUID()
        val entry = knowledgeEntry(entryId, qualityScore = 0.5f, answerSource = AnswerSource.USER_VERIFIED, accepted = true)
        val mapping = FeedbackMapping(id = "fb-1", channel = "C123", messageTs = "222.0", entryIds = listOf(entryId.toString()))

        every { feedbackMappingRepository.findById("fb-1") } returns mapping
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns listOf(entry)

        service.applyVote("fb-1", "U001", "negative")

        verify { knowledgeRepository.save(match { it.accepted }) }
    }

    private fun knowledgeEntry(
        id: UUID,
        qualityScore: Float,
        answerSource: AnswerSource = AnswerSource.USER_VERIFIED,
        accepted: Boolean = true,
    ) = KnowledgeEntry(
        id = id,
        tenantId = UUID.randomUUID(),
        source = KnowledgeSourceType.FAQ,
        content = "Test",
        answer = "Answer",
        qualityScore = qualityScore,
        answerSource = answerSource,
        accepted = accepted,
    )
}

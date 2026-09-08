package io.vedra.domain.service

import com.pgvector.PGvector
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.domain.model.AnswerSource
import io.vedra.domain.model.KnowledgeEntry
import io.vedra.domain.model.KnowledgeSourceType
import io.vedra.domain.model.ResponseSource
import io.vedra.domain.repository.knowledge.KnowledgeRepository
import io.vedra.domain.repository.knowledge.SimilarityResultProjection
import io.vedra.infrastructure.chat.LLMPort
import io.vedra.infrastructure.embedding.EmbeddingPort
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class KnowledgeServiceTest {
    private val tenantId: UUID = UUID.randomUUID()
    private val knowledgeRepository: KnowledgeRepository = mockk(relaxed = true)
    private val embeddingPort: EmbeddingPort = mockk()
    private val llmPort: LLMPort = mockk()
    private val questionNormalizer: QuestionNormalizer = mockk(relaxed = true)
    private val answerNormalizer: AnswerNormalizer = mockk(relaxed = true)
    private val llmCacheService: LlmCacheService = mockk(relaxed = true)
    private val embeddingCacheService: EmbeddingCacheService = mockk(relaxed = true)
    private val service =
        KnowledgeService(
            knowledgeRepository,
            embeddingPort,
            llmPort,
            questionNormalizer,
            answerNormalizer,
            llmCacheService,
            embeddingCacheService,
        )

    init {
        every { embeddingPort.embed(any()) } returns PGvector(FloatArray(768) { 0.1f })
        every { knowledgeRepository.save(any()) } answers { firstArg() }
        every { knowledgeRepository.findReusableAiAnswer(any(), any(), any()) } returns null
        every { knowledgeRepository.findByContentHash(any(), any(), any()) } returns null
        every { llmCacheService.lookup(any(), any(), any()) } returns null
        every { embeddingCacheService.lookup(any()) } returns null
        every { questionNormalizer.normalize(any(), any()) } answers {
            val question = it.invocation.args[0] as String
            val project = it.invocation.args[1] as? UUID
            io.vedra.domain.model.NormalizedQuestion(
                original = question,
                normalized = question.lowercase(),
                hash = "test-hash-$question",
                projectId = project,
            )
        }
        every { answerNormalizer.normalize(any()) } answers {
            val response = it.invocation.args[0] as String
            io.vedra.domain.model.NormalizedAnswer(
                original = response,
                normalized = response,
            )
        }
    }

    @Test
    fun `should return answer directly from knowledge base when high confidence`() {
        val entryId = UUID.randomUUID()
        val entry = knowledgeEntry(entryId)

        every {
            knowledgeRepository.findSimilarWithScore(any(), any(), any(), any(), any())
        } returns
            listOf(
                SimilarityResultProjection(
                    id = entryId,
                    content = "Prawo Ohma",
                    answer = "U = I * R",
                    source = "FAQ",
                    accepted = true,
                    score = 0.92,
                ),
            )
        every { knowledgeRepository.findById(entryId) } returns entry

        val result = service.answerQuestion("Jakie jest prawo Ohma?", listOf(null), tenantId)

        assertEquals("U = I * R", result.answer)
        assertEquals(ResponseSource.KNOWLEDGE_BASE, result.source)
        assertEquals(0.92, result.confidence)
        assertEquals(listOf(entryId), result.entryIds)
        verify(exactly = 0) { llmPort.generate(any(), any()) }
    }

    @Test
    fun `should use LLM with context when medium confidence`() {
        val entryId = UUID.randomUUID()

        every {
            knowledgeRepository.findSimilarWithScore(any(), any(), any(), any(), any())
        } returns
            listOf(
                SimilarityResultProjection(
                    id = entryId,
                    content = "Izolacja fundamentów",
                    answer = "Stosuje się izolację przeciwwodną",
                    source = "FAQ",
                    accepted = true,
                    score = 0.72,
                ),
            )
        every { llmPort.generate(any(), any()) } returns "Na podstawie wiedzy: izolacja przeciwwodna typu ciężkiego."

        val result = service.answerQuestion("Jaka izolacja na fundament?", listOf(null), tenantId)

        assertEquals("Na podstawie wiedzy: izolacja przeciwwodna typu ciężkiego.", result.answer)
        assertEquals(ResponseSource.AI_WITH_CONTEXT, result.source)
        assertEquals(0.72, result.confidence)
        verify { llmPort.generate(any(), any()) }
        verify { knowledgeRepository.save(any()) }
    }

    @Test
    fun `should use LLM without context when low confidence and save answer`() {
        every {
            knowledgeRepository.findSimilarWithScore(any(), any(), any(), any(), any())
        } returns emptyList()
        every { llmPort.generate(any(), any()) } returns "Odpowiedź ogólna z LLM."

        val result = service.answerQuestion("Co to jest beton?", listOf(null), tenantId)

        assertEquals("Odpowiedź ogólna z LLM.", result.answer)
        assertEquals(ResponseSource.AI_GENERAL, result.source)
        assertEquals(0.0, result.confidence)
        verify {
            knowledgeRepository.save(
                match {
                    it.answerSource == AnswerSource.AI_GENERATED && !it.accepted && it.answer == "Odpowiedź ogólna z LLM."
                },
            )
        }
    }

    @Test
    fun `should use LLM without context when no entries found`() {
        every {
            knowledgeRepository.findSimilarWithScore(any(), any(), any(), any(), any())
        } returns emptyList()
        every { llmPort.generate(any(), any()) } returns "Brak informacji, LLM odpowiada."

        val result = service.answerQuestion("Pytanie?", listOf(null), tenantId)

        assertEquals(ResponseSource.AI_GENERAL, result.source)
    }

    @Test
    fun `should fall back to context answer when LLM says no info`() {
        val entryId = UUID.randomUUID()

        every {
            knowledgeRepository.findSimilarWithScore(any(), any(), any(), any(), any())
        } returns
            listOf(
                SimilarityResultProjection(
                    id = entryId,
                    content = "Pytanie",
                    answer = "Poprzednia odpowiedź AI",
                    source = "FAQ",
                    accepted = false,
                    score = 0.75,
                ),
            )
        every { llmPort.generate(any(), any()) } returns "Brak informacji w bazie wiedzy na ten temat."

        val result = service.answerQuestion("Pytanie?", listOf(null), tenantId)

        assertEquals("Poprzednia odpowiedź AI", result.answer)
        assertEquals(ResponseSource.AI_WITH_CONTEXT, result.source)
        verify(exactly = 0) { knowledgeRepository.save(match { it.answerSource == AnswerSource.AI_GENERATED }) }
    }

    @Test
    fun `should fall back to context answer when LLM returns null`() {
        val entryId = UUID.randomUUID()

        every {
            knowledgeRepository.findSimilarWithScore(any(), any(), any(), any(), any())
        } returns
            listOf(
                SimilarityResultProjection(
                    id = entryId,
                    content = "Pytanie",
                    answer = "Zapisana odpowiedź",
                    source = "FAQ",
                    accepted = false,
                    score = 0.80,
                ),
            )
        every { llmPort.generate(any(), any()) } returns null

        val result = service.answerQuestion("Pytanie?", listOf(null), tenantId)

        assertEquals("Zapisana odpowiedź", result.answer)
        assertEquals(ResponseSource.AI_WITH_CONTEXT, result.source)
    }

    @Test
    fun `should increment usage count for high confidence answer`() {
        val entryId = UUID.randomUUID()
        val entry = knowledgeEntry(entryId)

        every {
            knowledgeRepository.findSimilarWithScore(any(), any(), any(), any(), any())
        } returns
            listOf(
                SimilarityResultProjection(
                    id = entryId,
                    content = "Test",
                    answer = "Answer",
                    source = "FAQ",
                    accepted = true,
                    score = 0.90,
                ),
            )
        every { knowledgeRepository.findById(entryId) } returns entry

        service.answerQuestion("Test?", listOf(null), tenantId)

        verify { knowledgeRepository.save(match { it.usageCount == 1 }) }
    }

    @Test
    fun `should save AI generated entry with accepted false`() {
        val entryId = UUID.randomUUID()

        every {
            knowledgeRepository.findSimilarWithScore(any(), any(), any(), any(), any())
        } returns
            listOf(
                SimilarityResultProjection(
                    id = entryId,
                    content = "Test",
                    answer = "Partial",
                    source = "FAQ",
                    accepted = true,
                    score = 0.70,
                ),
            )
        every { llmPort.generate(any(), any()) } returns "AI answer"

        service.answerQuestion("Test?", listOf(null), tenantId)

        verify {
            knowledgeRepository.save(
                match {
                    it.answerSource == AnswerSource.AI_GENERATED && !it.accepted
                },
            )
        }
    }

    @Test
    fun `addKnowledge should save with USER_VERIFIED source`() {
        every { knowledgeRepository.save(any()) } answers { firstArg() }

        val result = service.addKnowledge("Pytanie", "Odpowiedź", null, tenantId)

        assertEquals(AnswerSource.USER_VERIFIED, result.answerSource)
        assertEquals(true, result.accepted)
        assertEquals("Pytanie", result.content)
        verify { knowledgeRepository.save(any()) }
    }

    @Test
    fun `should return fallback when LLM returns null`() {
        every {
            knowledgeRepository.findSimilarWithScore(any(), any(), any(), any(), any())
        } returns emptyList()
        every { llmPort.generate(any(), any()) } returns null

        val result = service.answerQuestion("Pytanie?", listOf(null), tenantId)

        assertEquals("Nie udało się wygenerować odpowiedzi.", result.answer)
        assertEquals(ResponseSource.AI_GENERAL, result.source)
    }

    @Test
    fun `unchecked entry with high score should use RAG not direct answer`() {
        val entryId = UUID.randomUUID()

        every {
            knowledgeRepository.findSimilarWithScore(any(), any(), any(), any(), any())
        } returns
            listOf(
                SimilarityResultProjection(
                    id = entryId,
                    content = "Pytanie podobne",
                    answer = "Niesprawdzona odpowiedź",
                    source = "FAQ",
                    accepted = false,
                    score = 0.92,
                ),
            )
        every { llmPort.generate(any(), any()) } returns "LLM odpowiedź z kontekstem."

        val result = service.answerQuestion("Pytanie?", listOf(null), tenantId)

        assertEquals(ResponseSource.AI_WITH_CONTEXT, result.source)
        assertEquals(0.92, result.confidence)
        verify { llmPort.generate(any(), any()) }
    }

    @Test
    fun `accepted and unchecked entries with high score should use accepted for direct answer`() {
        val acceptedId = UUID.randomUUID()
        val uncheckedId = UUID.randomUUID()
        val entry = knowledgeEntry(acceptedId)

        every {
            knowledgeRepository.findSimilarWithScore(any(), any(), any(), any(), any())
        } returns
            listOf(
                SimilarityResultProjection(
                    id = uncheckedId,
                    content = "Pytanie",
                    answer = "Niesprawdzona",
                    source = "FAQ",
                    accepted = false,
                    score = 0.93,
                ),
                SimilarityResultProjection(
                    id = acceptedId,
                    content = "Pytanie",
                    answer = "Sprawdzona odpowiedź",
                    source = "FAQ",
                    accepted = true,
                    score = 0.88,
                ),
            )
        every { knowledgeRepository.findById(acceptedId) } returns entry

        val result = service.answerQuestion("Pytanie?", listOf(null), tenantId)

        assertEquals(ResponseSource.KNOWLEDGE_BASE, result.source)
        assertEquals("Sprawdzona odpowiedź", result.answer)
        verify(exactly = 0) { llmPort.generate(any(), any()) }
    }

    @Test
    fun `should pass real tenantId to similarity search`() {
        every {
            knowledgeRepository.findSimilarWithScore(any(), any(), any(), any(), any())
        } returns emptyList()
        every { llmPort.generate(any(), any()) } returns "Odp."

        service.answerQuestion("Pytanie?", listOf(null), tenantId)

        verify { knowledgeRepository.findSimilarWithScore(tenantId, any(), any(), any(), any()) }
        verify { llmCacheService.lookup(tenantId, any(), any()) }
    }

    @Test
    fun `should return cached confidence when no entry was user verified`() {
        val entryId = UUID.randomUUID()
        val cached =
            io.vedra.domain.model.LlmResponseCacheEntry(
                tenantId = tenantId,
                questionHash = "h",
                normalizedQuestion = "q",
                originalQuestion = "q",
                answer = "Cached",
                modelVersion = "m",
                confidence = 0.8,
                responseSource = ResponseSource.AI_WITH_CONTEXT,
                contextEntryIds = listOf(entryId),
                expiresAt =
                    java.time.Instant
                        .now()
                        .plusSeconds(60),
            )
        every { llmCacheService.lookup(any(), any(), any()) } returns cached
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns
            listOf(knowledgeEntry(entryId).copy(answerSource = AnswerSource.USER_VERIFIED, accepted = true))

        val result = service.answerQuestion("Q?", listOf(null), tenantId)

        assertEquals("Cached", result.answer)
        assertEquals(0.8, result.confidence)
        assertEquals(ResponseSource.AI_WITH_CONTEXT, result.source)
    }

    @Test
    fun `should return verified confidence when cached entry was accepted by vote`() {
        val entryId = UUID.randomUUID()
        val cached =
            io.vedra.domain.model.LlmResponseCacheEntry(
                tenantId = tenantId,
                questionHash = "h",
                normalizedQuestion = "q",
                originalQuestion = "q",
                answer = "Cached",
                modelVersion = "m",
                confidence = 0.0,
                responseSource = ResponseSource.AI_GENERAL,
                contextEntryIds = listOf(entryId),
                expiresAt =
                    java.time.Instant
                        .now()
                        .plusSeconds(60),
            )
        every { llmCacheService.lookup(any(), any(), any()) } returns cached
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns
            listOf(knowledgeEntry(entryId).copy(answerSource = AnswerSource.AI_GENERATED, accepted = true))

        val result = service.answerQuestion("Q?", listOf(null), tenantId)

        assertEquals(1.0, result.confidence)
        assertEquals(ResponseSource.KNOWLEDGE_BASE, result.source)
    }

    @Test
    fun `should keep zero confidence for cached answer without user verification`() {
        val entryId = UUID.randomUUID()
        val cached =
            io.vedra.domain.model.LlmResponseCacheEntry(
                tenantId = tenantId,
                questionHash = "h",
                normalizedQuestion = "q",
                originalQuestion = "q",
                answer = "Cached",
                modelVersion = "m",
                confidence = 0.0,
                responseSource = ResponseSource.AI_GENERAL,
                contextEntryIds = listOf(entryId),
                expiresAt =
                    java.time.Instant
                        .now()
                        .plusSeconds(60),
            )
        every { llmCacheService.lookup(any(), any(), any()) } returns cached
        every { knowledgeRepository.findByIds(listOf(entryId)) } returns
            listOf(knowledgeEntry(entryId).copy(answerSource = AnswerSource.AI_GENERATED, accepted = false))

        val result = service.answerQuestion("Q?", listOf(null), tenantId)

        assertEquals(0.0, result.confidence)
        assertEquals(ResponseSource.AI_GENERAL, result.source)
    }

    private fun knowledgeEntry(id: UUID) =
        KnowledgeEntry(
            id = id,
            tenantId = tenantId,
            source = KnowledgeSourceType.FAQ,
            content = "Test",
            answer = "Answer",
        )
}

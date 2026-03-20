package io.vedra.infrastructure.slack.service.outbox

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.domain.model.AnswerResult
import io.vedra.domain.model.ResponseSource
import io.vedra.domain.service.FeedbackService
import io.vedra.domain.service.KnowledgeService
import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.slack.service.SlackMessageService
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlackMessageAnswerHandlerTest {
    private val knowledgeService: KnowledgeService = mockk()
    private val slackMessageService: SlackMessageService = mockk(relaxed = true)
    private val feedbackService: FeedbackService = mockk(relaxed = true)
    private val handler = SlackMessageAnswerHandler(knowledgeService, slackMessageService, feedbackService)

    @Test
    fun `should support SLACK_MESSAGE_ANSWER event type`() {
        assertTrue(handler.supports("SLACK_MESSAGE_ANSWER"))
    }

    @Test
    fun `should not support other event types`() {
        assertFalse(handler.supports("OTHER_EVENT"))
        assertFalse(handler.supports("EMAIL_EVENT"))
        assertFalse(handler.supports(""))
    }

    @Test
    fun `should handle event by calling knowledgeService and sending formatted message`() {
        val entryId = UUID.randomUUID()
        val event =
            OutboxEvent(
                eventType = "SLACK_MESSAGE_ANSWER",
                payload =
                    mapOf(
                        "channel" to "C123",
                        "threadTs" to "1234567890.123456",
                        "question" to "Jakie jest prawo Ohma?",
                    ),
            )

        every {
            knowledgeService.answerQuestion("Jakie jest prawo Ohma?")
        } returns
            AnswerResult(
                answer = "U = I * R",
                source = ResponseSource.KNOWLEDGE_BASE,
                confidence = 0.92,
                entryIds = listOf(entryId),
            )

        handler.handle(event)

        verify { knowledgeService.answerQuestion("Jakie jest prawo Ohma?") }
        verify {
            slackMessageService.sendThreadMessage(
                "C123",
                "1234567890.123456",
                "U = I * R\n\n_Odpowiedź z bazy wiedzy._",
            )
        }
        verify {
            feedbackService.saveMapping(
                threadTs = "1234567890.123456",
                entryIds = listOf(entryId),
                answerSource = "KNOWLEDGE_BASE",
            )
        }
    }

    @Test
    fun `should format AI answer with context label`() {
        val event =
            OutboxEvent(
                eventType = "SLACK_MESSAGE_ANSWER",
                payload =
                    mapOf(
                        "channel" to "C123",
                        "threadTs" to "1234567890.123456",
                        "question" to "Question?",
                    ),
            )

        every {
            knowledgeService.answerQuestion("Question?")
        } returns
            AnswerResult(
                answer = "AI answer",
                source = ResponseSource.AI_WITH_CONTEXT,
                confidence = 0.72,
            )

        handler.handle(event)

        verify {
            slackMessageService.sendThreadMessage(
                "C123",
                "1234567890.123456",
                "AI answer\n\n_Wygenerowane przez AI na podstawie bazy wiedzy._",
            )
        }
    }

    @Test
    fun `should format general AI answer with no knowledge label`() {
        val event =
            OutboxEvent(
                eventType = "SLACK_MESSAGE_ANSWER",
                payload =
                    mapOf(
                        "channel" to "C123",
                        "threadTs" to "1234567890.123456",
                        "question" to "Question?",
                    ),
            )

        every {
            knowledgeService.answerQuestion("Question?")
        } returns
            AnswerResult(
                answer = "General AI answer",
                source = ResponseSource.AI_GENERAL,
                confidence = 0.0,
            )

        handler.handle(event)

        verify {
            slackMessageService.sendThreadMessage(
                "C123",
                "1234567890.123456",
                "General AI answer\n\n_Wygenerowane przez AI. Brak informacji w bazie wiedzy._",
            )
        }
    }

    @Test
    fun `should not send message when channel is missing`() {
        val event =
            OutboxEvent(
                eventType = "SLACK_MESSAGE_ANSWER",
                payload =
                    mapOf(
                        "threadTs" to "1234567890.123456",
                        "question" to "Question?",
                    ),
            )

        handler.handle(event)

        verify(exactly = 0) { knowledgeService.answerQuestion(any()) }
        verify(exactly = 0) { slackMessageService.sendThreadMessage(any(), any(), any()) }
    }

    @Test
    fun `should not send message when threadTs is missing`() {
        val event =
            OutboxEvent(
                eventType = "SLACK_MESSAGE_ANSWER",
                payload =
                    mapOf(
                        "channel" to "C123",
                        "question" to "Question?",
                    ),
            )

        handler.handle(event)

        verify(exactly = 0) { knowledgeService.answerQuestion(any()) }
        verify(exactly = 0) { slackMessageService.sendThreadMessage(any(), any(), any()) }
    }

    @Test
    fun `should not send message when question is missing`() {
        val event =
            OutboxEvent(
                eventType = "SLACK_MESSAGE_ANSWER",
                payload =
                    mapOf(
                        "channel" to "C123",
                        "threadTs" to "1234567890.123456",
                    ),
            )

        handler.handle(event)

        verify(exactly = 0) { knowledgeService.answerQuestion(any()) }
        verify(exactly = 0) { slackMessageService.sendThreadMessage(any(), any(), any()) }
    }

    @Test
    fun `should not send message when payload values have wrong type`() {
        val event =
            OutboxEvent(
                eventType = "SLACK_MESSAGE_ANSWER",
                payload =
                    mapOf(
                        "channel" to 123,
                        "threadTs" to 456,
                        "question" to 789,
                    ),
            )

        handler.handle(event)

        verify(exactly = 0) { knowledgeService.answerQuestion(any()) }
        verify(exactly = 0) { slackMessageService.sendThreadMessage(any(), any(), any()) }
    }
}

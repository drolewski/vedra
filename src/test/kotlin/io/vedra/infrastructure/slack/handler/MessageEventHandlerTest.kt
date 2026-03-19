package io.vedra.infrastructure.slack.handler

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.domain.service.KnowledgeService
import io.vedra.infrastructure.slack.SlackMessageService
import io.vedra.infrastructure.slack.model.SlackEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageEventHandlerTest {
    private val slackMessageService: SlackMessageService = mockk(relaxed = true)
    private val knowledgeService: KnowledgeService = mockk()
    private val handler = MessageEventHandler(slackMessageService, knowledgeService)

    @Test
    fun `should support message event type`() {
        val event =
            SlackEvent(
                type = "message",
                subtype = null,
                botId = null,
                text = "hello",
                channel = "C456",
                threadTs = "1234567890.123456",
            )

        assertTrue(handler.supports(event))
    }

    @Test
    fun `should not support non-message event type`() {
        val event =
            SlackEvent(
                type = "reaction_added",
                subtype = null,
                botId = null,
                text = "hello",
                channel = "C456",
                threadTs = "1234567890.123456",
            )

        assertFalse(handler.supports(event))
    }

    @Test
    fun `should send knowledge answer as thread message`() {
        val event =
            SlackEvent(
                type = "message",
                subtype = null,
                botId = null,
                text = "Jakie jest prawo Ohma?",
                channel = "C456",
                threadTs = "1234567890.123456",
            )

        every { knowledgeService.answerQuestion("Jakie jest prawo Ohma?") } returns "Prawo Ohma: U = I * R"

        handler.handle(event)

        verify { knowledgeService.answerQuestion("Jakie jest prawo Ohma?") }
        verify { slackMessageService.sendThreadMessage("C456", "1234567890.123456", "Prawo Ohma: U = I * R") }
    }

    @Test
    fun `should not send message when channel is null`() {
        val event =
            SlackEvent(
                type = "message",
                subtype = null,
                botId = null,
                text = "question",
                channel = null,
                threadTs = "1234567890.123456",
            )

        handler.handle(event)

        verify(exactly = 0) { knowledgeService.answerQuestion(any()) }
        verify(exactly = 0) { slackMessageService.sendThreadMessage(any(), any(), any()) }
    }

    @Test
    fun `should not send message when threadTs is null`() {
        val event =
            SlackEvent(
                type = "message",
                subtype = null,
                botId = null,
                text = "question",
                channel = "C456",
                threadTs = null,
            )

        handler.handle(event)

        verify(exactly = 0) { knowledgeService.answerQuestion(any()) }
        verify(exactly = 0) { slackMessageService.sendThreadMessage(any(), any(), any()) }
    }

    @Test
    fun `should not send message when text is null`() {
        val event =
            SlackEvent(
                type = "message",
                subtype = null,
                botId = null,
                text = null,
                channel = "C456",
                threadTs = "1234567890.123456",
            )

        handler.handle(event)

        verify(exactly = 0) { knowledgeService.answerQuestion(any()) }
        verify(exactly = 0) { slackMessageService.sendThreadMessage(any(), any(), any()) }
    }
}

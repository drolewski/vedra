package io.vedra.infrastructure.slack.handler

import io.mockk.mockk
import io.mockk.verify
import io.vedra.infrastructure.slack.SlackMessageService
import io.vedra.infrastructure.slack.model.SlackEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageEventHandlerTest {
    private val slackMessageService: SlackMessageService = mockk(relaxed = true)
    private val handler = MessageEventHandler(slackMessageService)

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
    fun `should send thread message with palindrome format`() {
        val event =
            SlackEvent(
                type = "message",
                subtype = null,
                botId = null,
                text = "kajak",
                channel = "C456",
                threadTs = "1234567890.123456",
            )

        handler.handle(event)

        verify { slackMessageService.sendThreadMessage("C456", "1234567890.123456", "Palindrom:\n```kajak```") }
    }

    @Test
    fun `should not send message when channel is null`() {
        val event =
            SlackEvent(
                type = "message",
                subtype = null,
                botId = null,
                text = "kajak",
                channel = null,
                threadTs = "1234567890.123456",
            )

        handler.handle(event)

        verify(exactly = 0) { slackMessageService.sendThreadMessage(any(), any(), any()) }
    }

    @Test
    fun `should not send message when threadTs is null`() {
        val event =
            SlackEvent(
                type = "message",
                subtype = null,
                botId = null,
                text = "kajak",
                channel = "C456",
                threadTs = null,
            )

        handler.handle(event)

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

        verify(exactly = 0) { slackMessageService.sendThreadMessage(any(), any(), any()) }
    }
}

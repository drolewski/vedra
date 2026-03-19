package io.vedra.infrastructure.slack.handler

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.domain.service.OutboxService
import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.slack.model.SlackEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageEventHandlerTest {
    private val outboxService: OutboxService = mockk(relaxed = true)
    private val handler = MessageEventHandler(outboxService)

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
    fun `should save event to outbox`() {
        val event =
            SlackEvent(
                type = "message",
                subtype = null,
                botId = null,
                text = "Jakie jest prawo Ohma?",
                channel = "C456",
                threadTs = "1234567890.123456",
            )

        every {
            outboxService.saveEvent(
                eventType = "SLACK_MESSAGE_ANSWER",
                aggregateId = "1234567890.123456",
                payload = any(),
            )
        } returns OutboxEvent(eventType = "SLACK_MESSAGE_ANSWER", payload = emptyMap())

        handler.handle(event)

        verify {
            outboxService.saveEvent(
                eventType = "SLACK_MESSAGE_ANSWER",
                aggregateId = "1234567890.123456",
                payload =
                    match {
                        it["channel"] == "C456" &&
                            it["threadTs"] == "1234567890.123456" &&
                            it["question"] == "Jakie jest prawo Ohma?"
                    },
            )
        }
    }

    @Test
    fun `should not save event when channel is null`() {
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

        verify(exactly = 0) { outboxService.saveEvent(any(), any(), any()) }
    }

    @Test
    fun `should not save event when threadTs is null`() {
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

        verify(exactly = 0) { outboxService.saveEvent(any(), any(), any()) }
    }

    @Test
    fun `should not save event when text is null`() {
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

        verify(exactly = 0) { outboxService.saveEvent(any(), any(), any()) }
    }
}

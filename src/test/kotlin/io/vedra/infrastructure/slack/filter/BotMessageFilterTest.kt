package io.vedra.infrastructure.slack.filter

import io.vedra.infrastructure.slack.model.SlackEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BotMessageFilterTest {
    private val filter = BotMessageFilter()

    @Test
    fun `should allow processing user message`() {
        val event =
            SlackEvent(
                eventId = null,
                type = "message",
                subtype = null,
                botId = null,
                text = "hello",
                channel = "C456",
                threadTs = "1234567890.123456",
            )

        assertTrue(filter.shouldProcess(event))
    }

    @Test
    fun `should reject bot message with subtype`() {
        val event =
            SlackEvent(
                eventId = null,
                type = "message",
                subtype = "bot_message",
                botId = null,
                text = "hello",
                channel = "C456",
                threadTs = "1234567890.123456",
            )

        assertFalse(filter.shouldProcess(event))
    }

    @Test
    fun `should reject bot message with botId`() {
        val event =
            SlackEvent(
                eventId = null,
                type = "message",
                subtype = null,
                botId = "B123",
                text = "hello",
                channel = "C456",
                threadTs = "1234567890.123456",
            )

        assertFalse(filter.shouldProcess(event))
    }

    @Test
    fun `should reject bot message with both subtype and botId`() {
        val event =
            SlackEvent(
                eventId = null,
                type = "message",
                subtype = "bot_message",
                botId = "B123",
                text = "hello",
                channel = "C456",
                threadTs = "1234567890.123456",
            )

        assertFalse(filter.shouldProcess(event))
    }
}

package io.vedra.infrastructure.slack.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlackEventTest {
    @Test
    fun `should create event from raw payload`() {
        val payload: Map<String, Any> =
            mapOf(
                "type" to "message",
                "subtype" to "bot_message",
                "bot_id" to "B123",
                "text" to "hello",
                "channel" to "C456",
                "thread_ts" to "1234567890.000000",
                "ts" to "1234567890.123456",
            )

        val event = SlackEvent.fromRawPayload(payload, "Ev123")

        assertEquals("Ev123", event.eventId)
        assertEquals("message", event.type)
        assertEquals("bot_message", event.subtype)
        assertEquals("B123", event.botId)
        assertEquals("hello", event.text)
        assertEquals("C456", event.channel)
        assertEquals("1234567890.000000", event.threadTs)
    }

    @Test
    fun `should fallback to ts when thread_ts is missing`() {
        val payload: Map<String, Any> =
            mapOf(
                "type" to "message",
                "text" to "hello",
                "channel" to "C456",
                "ts" to "1234567890.123456",
            )

        val event = SlackEvent.fromRawPayload(payload)

        assertEquals("1234567890.123456", event.threadTs)
    }

    @Test
    fun `should handle missing optional fields`() {
        val payload: Map<String, Any> =
            mapOf(
                "type" to "message",
                "text" to "hello",
            )

        val event = SlackEvent.fromRawPayload(payload)

        assertEquals("message", event.type)
        assertNull(event.subtype)
        assertNull(event.botId)
        assertEquals("hello", event.text)
        assertNull(event.channel)
        assertNull(event.threadTs)
    }

    @Test
    fun `should default type to empty string when missing`() {
        val payload: Map<String, Any> =
            mapOf(
                "text" to "hello",
            )

        val event = SlackEvent.fromRawPayload(payload)

        assertEquals("", event.type)
    }

    @Test
    fun `isBotMessage should return true when subtype is bot_message`() {
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

        assertTrue(event.isBotMessage)
    }

    @Test
    fun `isBotMessage should return true when botId is present`() {
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

        assertTrue(event.isBotMessage)
    }

    @Test
    fun `isBotMessage should return false for regular user message`() {
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

        assertFalse(event.isBotMessage)
    }
}

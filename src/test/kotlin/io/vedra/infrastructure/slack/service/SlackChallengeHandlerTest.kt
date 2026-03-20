package io.vedra.infrastructure.slack.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SlackChallengeHandlerTest {
    private val handler = SlackChallengeHandler()

    @Test
    fun `should return response with challenge when present`() {
        val body = mapOf("challenge" to "abc123", "type" to "url_verification")

        val response = handler.handle(body)

        assertNotNull(response)
        assertEquals(200, response.statusCode.value())
        assertEquals(mapOf("challenge" to "abc123"), response.body)
    }

    @Test
    fun `should return null when challenge is missing`() {
        val body = mapOf("type" to "event_callback", "event" to mapOf("type" to "message"))

        val response = handler.handle(body)

        assertNull(response)
    }

    @Test
    fun `should return null when body is empty`() {
        val body = emptyMap<String, Any>()

        val response = handler.handle(body)

        assertNull(response)
    }

    @Test
    fun `should return null when challenge is not a string`() {
        val body = mapOf("challenge" to 123)

        val response = handler.handle(body)

        assertNull(response)
    }
}

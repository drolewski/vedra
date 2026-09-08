package io.vedra.infrastructure.slack.service

import io.mockk.mockk
import io.mockk.verify
import io.vedra.infrastructure.slack.model.SlackEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class SlackEventRequestHandlerTest {
    private val eventProcessor: SlackEventProcessor = mockk(relaxed = true)
    private val installationService: io.vedra.domain.service.SlackInstallationService = mockk(relaxed = true)
    private val tenantRepository: io.vedra.domain.repository.tenant.TenantRepository = mockk(relaxed = true)
    private val handler = SlackEventRequestHandler(eventProcessor, installationService, tenantRepository)

    @Test
    fun `should process event from body`() {
        val body =
            mapOf(
                "event_id" to "Ev123",
                "event" to
                    mapOf(
                        "type" to "message",
                        "text" to "hello",
                        "channel" to "C456",
                        "ts" to "1234567890.123456",
                    ),
            )

        val response = handler.handle(body)

        assertEquals(200, response.statusCode.value())
        verify {
            eventProcessor.process(
                SlackEvent(
                    eventId = "Ev123",
                    type = "message",
                    subtype = null,
                    botId = null,
                    text = "hello",
                    channel = "C456",
                    threadTs = "1234567890.123456",
                ),
            )
        }
    }

    @Test
    fun `should return ok without processing when event is missing`() {
        val body = mapOf<String, Any>()

        val response = handler.handle(body)

        assertEquals(200, response.statusCode.value())
        verify(exactly = 0) { eventProcessor.process(any()) }
    }

    @Test
    fun `should return ok without processing when event is not a map`() {
        val body = mapOf("event" to "not a map")

        val response = handler.handle(body)

        assertEquals(200, response.statusCode.value())
        verify(exactly = 0) { eventProcessor.process(any()) }
    }

    @Test
    fun `should process bot message event`() {
        val body =
            mapOf(
                "event" to
                    mapOf(
                        "type" to "message",
                        "subtype" to "bot_message",
                        "bot_id" to "B123",
                        "text" to "hello",
                        "channel" to "C456",
                        "ts" to "1234567890.123456",
                    ),
            )

        handler.handle(body)

        verify {
            eventProcessor.process(
                SlackEvent(
                    eventId = null,
                    type = "message",
                    subtype = "bot_message",
                    botId = "B123",
                    text = "hello",
                    channel = "C456",
                    threadTs = "1234567890.123456",
                ),
            )
        }
    }
}

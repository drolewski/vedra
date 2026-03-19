package io.vedra.api.controller.slack.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.mockk
import io.mockk.verify
import io.vedra.domain.model.SlackEvent
import io.vedra.domain.service.SlackEventProcessor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SlackEventsControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var eventProcessor: SlackEventProcessor
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        eventProcessor = mockk(relaxed = true)
        val controller = SlackEventsController(eventProcessor)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `should process user message event`() {
        val payload =
            mapOf(
                "event" to
                    mapOf(
                        "type" to "message",
                        "text" to "hello",
                        "channel" to "C456",
                        "ts" to "1234567890.123456",
                    ),
            )

        mockMvc
            .perform(
                post("/api/v1/slack/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload)),
            ).andExpect(status().isOk)

        verify {
            eventProcessor.process(
                SlackEvent(
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
    fun `should process bot message event and let processor decide`() {
        val payload =
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

        mockMvc
            .perform(
                post("/api/v1/slack/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload)),
            ).andExpect(status().isOk)

        verify {
            eventProcessor.process(
                SlackEvent(
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

    @Test
    fun `should return ok when event payload is missing`() {
        val payload = mapOf<String, Any>()

        mockMvc
            .perform(
                post("/api/v1/slack/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload)),
            ).andExpect(status().isOk)

        verify(exactly = 0) { eventProcessor.process(any()) }
    }

    @Test
    fun `should return ok for non-message event type`() {
        val payload =
            mapOf(
                "event" to
                    mapOf(
                        "type" to "reaction_added",
                        "text" to "thumbsup",
                        "channel" to "C456",
                        "ts" to "1234567890.123456",
                    ),
            )

        mockMvc
            .perform(
                post("/api/v1/slack/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload)),
            ).andExpect(status().isOk)

        verify {
            eventProcessor.process(
                SlackEvent(
                    type = "reaction_added",
                    subtype = null,
                    botId = null,
                    text = "thumbsup",
                    channel = "C456",
                    threadTs = "1234567890.123456",
                ),
            )
        }
    }
}

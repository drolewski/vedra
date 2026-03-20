package io.vedra.infrastructure.slack.api.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.infrastructure.slack.service.SlackChallengeHandler
import io.vedra.infrastructure.slack.service.SlackEventRequestHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SlackEventsControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var challengeHandler: SlackChallengeHandler
    private lateinit var eventRequestHandler: SlackEventRequestHandler
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        challengeHandler = mockk(relaxed = true)
        eventRequestHandler = mockk(relaxed = true)
        every { challengeHandler.handle(any()) } returns null
        every { eventRequestHandler.handle(any()) } returns ResponseEntity.ok().build()
        val controller = SlackEventsController(challengeHandler, eventRequestHandler)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `should delegate to eventRequestHandler when no challenge`() {
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

        verify { challengeHandler.handle(any()) }
        verify { eventRequestHandler.handle(any()) }
    }

    @Test
    fun `should delegate to eventRequestHandler when event payload is missing`() {
        val payload = mapOf<String, Any>()

        mockMvc
            .perform(
                post("/api/v1/slack/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload)),
            ).andExpect(status().isOk)

        verify { challengeHandler.handle(any()) }
        verify { eventRequestHandler.handle(any()) }
    }

    @Test
    fun `should delegate to eventRequestHandler for non-message event type`() {
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

        verify { challengeHandler.handle(any()) }
        verify { eventRequestHandler.handle(any()) }
    }

    @Test
    fun `should return challenge when url_verification request`() {
        val challengeResponse: ResponseEntity<Any> = ResponseEntity.ok(mapOf("challenge" to "3eZbrw1aMUhUEjornBHxJXNQKzqnux salopes"))
        every { challengeHandler.handle(any()) } returns challengeResponse

        val payload =
            mapOf(
                "token" to "Jhj5dZrVaK7ZwHHjRyZWjbDl",
                "challenge" to "3eZbrw1aMUhUEjornBHxJXNQKzqnux salopes",
                "type" to "url_verification",
            )

        mockMvc
            .perform(
                post("/api/v1/slack/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload)),
            ).andExpect(status().isOk)
            .andExpect(content().json("""{"challenge":"3eZbrw1aMUhUEjornBHxJXNQKzqnux salopes"}"""))

        verify { challengeHandler.handle(any()) }
        verify(exactly = 0) { eventRequestHandler.handle(any()) }
    }
}

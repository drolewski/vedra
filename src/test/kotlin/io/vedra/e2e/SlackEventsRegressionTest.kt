package io.vedra.e2e

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.infrastructure.slack.api.events.SlackEventsController
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

class SlackEventsRegressionTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var challengeHandler: SlackChallengeHandler
    private lateinit var eventRequestHandler: SlackEventRequestHandler
    private val mapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        challengeHandler = mockk(relaxed = true)
        eventRequestHandler = mockk(relaxed = true)
        every { challengeHandler.handle(any()) } returns null
        every { eventRequestHandler.handle(any()) } returns ResponseEntity.ok().build()
        mockMvc = MockMvcBuilders.standaloneSetup(SlackEventsController(challengeHandler, eventRequestHandler)).build()
    }

    @Test
    fun `TC-SLACK-EV-01 url_verification returns challenge`() {
        every { challengeHandler.handle(any()) } returns ResponseEntity.ok(mapOf("challenge" to "abc123"))
        mockMvc
            .perform(
                post("/api/v1/slack/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(mapOf("type" to "url_verification", "challenge" to "abc123"))),
            ).andExpect(status().isOk)
            .andExpect(content().json("""{"challenge":"abc123"}"""))
        verify(exactly = 0) { eventRequestHandler.handle(any()) }
    }

    @Test
    fun `TC-SLACK-EV-02 message event is delegated`() {
        val payload =
            mapOf(
                "type" to "event_callback",
                "event" to mapOf("type" to "message", "text" to "Pomocy?", "channel" to "C1", "ts" to "1.0"),
            )
        mockMvc
            .perform(
                post("/api/v1/slack/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(payload)),
            ).andExpect(status().isOk)
        verify { eventRequestHandler.handle(any()) }
    }

    @Test
    fun `TC-SLACK-EV-03 unknown event never breaks with 500`() {
        mockMvc
            .perform(
                post("/api/v1/slack/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        mapper.writeValueAsString(
                            mapOf("event" to mapOf("type" to "reaction_added", "channel" to "C1")),
                        ),
                    ),
            ).andExpect(status().isOk)
    }
}

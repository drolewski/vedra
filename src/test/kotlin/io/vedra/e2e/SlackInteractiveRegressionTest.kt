package io.vedra.e2e

import io.mockk.every
import io.mockk.mockk
import io.vedra.infrastructure.slack.api.interactive.SlackInteractiveController
import io.vedra.infrastructure.slack.service.SlackInteractiveRequestHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SlackInteractiveRegressionTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var handler: SlackInteractiveRequestHandler

    @BeforeEach
    fun setUp() {
        handler = mockk(relaxed = true)
        every { handler.handle(any()) } returns ResponseEntity.ok().build()
        mockMvc = MockMvcBuilders.standaloneSetup(SlackInteractiveController(handler)).build()
    }

    @Test
    fun `TC-SLACK-INT-01 malformed payload never breaks`() {
        mockMvc
            .perform(
                post("/api/v1/slack/interactive")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("payload", "not-json{{{"),
            ).andExpect(status().isOk)
    }

    @Test
    fun `TC-SLACK-INT-02 validation error shape is stable`() {
        val error: ResponseEntity<Any> =
            ResponseEntity.ok(
                mapOf(
                    "response_action" to "errors",
                    "errors" to mapOf("project_name_block" to "Nazwa projektu jest wymagana."),
                ),
            )
        every { handler.handle(any()) } returns error
        mockMvc
            .perform(
                post("/api/v1/slack/interactive")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("payload", """{"type":"view_submission"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.response_action").value("errors"))
            .andExpect(jsonPath("$.errors.project_name_block").value("Nazwa projektu jest wymagana."))
    }

    @Test
    fun `TC-SLACK-INT-03 success update shape is stable`() {
        val ok: ResponseEntity<Any> = ResponseEntity.ok(mapOf("response_action" to "update"))
        every { handler.handle(any()) } returns ok
        mockMvc
            .perform(
                post("/api/v1/slack/interactive")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("payload", """{"type":"view_submission"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.response_action").value("update"))
    }
}

package io.vedra.e2e

import io.mockk.every
import io.mockk.mockk
import io.vedra.infrastructure.slack.api.commands.SlackSlashCommandController
import io.vedra.infrastructure.slack.service.command.CommandDispatcher
import io.vedra.infrastructure.slack.service.command.SlackCommandParser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SlackCommandsRegressionTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var dispatcher: CommandDispatcher

    @BeforeEach
    fun setUp() {
        dispatcher = mockk(relaxed = true)
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(SlackSlashCommandController(dispatcher, SlackCommandParser()))
                .build()
    }

    private fun command(text: String) =
        post("/api/v1/slack/commands")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("command", "/vedra")
            .param("text", text)
            .param("channel_id", "C1")
            .param("team_id", "T1")
            .param("trigger_id", "TR1")

    @Test
    fun `TC-SLACK-CMD-01 unknown slash command`() {
        mockMvc
            .perform(
                post("/api/v1/slack/commands")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("command", "/xyz")
                    .param("text", "help")
                    .param("channel_id", "C1")
                    .param("team_id", "T1")
                    .param("trigger_id", "TR1"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.text").value("Nieznana komenda"))
    }

    @Test
    fun `TC-SLACK-CMD-02 help returns ephemeral text`() {
        every { dispatcher.dispatch(any()) } returns "Dostępne komendy"
        mockMvc
            .perform(command("help"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.response_type").value("ephemeral"))
            .andExpect(jsonPath("$.text").value("Dostępne komendy"))
    }

    @Test
    fun `TC-SLACK-CMD-03 create opens modal with empty body`() {
        every { dispatcher.dispatch(any()) } returns ""
        mockMvc
            .perform(command("create"))
            .andExpect(status().isOk)
    }
}

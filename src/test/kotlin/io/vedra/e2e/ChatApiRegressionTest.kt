package io.vedra.e2e

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.api.controller.ChatController
import io.vedra.domain.model.AnswerResult
import io.vedra.domain.model.ResponseSource
import io.vedra.domain.service.KnowledgeService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class ChatApiRegressionTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var knowledgeService: KnowledgeService
    private val mapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        knowledgeService = mockk(relaxed = true)
        mockMvc = MockMvcBuilders.standaloneSetup(ChatController(knowledgeService)).build()
    }

    @Test
    fun `TC-CHAT-01 missing tenant header returns 400`() {
        mockMvc
            .perform(
                post("/api/v1/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"question":"Ile kosztuje?"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Brak nagłówka X-Tenant-Id"))
        verify(exactly = 0) { knowledgeService.answerQuestion(any(), any(), any()) }
    }

    @Test
    fun `TC-CHAT-02 invalid tenant header returns 400`() {
        mockMvc
            .perform(
                post("/api/v1/chat")
                    .header("X-Tenant-Id", "zzz")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"question":"Ile kosztuje?"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Nieprawidłowy X-Tenant-Id"))
    }

    @Test
    fun `TC-CHAT-03 valid tenant returns answer contract`() {
        val tenantId = UUID.randomUUID()
        every { knowledgeService.answerQuestion(any(), any(), any()) } returns
            AnswerResult(answer = "Odp.", source = ResponseSource.AI_GENERAL, confidence = 0.8)
        mockMvc
            .perform(
                post("/api/v1/chat")
                    .header("X-Tenant-Id", tenantId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(mapOf("question" to "Ile kosztuje?"))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.answer").value("Odp."))
            .andExpect(jsonPath("$.source").value("AI_GENERAL"))
            .andExpect(jsonPath("$.confidence").value(0.8))
        verify { knowledgeService.answerQuestion("Ile kosztuje?", listOf(null), tenantId) }
    }

    @Test
    fun `TC-CHAT-04 knowledge endpoint stores answer`() {
        val tenantId = UUID.randomUUID()
        mockMvc
            .perform(
                post("/api/v1/chat/knowledge")
                    .header("X-Tenant-Id", tenantId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"question":"P?","answer":"O!"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.answer").value("Dodano do bazy wiedzy"))
        verify { knowledgeService.addKnowledge("P?", "O!", null, tenantId) }
    }

    @Test
    fun `TC-CHAT-04b knowledge without tenant returns 400`() {
        mockMvc
            .perform(
                post("/api/v1/chat/knowledge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"question":"P?","answer":"O!"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Brak nagłówka X-Tenant-Id"))
    }
}

package io.vedra.api.controller

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

class ChatControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var knowledgeService: KnowledgeService

    @BeforeEach
    fun setUp() {
        knowledgeService = mockk(relaxed = true)
        mockMvc = MockMvcBuilders.standaloneSetup(ChatController(knowledgeService)).build()
    }

    @Test
    fun `should return 400 without X-Tenant-Id`() {
        mockMvc
            .perform(
                post("/api/v1/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"question":"Pytanie?"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Brak nagłówka X-Tenant-Id"))

        verify(exactly = 0) { knowledgeService.answerQuestion(any(), any(), any()) }
    }

    @Test
    fun `should return 400 with invalid X-Tenant-Id`() {
        mockMvc
            .perform(
                post("/api/v1/chat")
                    .header("X-Tenant-Id", "zzz")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"question":"Pytanie?"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Nieprawidłowy X-Tenant-Id"))

        verify(exactly = 0) { knowledgeService.answerQuestion(any(), any(), any()) }
    }

    @Test
    fun `should delegate with tenant from header`() {
        val tenantId = UUID.randomUUID()
        every { knowledgeService.answerQuestion(any(), any(), any()) } returns
            AnswerResult(answer = "Odp.", source = ResponseSource.AI_GENERAL, confidence = 0.0)

        mockMvc
            .perform(
                post("/api/v1/chat")
                    .header("X-Tenant-Id", tenantId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"question":"Pytanie?"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.answer").value("Odp."))

        verify { knowledgeService.answerQuestion("Pytanie?", listOf(null), tenantId) }
    }

    @Test
    fun `knowledge should delegate with tenant from header`() {
        val tenantId = UUID.randomUUID()

        mockMvc
            .perform(
                post("/api/v1/chat/knowledge")
                    .header("X-Tenant-Id", tenantId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"question":"P?","answer":"O!"}"""),
            ).andExpect(status().isOk)

        verify { knowledgeService.addKnowledge("P?", "O!", null, tenantId) }
    }
}

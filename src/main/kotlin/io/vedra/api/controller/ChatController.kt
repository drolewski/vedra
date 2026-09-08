package io.vedra.api.controller

import io.vedra.api.advice.shared.ErrorResponse
import io.vedra.domain.service.KnowledgeService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/chat")
class ChatController(
    private val knowledgeService: KnowledgeService,
) {
    @PostMapping
    fun chat(
        @RequestHeader(value = "X-Tenant-Id", required = false) tenantHeader: String?,
        @RequestBody request: ChatRequest,
    ): ResponseEntity<Any> {
        val tenantId = parseTenantId(tenantHeader) ?: return tenantError(tenantHeader)
        val result = knowledgeService.answerQuestion(request.question, listOf(null), tenantId)
        return ResponseEntity.ok(
            ChatResponse(
                answer = result.answer,
                source = result.source.name,
                confidence = result.confidence,
            ),
        )
    }

    @PostMapping("/knowledge")
    fun knowledge(
        @RequestHeader(value = "X-Tenant-Id", required = false) tenantHeader: String?,
        @RequestBody request: ChatKnowledgeRequest,
    ): ResponseEntity<Any> {
        val tenantId = parseTenantId(tenantHeader) ?: return tenantError(tenantHeader)
        knowledgeService.addKnowledge(request.question, request.answer, null, tenantId)
        return ResponseEntity.ok(ChatResponse(answer = "Dodano do bazy wiedzy"))
    }

    private fun parseTenantId(header: String?): UUID? =
        try {
            if (header.isNullOrBlank()) null else UUID.fromString(header)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun tenantError(header: String?): ResponseEntity<Any> {
        val message = if (header.isNullOrBlank()) "Brak nagłówka X-Tenant-Id" else "Nieprawidłowy X-Tenant-Id"
        return ResponseEntity.badRequest().body(ErrorResponse(status = 400, message = message))
    }
}

data class ChatRequest(
    val question: String,
)

data class ChatKnowledgeRequest(
    val question: String,
    val answer: String,
)

data class ChatResponse(
    val answer: String,
    val source: String? = null,
    val confidence: Double? = null,
)

package io.vedra.api.controller

import io.vedra.domain.service.KnowledgeService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/chat")
class ChatController(
    private val knowledgeService: KnowledgeService,
) {
    @PostMapping
    fun chat(
        @RequestBody request: ChatRequest,
    ): ResponseEntity<ChatResponse> {
        val result = knowledgeService.answerQuestion(request.question)
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
        @RequestBody request: ChatKnowledgeRequest,
    ): ResponseEntity<ChatResponse> {
        knowledgeService.addKnowledge(request.question, request.answer)
        return ResponseEntity.ok(ChatResponse(answer = "Dodano do bazy wiedzy"))
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

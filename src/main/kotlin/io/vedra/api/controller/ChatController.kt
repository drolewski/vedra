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
        val response = knowledgeService.answerQuestion(request.question)
        return ResponseEntity.ok(ChatResponse(response = response))
    }

    @PostMapping("/knowledge")
    fun knowledge(
        @RequestBody request: ChatKnowledgeRequest,
    ): ResponseEntity<ChatResponse> {
        knowledgeService.addKnowledge(request.question, request.answer)
        return ResponseEntity.ok(ChatResponse(response = "Dodano do bazy wiedzy"))
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
    val response: String,
)

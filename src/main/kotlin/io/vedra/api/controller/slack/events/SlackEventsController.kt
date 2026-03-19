package io.vedra.api.controller.slack.events

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/slack/events")
class SlackEventsController {
    @PostMapping
    fun slackEvents(@RequestBody body: Map<String, Any>): ResponseEntity<Map<String, String?>> {
        val challenge = body["challenge"] as? String
        if (challenge != null) {
            return ResponseEntity.ok(mapOf("challenge" to challenge))
        }
        // Obsługa eventów

        println(body)
        val event = body["event"] as? Map<*, *> ?: return ResponseEntity.ok().build()
        val type = event["type"] as? String
        val text = event["text"] as? String
        val channel = event["channel"] as? String
        val threadTs = event["ts"] as? String

        if (type in listOf("message", "app_mention") && !text.isNullOrBlank()) {
//            val response = knowledgeService.answerQuestion(text)
            // Odpowiedź w Slacku (wymaga bot-token w nagłówku)
            println("generuję kodzik")
        }
        val body1 = mapOf(Pair<String, String?>("challenge", challenge))
        return ResponseEntity.ok(body1)
    }
}
package io.vedra.infrastructure.slack.service

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

@Component
class SlackChallengeHandler {
    fun handle(body: Map<String, Any>): ResponseEntity<Any>? {
        val challenge = body["challenge"] as? String ?: return null
        return ResponseEntity.ok(mapOf("challenge" to challenge))
    }
}

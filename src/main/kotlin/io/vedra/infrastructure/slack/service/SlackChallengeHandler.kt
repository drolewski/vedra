package io.vedra.infrastructure.slack.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.slack.model.dto.SlackChallengePayload
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class SlackChallengeHandler {
    fun handle(body: Map<String, Any>): ResponseEntity<Any>? {
        val payload = SlackChallengePayload.fromRawBody(body)
        logger.info { "Responding to Slack URL verification challenge" }
        return ResponseEntity.ok(mapOf("challenge" to payload.challenge))
    }
}

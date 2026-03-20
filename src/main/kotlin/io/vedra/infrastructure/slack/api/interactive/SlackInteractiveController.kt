package io.vedra.infrastructure.slack.api.interactive

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.slack.service.SlackInteractiveRequestHandler
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
class SlackInteractiveController(
    private val interactiveRequestHandler: SlackInteractiveRequestHandler,
) {
    private val objectMapper = jacksonObjectMapper()

    @PostMapping("/api/v1/slack/interactive", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun handleInteractive(
        @RequestParam payload: String,
    ): ResponseEntity<Any> {
        val rawPayload = parseJsonPayload(payload) ?: return ResponseEntity.ok().build()
        return interactiveRequestHandler.handle(rawPayload)
    }

    private fun parseJsonPayload(payload: String): Map<String, Any>? =
        try {
            objectMapper.readValue(payload, Map::class.java) as Map<String, Any>
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse Slack interactive payload JSON" }
            null
        }
}

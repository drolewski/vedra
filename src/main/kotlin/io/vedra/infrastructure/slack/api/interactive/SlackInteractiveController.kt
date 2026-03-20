package io.vedra.infrastructure.slack.api.interactive

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.vedra.infrastructure.outbox.service.OutboxService
import io.vedra.infrastructure.slack.service.SlackMessageService.Companion.ACTION_NEGATIVE
import io.vedra.infrastructure.slack.service.SlackMessageService.Companion.ACTION_POSITIVE
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient

@RestController
class SlackInteractiveController(
    private val outboxService: OutboxService,
) {
    private val objectMapper = jacksonObjectMapper()
    private val webClient = WebClient.create()

    @PostMapping("/api/v1/slack/interactive", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun handleInteractive(
        @RequestParam payload: String,
    ): ResponseEntity<Any> {
        val json = objectMapper.readValue(payload, Map::class.java) as Map<String, Any>

        val actions = json["actions"] as? List<*> ?: return ResponseEntity.ok().build()
        val action = actions.firstOrNull() as? Map<*, *> ?: return ResponseEntity.ok().build()

        val actionId = action["action_id"] as? String ?: return ResponseEntity.ok().build()
        val feedbackId = action["value"] as? String ?: return ResponseEntity.ok().build()

        val user = json["user"] as? Map<*, *> ?: return ResponseEntity.ok().build()
        val userId = user["id"] as? String ?: return ResponseEntity.ok().build()

        val responseUrl = json["response_url"] as? String

        val vote =
            when (actionId) {
                ACTION_POSITIVE -> "positive"
                ACTION_NEGATIVE -> "negative"
                else -> return ResponseEntity.ok().build()
            }

        outboxService.saveEvent(
            eventType = EVENT_TYPE,
            aggregateId = "${feedbackId}_${userId}_$vote",
            payload =
                mapOf(
                    "feedbackId" to feedbackId,
                    "userId" to userId,
                    "vote" to vote,
                ),
        )

        if (responseUrl != null) {
            sendConfirmation(responseUrl, vote)
        }

        return ResponseEntity.ok().build()
    }

    private fun sendConfirmation(
        responseUrl: String,
        vote: String,
    ) {
        val message =
            when (vote) {
                "positive" -> "Dziękujemy za feedback! Twoja opinia została zapisana."
                "negative" -> "Dziękujemy za feedback! Będziemy pracować nad poprawą odpowiedzi."
                else -> "Feedback przyjęty."
            }

        try {
            webClient
                .post()
                .uri(responseUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("text" to message, "replace_original" to false))
                .retrieve()
                .toBodilessEntity()
                .block()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val EVENT_TYPE = "SLACK_FEEDBACK"
    }
}

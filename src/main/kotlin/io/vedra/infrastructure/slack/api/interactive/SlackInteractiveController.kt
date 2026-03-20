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

@RestController
class SlackInteractiveController(
    private val outboxService: OutboxService,
) {
    private val objectMapper = jacksonObjectMapper()

    @PostMapping("/api/v1/slack/interactive", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun handleInteractive(
        @RequestParam payload: String,
    ): ResponseEntity<Map<String, String>> {
        val json = objectMapper.readValue(payload, Map::class.java) as Map<String, Any>

        val actions = json["actions"] as? List<*> ?: return okResponse()
        val action = actions.firstOrNull() as? Map<*, *> ?: return okResponse()

        val actionId = action["action_id"] as? String ?: return okResponse()
        val feedbackId = action["value"] as? String ?: return okResponse()

        val user = json["user"] as? Map<*, *> ?: return okResponse()
        val userId = user["id"] as? String ?: return okResponse()

        val vote =
            when (actionId) {
                ACTION_POSITIVE -> "positive"
                ACTION_NEGATIVE -> "negative"
                else -> return okResponse()
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

        return confirmationResponse(vote)
    }

    private fun confirmationResponse(vote: String): ResponseEntity<Map<String, String>> {
        val message =
            when (vote) {
                "positive" -> "Dziękujemy za feedback :thumbsup: Twoja opinia została zapisana."
                "negative" -> "Dziękujemy za feedback :thumbsup: Będziemy pracować nad poprawą odpowiedzi."
                else -> "Feedback przyjęty."
            }
        return ResponseEntity.ok(mapOf("text" to message))
    }

    private fun okResponse(): ResponseEntity<Map<String, String>> = ResponseEntity.ok().build()

    companion object {
        const val EVENT_TYPE = "SLACK_FEEDBACK"
    }
}

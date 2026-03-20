package io.vedra.infrastructure.slack.api.interactive

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.vedra.infrastructure.outbox.service.OutboxService
import io.vedra.infrastructure.slack.service.SlackMessageService
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
    private val slackMessageService: SlackMessageService,
) {
    private val objectMapper = jacksonObjectMapper()

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

        val vote =
            when (actionId) {
                ACTION_POSITIVE -> "positive"
                ACTION_NEGATIVE -> "negative"
                else -> return ResponseEntity.ok().build()
            }

        outboxService.saveEvent(
            eventType = EVENT_TYPE,
            aggregateId = "${feedbackId}_$userId",
            payload =
                mapOf(
                    "feedbackId" to feedbackId,
                    "userId" to userId,
                    "vote" to vote,
                ),
        )

        try {
            val message = json["message"] as? Map<*, *>
            val channel =
                (json["container"] as? Map<*, *>)?.get("channel_id") as? String
                    ?: message?.get("channel") as? String
            val messageTs = message?.get("ts") as? String
            val originalBlocks = message?.get("blocks") as? List<*>

            if (channel != null && messageTs != null && originalBlocks != null) {
                slackMessageService.replaceFeedbackButtons(channel, messageTs, originalBlocks, vote)
            }
        } catch (_: Exception) {
        }

        return ResponseEntity.ok().build()
    }

    companion object {
        const val EVENT_TYPE = "SLACK_FEEDBACK"
    }
}

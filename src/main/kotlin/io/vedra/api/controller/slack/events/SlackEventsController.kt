package io.vedra.api.controller.slack.events

import com.slack.api.model.event.MessageEvent
import io.vedra.infrastructure.slack.SlackMessageService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/slack/events")
class SlackEventsController(
    private val slackMessageService: SlackMessageService,
) {
    @PostMapping
    fun slackEvents(
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<Void> {

        println(body)
        val event = body["event"] as? Map<*, *> ?: return ResponseEntity.ok().build()
        val type = event["type"] as? String
        val subtype = event["subtype"] as? String
        val botId = event["bot_id"] as? String
        val text = event["text"] as? String
        val channel = event["channel"] as? String
        val threadTs = event["ts"] as? String

        if (type == MessageEvent.TYPE_NAME
            && subtype != "bot_message"
            && botId == null
            && !text.isNullOrBlank()
            && channel != null
            && threadTs != null
        ) {
            slackMessageService.sendThreadMessage(channel, threadTs, "Palindrom:\n```$text```")
        }
        return ResponseEntity.ok().build()
    }
}

package io.vedra.infrastructure.outbox

import io.vedra.domain.service.KnowledgeService
import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.slack.SlackMessageService
import org.springframework.stereotype.Component

@Component
class SlackMessageAnswerHandler(
    private val knowledgeService: KnowledgeService,
    private val slackMessageService: SlackMessageService,
) : OutboxEventHandler {
    override fun supports(eventType: String): Boolean = eventType == "SLACK_MESSAGE_ANSWER"

    override fun handle(event: OutboxEvent) {
        val channel = event.payload["channel"] as? String ?: return
        val threadTs = event.payload["threadTs"] as? String ?: return
        val question = event.payload["question"] as? String ?: return

        val answer = knowledgeService.answerQuestion(question)
        slackMessageService.sendThreadMessage(channel, threadTs, answer)
    }

    companion object {
        const val EVENT_TYPE = "SLACK_MESSAGE_ANSWER"
    }
}

package io.vedra.infrastructure.slack.service.outbox

import io.vedra.domain.model.AnswerResult
import io.vedra.domain.model.ResponseSource
import io.vedra.domain.service.FeedbackService
import io.vedra.domain.service.KnowledgeService
import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.outbox.handler.OutboxEventHandler
import io.vedra.infrastructure.slack.service.SlackMessageService
import org.springframework.stereotype.Component

@Component
class SlackMessageAnswerHandler(
    private val knowledgeService: KnowledgeService,
    private val slackMessageService: SlackMessageService,
    private val feedbackService: FeedbackService,
) : OutboxEventHandler {
    override fun supports(eventType: String): Boolean = eventType == "SLACK_MESSAGE_ANSWER"

    override fun handle(event: OutboxEvent) {
        val channel = event.payload["channel"] as? String ?: return
        val threadTs = event.payload["threadTs"] as? String ?: return
        val question = event.payload["question"] as? String ?: return

        val result = knowledgeService.answerQuestion(question)
        val message = formatMessage(result)
        slackMessageService.sendThreadMessage(channel, threadTs, message)

        feedbackService.saveMapping(
            threadTs = threadTs,
            entryIds = result.entryIds,
            answerSource = result.source.name,
        )
    }

    private fun formatMessage(result: AnswerResult): String {
        val sourceLabel =
            when (result.source) {
                ResponseSource.KNOWLEDGE_BASE -> "_Odpowiedź z bazy wiedzy._"
                ResponseSource.AI_WITH_CONTEXT -> "_Wygenerowane przez AI na podstawie bazy wiedzy._"
                ResponseSource.AI_GENERAL -> "_Wygenerowane przez AI. Brak informacji w bazie wiedzy._"
            }
        return "${result.answer}\n\n$sourceLabel"
    }

    companion object {
        const val EVENT_TYPE = "SLACK_MESSAGE_ANSWER"
    }
}

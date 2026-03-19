package io.vedra.infrastructure.slack.handler

import com.slack.api.model.event.MessageEvent
import io.vedra.domain.service.KnowledgeService
import io.vedra.infrastructure.slack.SlackMessageService
import io.vedra.infrastructure.slack.model.SlackEvent
import io.vedra.infrastructure.slack.model.SlackEventHandler
import org.springframework.stereotype.Component

@Component
class MessageEventHandler(
    private val slackMessageService: SlackMessageService,
    private val knowledgeService: KnowledgeService,
) : SlackEventHandler {
    override fun supports(event: SlackEvent): Boolean = event.type == MessageEvent.TYPE_NAME

    override fun handle(event: SlackEvent) {
        val channel = event.channel ?: return
        val threadTs = event.threadTs ?: return
        val question = event.text ?: return

        val answer = knowledgeService.answerQuestion(question)
        slackMessageService.sendThreadMessage(channel, threadTs, answer)
    }
}

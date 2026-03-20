package io.vedra.infrastructure.slack.service

import com.slack.api.bolt.App
import com.slack.api.model.block.ActionsBlock
import com.slack.api.model.block.SectionBlock
import com.slack.api.model.block.composition.MarkdownTextObject
import com.slack.api.model.block.composition.PlainTextObject
import com.slack.api.model.block.element.ButtonElement
import org.springframework.stereotype.Service

@Service
class SlackMessageService(
    private val slackApp: App,
) {
    fun sendThreadMessage(
        channel: String,
        threadTs: String,
        text: String,
    ) {
        slackApp.client().chatPostMessage { req ->
            req
                .channel(channel)
                .threadTs(threadTs)
                .text(text)
        }
    }

    fun sendThreadMessageWithFeedback(
        channel: String,
        threadTs: String,
        text: String,
        feedbackId: String,
    ): String {
        val response =
            slackApp.client().chatPostMessage { req ->
                req
                    .channel(channel)
                    .threadTs(threadTs)
                    .text(text)
                    .blocks(
                        listOf(
                            SectionBlock
                                .builder()
                                .text(MarkdownTextObject.builder().text(text).build())
                                .build(),
                            ActionsBlock
                                .builder()
                                .elements(
                                    listOf(
                                        ButtonElement
                                            .builder()
                                            .text(PlainTextObject.builder().text("Pomogło :+1:").build())
                                            .actionId(ACTION_POSITIVE)
                                            .value(feedbackId)
                                            .style("primary")
                                            .build(),
                                        ButtonElement
                                            .builder()
                                            .text(PlainTextObject.builder().text("Nie pomogło :-1:").build())
                                            .actionId(ACTION_NEGATIVE)
                                            .value(feedbackId)
                                            .style("danger")
                                            .build(),
                                    ),
                                ).build(),
                        ),
                    )
            }
        return response.ts
    }

    companion object {
        const val ACTION_POSITIVE = "feedback_positive"
        const val ACTION_NEGATIVE = "feedback_negative"
    }
}

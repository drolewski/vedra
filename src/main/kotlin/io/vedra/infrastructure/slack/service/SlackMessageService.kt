package io.vedra.infrastructure.slack.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
    private val objectMapper = jacksonObjectMapper()

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
                                .blockId(BLOCK_ACTIONS)
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

    fun replaceFeedbackButtons(
        channel: String,
        messageTs: String,
        originalBlocks: List<*>,
        vote: String,
    ) {
        val label =
            when (vote) {
                "positive" -> ":thumbsup: Pomogło"
                "negative" -> ":thumbsdown: Nie pomogło"
                else -> vote
            }

        val confirmationBlock =
            mapOf(
                "type" to "context",
                "elements" to
                    listOf(
                        mapOf("type" to "mrkdwn", "text" to "_Oddano głos: $label._"),
                    ),
            )

        val filteredBlocks =
            (originalBlocks as List<Any>)
                .filter { block ->
                    val map = block as? Map<*, *>
                    map?.get("block_id") != BLOCK_ACTIONS
                } + confirmationBlock

        val blocksJson = objectMapper.writeValueAsString(filteredBlocks)

        slackApp.client().chatUpdate { req ->
            req
                .channel(channel)
                .ts(messageTs)
                .blocksAsString(blocksJson)
        }
    }

    companion object {
        const val ACTION_POSITIVE = "feedback_positive"
        const val ACTION_NEGATIVE = "feedback_negative"
        private const val BLOCK_ACTIONS = "feedback_actions"
    }
}

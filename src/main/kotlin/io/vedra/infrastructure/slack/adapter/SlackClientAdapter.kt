package io.vedra.infrastructure.slack.adapter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.slack.api.bolt.App
import com.slack.api.methods.request.views.ViewsOpenRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.slack.exception.SlackApiException
import io.vedra.infrastructure.slack.port.SlackClientPort
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class SlackClientAdapter(
    private val slackApp: App,
) : SlackClientPort {
    private val objectMapper = jacksonObjectMapper()

    override fun postThreadMessage(
        channel: String,
        threadTs: String,
        text: String,
    ) {
        try {
            slackApp.client().chatPostMessage { req ->
                req.channel(channel).threadTs(threadTs).text(text)
            }
        } catch (e: Exception) {
            throw SlackApiException("Failed to post thread message to channel $channel", e)
        }
    }

    override fun postThreadMessageWithBlocks(
        channel: String,
        threadTs: String,
        blocks: List<Map<String, Any>>,
        fallbackText: String,
    ): String {
        try {
            val blocksJson = objectMapper.writeValueAsString(blocks)
            val response =
                slackApp.client().chatPostMessage { req ->
                    req
                        .channel(channel)
                        .threadTs(threadTs)
                        .text(fallbackText)
                        .blocksAsString(blocksJson)
                }
            return response.ts
        } catch (e: Exception) {
            throw SlackApiException("Failed to post message with blocks to channel $channel", e)
        }
    }

    override fun updateMessage(
        channel: String,
        messageTs: String,
        blocks: List<Map<String, Any>>,
    ) {
        try {
            val blocksJson = objectMapper.writeValueAsString(blocks)
            slackApp.client().chatUpdate { req ->
                req.channel(channel).ts(messageTs).blocksAsString(blocksJson)
            }
        } catch (e: Exception) {
            throw SlackApiException("Failed to update message $messageTs in channel $channel", e)
        }
    }

    override fun openModal(request: ViewsOpenRequest) {
        try {
            slackApp.client().viewsOpen(request)
        } catch (e: Exception) {
            throw SlackApiException("Failed to open modal", e)
        }
    }

    override fun getTeamName(teamId: String): String? =
        try {
            val response = slackApp.client().teamInfo { req -> req.team(teamId) }
            response.team.name
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get team name for teamId=$teamId" }
            null
        }
}

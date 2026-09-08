package io.vedra.infrastructure.slack.adapter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.slack.api.bolt.App
import com.slack.api.methods.request.views.ViewsOpenRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.service.SlackInstallationService
import io.vedra.infrastructure.slack.exception.SlackApiException
import io.vedra.infrastructure.slack.exception.SlackNotInstalledException
import io.vedra.infrastructure.slack.port.SlackClientPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class SlackClientAdapter(
    private val slackApp: App,
    private val installationService: SlackInstallationService,
    @Value($$"${slack.bot-token:}") private val legacyBotToken: String,
) : SlackClientPort {
    private val objectMapper = jacksonObjectMapper()

    private fun tokenFor(teamId: String?): String {
        if (!teamId.isNullOrBlank()) {
            installationService.findBotToken(teamId)?.let { return it }
        }
        if (legacyBotToken.isNotBlank()) {
            if (!teamId.isNullOrBlank()) {
                logger.warn { "[INSTALL] legacy global token used team=$teamId" }
            }
            return legacyBotToken
        }
        throw SlackNotInstalledException(teamId ?: "unknown")
    }

    override fun postThreadMessage(
        channel: String,
        threadTs: String,
        text: String,
        teamId: String?,
    ) {
        try {
            val token = tokenFor(teamId)
            slackApp.client().chatPostMessage { req ->
                req
                    .token(token)
                    .channel(channel)
                    .threadTs(threadTs)
                    .text(text)
            }
        } catch (e: SlackNotInstalledException) {
            throw e
        } catch (e: Exception) {
            throw SlackApiException("Failed to post thread message to channel $channel", e)
        }
    }

    override fun postThreadMessageWithBlocks(
        channel: String,
        threadTs: String,
        blocks: List<Map<String, Any>>,
        fallbackText: String,
        teamId: String?,
    ): String {
        try {
            val token = tokenFor(teamId)
            val blocksJson = objectMapper.writeValueAsString(blocks)
            val response =
                slackApp.client().chatPostMessage { req ->
                    req
                        .token(token)
                        .channel(channel)
                        .threadTs(threadTs)
                        .text(fallbackText)
                        .blocksAsString(blocksJson)
                }
            return response.ts
        } catch (e: SlackNotInstalledException) {
            throw e
        } catch (e: Exception) {
            throw SlackApiException("Failed to post message with blocks to channel $channel", e)
        }
    }

    override fun updateMessage(
        channel: String,
        messageTs: String,
        blocks: List<Map<String, Any>>,
        teamId: String?,
        text: String?,
    ) {
        try {
            val token = tokenFor(teamId)
            val blocksJson = objectMapper.writeValueAsString(blocks)
            slackApp.client().chatUpdate { req ->
                req
                    .token(token)
                    .channel(channel)
                    .ts(messageTs)
                    .blocksAsString(blocksJson)
                if (!text.isNullOrBlank()) req.text(text)
                req
            }
        } catch (e: SlackNotInstalledException) {
            throw e
        } catch (e: Exception) {
            throw SlackApiException("Failed to update message $messageTs in channel $channel", e)
        }
    }

    override fun openModal(
        request: ViewsOpenRequest,
        teamId: String?,
    ) {
        try {
            val token = tokenFor(teamId)
            if (request.token == null) request.token = token
            slackApp.client().viewsOpen(request)
        } catch (e: SlackNotInstalledException) {
            throw e
        } catch (e: Exception) {
            throw SlackApiException("Failed to open modal", e)
        }
    }

    override fun getTeamName(teamId: String): String? =
        try {
            val token = tokenFor(teamId)
            val response = slackApp.client().teamInfo { req -> req.token(token).team(teamId) }
            response.team.name
        } catch (e: SlackNotInstalledException) {
            logger.warn { "[INSTALL] getTeamName without installation team=$teamId" }
            null
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get team name for teamId=$teamId" }
            null
        }

    override fun getChannelName(
        channelId: String,
        teamId: String?,
    ): String? =
        try {
            val token = tokenFor(teamId)
            val response = slackApp.client().conversationsInfo { req -> req.token(token).channel(channelId) }
            if (response.isOk) response.channel.name else null
        } catch (e: SlackNotInstalledException) {
            logger.warn { "[INSTALL] getChannelName without installation channel=$channelId team=$teamId" }
            null
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get channel name for channelId=$channelId" }
            null
        }

    override fun postChannelMessage(
        channel: String,
        text: String,
        teamId: String?,
    ) {
        try {
            val token = tokenFor(teamId)
            slackApp.client().chatPostMessage { req ->
                req.token(token).channel(channel).text(text)
            }
        } catch (e: SlackNotInstalledException) {
            throw e
        } catch (e: Exception) {
            throw SlackApiException("Failed to post channel message to channel $channel", e)
        }
    }
}

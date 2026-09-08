package io.vedra.infrastructure.slack.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.service.TenantProjectService
import io.vedra.infrastructure.slack.model.SlackEvent
import io.vedra.infrastructure.slack.model.SlackEventHandler
import io.vedra.infrastructure.slack.port.SlackClientPort
import io.vedra.infrastructure.slack.service.SlackMessageService
import io.vedra.infrastructure.slack.service.SlackTenantResolver
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class BotChannelJoinHandler(
    private val tenantProjectService: TenantProjectService,
    private val tenantResolver: SlackTenantResolver,
    private val slackClient: SlackClientPort,
    private val slackMessageService: SlackMessageService,
) : SlackEventHandler {
    override fun supports(event: SlackEvent): Boolean = event.type == TYPE

    override fun handle(event: SlackEvent) {
        val channel = event.channel
        val workspaceId = event.workspaceId

        if (channel == null || workspaceId == null) {
            logger.debug { "Skipping join event without channel/workspace" }
            return
        }

        tenantResolver.withTenantResult(workspaceId) { tenant ->
            val tenantId = tenant.id!!
            val channelName =
                try {
                    slackClient.getChannelName(channel, workspaceId)
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to resolve channel name for channel=$channel, using fallback" }
                    null
                }
            when (val space = tenantProjectService.ensureChannelSpace(tenantId, channel, channelName)) {
                is TenantProjectService.ChannelSpaceResult.Created -> {
                    try {
                        slackMessageService.sendChannelMessage(
                            channel,
                            ":wave: Vedra dołączyła! Utworzyłam przestrzeń *${space.project.name}*. " +
                                "Pisz śmiało — zbieram wiedzę, na pytania odpowiadam w wątku. " +
                                "Szczegóły poprawisz przez `/vedra config`. Pomoc: `/vedra help`.",
                            workspaceId,
                        )
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to send welcome message to channel=$channel, space kept" }
                    }
                }
                is TenantProjectService.ChannelSpaceResult.AlreadyLinked -> {
                    logger.debug { "Join on already linked channel=$channel, skipping welcome" }
                }
            }
        }
    }

    companion object {
        const val TYPE = "member_joined_channel"
    }
}

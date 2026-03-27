package io.vedra.infrastructure.slack.service.command.link

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.service.TenantExternalIdMissingException
import io.vedra.domain.service.TenantProjectService
import io.vedra.domain.service.TenantProjectService.LinkResult
import io.vedra.domain.service.TenantService
import io.vedra.infrastructure.slack.port.SlackClientPort
import io.vedra.infrastructure.slack.service.command.CommandHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class LinkProjectCommandHandler(
    private val tenantProjectService: TenantProjectService,
    private val tenantService: TenantService,
    private val slackClient: SlackClientPort,
) : CommandHandler<LinkProjectCommand> {
    override fun supports(command: Any): Boolean = command is LinkProjectCommand

    override fun handle(command: LinkProjectCommand): String {
        val tenant =
            try {
                val teamName = slackClient.getTeamName(command.workspaceId)
                tenantService.resolveByExternalId(command.workspaceId, teamName)
            } catch (e: TenantExternalIdMissingException) {
                logger.error(e) { "Cannot resolve tenant: missing workspaceId" }
                return "Błąd: Nie można zidentyfikować workspace."
            }

        return when (tenantProjectService.linkChannelToProject(tenant.id!!, command.channelId, command.projectName)) {
            LinkResult.SUCCESS -> "Kanał powiązany z projektem *${command.projectName}*"
            LinkResult.PROJECT_NOT_FOUND ->
                "Projekt *${command.projectName}* nie istnieje. Użyj `/vedra list` aby zobaczyć dostępne projekty."

            LinkResult.CANNOT_LINK_GENERAL -> "Nie można powiązać kanału z projektem ogólnym."
        }
    }
}

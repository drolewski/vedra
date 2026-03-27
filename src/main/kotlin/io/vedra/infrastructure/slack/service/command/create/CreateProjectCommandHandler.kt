package io.vedra.infrastructure.slack.service.command.create

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.service.TenantExternalIdMissingException
import io.vedra.domain.service.TenantService
import io.vedra.infrastructure.slack.port.SlackClientPort
import io.vedra.infrastructure.slack.service.SlackModalService
import io.vedra.infrastructure.slack.service.command.CommandHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class CreateProjectCommandHandler(
    private val slackModalService: SlackModalService,
    private val tenantService: TenantService,
    private val slackClient: SlackClientPort,
) : CommandHandler<CreateProjectCommand> {
    override fun supports(command: Any): Boolean = command is CreateProjectCommand

    override fun handle(command: CreateProjectCommand): String {
        logger.info { "Opening create project modal for triggerId=${command.triggerId}" }

        val tenant =
            try {
                val teamName = slackClient.getTeamName(command.workspaceId)
                tenantService.resolveByExternalId(command.workspaceId, teamName)
            } catch (e: TenantExternalIdMissingException) {
                logger.error(e) { "Cannot resolve tenant: missing workspaceId" }
                return "Błąd: Nie można zidentyfikować workspace."
            }

        return try {
            slackModalService.openCreateProjectModal(
                triggerId = command.triggerId,
                channelId = command.channelId,
                tenantId = tenant.id!!,
            )
            ""
        } catch (e: Exception) {
            logger.error(e) { "Failed to open create project modal" }
            "Nie udało się otworzyć formularza. Spróbuj ponownie."
        }
    }
}

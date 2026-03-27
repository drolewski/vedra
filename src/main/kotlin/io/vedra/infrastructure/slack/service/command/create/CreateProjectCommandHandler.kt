package io.vedra.infrastructure.slack.service.command.create

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.slack.service.SlackModalService
import io.vedra.infrastructure.slack.service.SlackTenantResolver
import io.vedra.infrastructure.slack.service.command.CommandHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class CreateProjectCommandHandler(
    private val slackModalService: SlackModalService,
    private val tenantResolver: SlackTenantResolver,
) : CommandHandler<CreateProjectCommand> {
    override fun supports(command: Any): Boolean = command is CreateProjectCommand

    override fun handle(command: CreateProjectCommand): String =
        tenantResolver.withTenant(command.workspaceId) { tenant ->
            logger.info { "Opening create project modal for triggerId=${command.triggerId}" }
            try {
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

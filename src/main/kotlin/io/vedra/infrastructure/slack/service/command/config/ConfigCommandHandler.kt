package io.vedra.infrastructure.slack.service.command.config

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.model.ProjectVisibility
import io.vedra.domain.service.TenantProjectService
import io.vedra.infrastructure.slack.service.SlackModalService
import io.vedra.infrastructure.slack.service.SlackTenantResolver
import io.vedra.infrastructure.slack.service.command.CommandHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class ConfigCommandHandler(
    private val slackModalService: SlackModalService,
    private val tenantResolver: SlackTenantResolver,
    private val tenantProjectService: TenantProjectService,
) : CommandHandler<ConfigCommand> {
    override fun supports(command: Any): Boolean = command is ConfigCommand

    override fun handle(command: ConfigCommand): String =
        tenantResolver.withTenant(command.workspaceId) { tenant ->
            val tenantId = tenant.id!!
            val linked = tenantProjectService.getLinkedProject(command.channelId, tenantId)
            val project =
                if (linked == null) {
                    when (val space = tenantProjectService.ensureChannelSpace(tenantId, command.channelId, null)) {
                        is TenantProjectService.ChannelSpaceResult.Created -> space.project
                        is TenantProjectService.ChannelSpaceResult.AlreadyLinked ->
                            space.project ?: return@withTenant "Przestrzeni publicznej nie można edytować."
                    }
                } else {
                    linked
                }
            if (project.visibility == ProjectVisibility.PUBLIC) {
                return@withTenant "Przestrzeni publicznej nie można edytować."
            }
            logger.info { "Opening config modal for channel=${command.channelId} project=${project.name}" }
            try {
                slackModalService.openConfigModal(
                    triggerId = command.triggerId,
                    channelId = command.channelId,
                    tenantId = tenantId,
                    projectName = project.name,
                    description = project.description,
                    industry = project.industry,
                )
                ""
            } catch (e: Exception) {
                logger.error(e) { "Failed to open config modal" }
                "Nie udało się otworzyć formularza. Spróbuj ponownie."
            }
        }
}

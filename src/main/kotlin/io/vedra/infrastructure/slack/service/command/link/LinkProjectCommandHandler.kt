package io.vedra.infrastructure.slack.service.command.link

import io.vedra.domain.service.TenantProjectService
import io.vedra.domain.service.TenantProjectService.LinkResult
import io.vedra.infrastructure.slack.service.command.CommandHandler
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class LinkProjectCommandHandler(
    private val tenantProjectService: TenantProjectService,
) : CommandHandler<LinkProjectCommand> {
    override fun supports(command: Any): Boolean = command is LinkProjectCommand

    override fun handle(command: LinkProjectCommand): String {
        val tenantId = UUID.nameUUIDFromBytes(command.workspaceId.toByteArray())

        return when (tenantProjectService.linkChannelToProject(tenantId, command.channelId, command.projectName)) {
            LinkResult.SUCCESS -> "Kanał powiązany z projektem *${command.projectName}*"
            LinkResult.PROJECT_NOT_FOUND ->
                "Projekt *${command.projectName}* nie istnieje. Użyj `/vedra list` aby zobaczyć dostępne projekty."

            LinkResult.CANNOT_LINK_GENERAL -> "Nie można powiązać kanału z projektem ogólnym."
        }
    }
}

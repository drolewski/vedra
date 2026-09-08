package io.vedra.infrastructure.slack.service.command.status

import io.vedra.domain.service.TenantProjectService
import io.vedra.infrastructure.slack.service.SlackTenantResolver
import io.vedra.infrastructure.slack.service.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class StatusCommandHandler(
    private val tenantProjectService: TenantProjectService,
    private val tenantResolver: SlackTenantResolver,
) : CommandHandler<StatusCommand> {
    override fun supports(command: Any): Boolean = command is StatusCommand

    override fun handle(command: StatusCommand): String {
        if (command.workspaceId.isBlank()) {
            return "Nie można ustalić kontekstu workspace."
        }
        return tenantResolver.withTenant(command.workspaceId) { tenant ->
            val status = tenantProjectService.getChannelStatus(command.channelId, tenant.id!!)

            if (status.isLinked) {
                "Ten kanał jest powiązany z projektem *${status.projectName}*.\n" +
                    "Odpowiedzi będą szukane w bazie projektu + baza publiczna."
            } else {
                "Ten kanał nie ma własnego projektu.\n" +
                    "Odpowiedzi będą szukane tylko w bazie publicznej.\n" +
                    "Użyj `/vedra create` aby utworzyć projekt dla tego kanału."
            }
        }
    }
}

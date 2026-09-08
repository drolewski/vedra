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
                "*Konfiguracja przestrzeni tego kanału*\n" +
                    "• Nazwa: *${status.projectName}*\n" +
                    "• Opis: ${status.description ?: "—"}\n" +
                    "• Branża: ${status.industry ?: "nie ustawiono"}\n" +
                    "Odpowiedzi szukane w tej przestrzeni + baza publiczna.\n" +
                    "Zmień przez `/vedra config`."
            } else {
                "Ten kanał nie ma jeszcze własnej przestrzeni.\n" +
                    "Odpowiedzi szukane tylko w bazie publicznej.\n" +
                    "Uruchom `/vedra config`, aby utworzyć i skonfigurować przestrzeń."
            }
        }
    }
}

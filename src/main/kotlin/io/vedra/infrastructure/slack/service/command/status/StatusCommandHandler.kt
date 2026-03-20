package io.vedra.infrastructure.slack.service.command.status

import io.vedra.domain.service.TenantProjectService
import io.vedra.infrastructure.slack.service.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class StatusCommandHandler(
    private val tenantProjectService: TenantProjectService,
) : CommandHandler<StatusCommand> {
    override fun supports(command: Any): Boolean = command is StatusCommand

    override fun handle(command: StatusCommand): String {
        val status = tenantProjectService.getChannelStatus(command.channelId)

        return if (status.isLinked) {
            "Ten kanał jest powiązany z projektem *${status.projectName}*.\n" +
                "Odpowiedzi będą szukane w bazie projektu + baza ogólna."
        } else {
            "Ten kanał nie jest powiązany z żadnym projektem.\n" +
                "Odpowiedzi będą szukane tylko w bazie ogólnej.\n" +
                "Użyj `/vedra link <nazwa-projektu>` aby powiązać."
        }
    }
}

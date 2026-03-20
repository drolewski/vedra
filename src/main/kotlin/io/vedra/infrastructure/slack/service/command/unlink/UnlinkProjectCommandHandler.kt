package io.vedra.infrastructure.slack.service.command.unlink

import io.vedra.domain.service.TenantProjectService
import io.vedra.infrastructure.slack.service.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class UnlinkProjectCommandHandler(
    private val tenantProjectService: TenantProjectService,
) : CommandHandler<UnlinkProjectCommand> {
    override fun supports(command: Any): Boolean = command is UnlinkProjectCommand

    override fun handle(command: UnlinkProjectCommand): String {
        tenantProjectService.unlinkChannel(command.channelId)
        return "Kanał odłączony od projektu. Teraz używa bazy ogólnej."
    }
}

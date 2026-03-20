package io.vedra.infrastructure.slack.service.command.list

import io.vedra.domain.service.TenantProjectService
import io.vedra.infrastructure.slack.service.command.CommandHandler
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ListProjectsCommandHandler(
    private val tenantProjectService: TenantProjectService,
) : CommandHandler<ListProjectsCommand> {
    override fun supports(command: Any): Boolean = command is ListProjectsCommand

    override fun handle(command: ListProjectsCommand): String {
        val tenantId = UUID.nameUUIDFromBytes(command.teamId.toByteArray())
        val projects = tenantProjectService.listProjects(tenantId)

        if (projects.isEmpty()) {
            return "Brak skonfigurowanych projektów."
        }

        val lines =
            projects.map { project ->
                val label = if (project.isGeneral) "(ogólna)" else ""
                "• *${project.name}* $label"
            }

        return "Dostępne projekty:\n${lines.joinToString("\n")}"
    }
}
